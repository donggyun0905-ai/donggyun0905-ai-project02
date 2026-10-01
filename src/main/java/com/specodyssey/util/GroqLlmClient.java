package com.specodyssey.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Groq(OpenAI 호환 Chat Completions)로 동작하는 LlmClient 구현체.
 * 관련 규칙: claude.md "AI 호출은 반드시 서버에서", "외부 API 호출은 타임아웃을 직접 지정",
 *           "LLM 응답은 JSON으로 받고 파싱 실패를 예외 처리", "재시도는 429·5xx·-1만, 400·401은 재시도하지 않는다"
 *
 * 키는 TrendLlmService와 같은 GROQ_API_KEY를 쓴다. 모델은 기본 GROQ_MODEL이고, 기능별 키를 줄 수 있다.
 * 프롬프트·응답 본문은 로그나 예외 메시지에 넣지 않는다 — 자소서처럼 개인정보가 담길 수 있다.
 */
public class GroqLlmClient implements LlmClient {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 3;
    // 사용자가 화면에서 기다리는 호출이라 TrendLlmService(20초)보다 짧게 기다린다
    private static final long RETRY_WAIT_MS = 3_000;

    /** 요청 본문 → 응답 본문. 테스트에서 네트워크 없이 바꿔 끼우기 위한 틈. */
    @FunctionalInterface
    interface Transport {
        String post(Map<String, Object> body, String apiKey) throws ExternalApiException;
    }

    private final Transport transport;
    private final long retryWaitMs;
    private final Supplier<String> apiKeySupplier;
    private final Supplier<String> modelSupplier;

    /** GROQ_MODEL(없으면 기본 모델)을 쓴다. */
    public GroqLlmClient() {
        this("GROQ_MODEL", DEFAULT_MODEL);
    }

    /**
     * 기능별로 모델을 따로 쓴다. Groq 무료 한도는 모델마다 하루 토큰을 따로 세므로,
     * 트렌드 수집처럼 토큰을 많이 쓰는 기능과 모델을 나누면 서로의 한도를 잡아먹지 않는다.
     * @param modelConfigKey .env에서 모델명을 읽을 키 (예: RESUME_FEEDBACK_MODEL)
     * @param defaultModel   키가 없을 때 쓸 모델
     */
    public GroqLlmClient(String modelConfigKey, String defaultModel) {
        this((body, apiKey) -> ExternalApiClient.postJson(ENDPOINT, body,
                        Map.of("Authorization", "Bearer " + apiKey), TIMEOUT), RETRY_WAIT_MS,
                () -> AppConfig.get("GROQ_API_KEY"),
                () -> {
                    String model = AppConfig.get(modelConfigKey);
                    return model == null ? defaultModel : model;
                });
    }

    GroqLlmClient(Transport transport, long retryWaitMs, Supplier<String> apiKeySupplier) {
        this(transport, retryWaitMs, apiKeySupplier, () -> DEFAULT_MODEL);
    }

    GroqLlmClient(Transport transport, long retryWaitMs, Supplier<String> apiKeySupplier, Supplier<String> modelSupplier) {
        this.transport = transport;
        this.retryWaitMs = retryWaitMs;
        this.apiKeySupplier = apiKeySupplier;
        this.modelSupplier = modelSupplier;
    }

    @Override
    public <T> T completeJson(String prompt, Class<T> type) throws ExternalApiException {
        String apiKey = apiKeySupplier.get();
        if (apiKey == null) {
            throw new ExternalApiException("GROQ_API_KEY가 설정되지 않았습니다 (.env 또는 환경변수)", null);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelSupplier.get());
        body.put("temperature", 0.3);
        // gpt-oss는 추론에 토큰을 쓴다 — 출력이 잘려 JSON이 깨지지 않게 추론을 줄이고 한도를 넉넉히 둔다
        body.put("reasoning_effort", "low");
        body.put("max_completion_tokens", 4000);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", "너는 요청받은 형식의 JSON 객체 하나만 출력한다. 설명 문장이나 코드 블록 표시는 쓰지 않는다."),
                Map.of("role", "user", "content", prompt)));

        return ExternalApiClient.parseJson(extractContent(postWithRetry(body, apiKey)), type);
    }

    private String postWithRetry(Map<String, Object> body, String apiKey) throws ExternalApiException {
        for (int attempt = 1; ; attempt++) {
            try {
                return transport.post(body, apiKey);
            } catch (ExternalApiException e) {
                if (!isRetryable(e.getStatusCode()) || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                sleep(retryWaitMs * attempt, e);
            }
        }
    }

    // 429(한도 초과)·5xx(서버 오류)·-1(타임아웃·네트워크)만 다시 시도한다. 400·401은 다시 보내도 결과가 같다.
    static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500 || statusCode == -1;
    }

    /** {"choices":[{"message":{"content":"{…}"}}]} 에서 content(모델이 만든 JSON 문자열)를 꺼낸다. */
    static String extractContent(String response) throws ExternalApiException {
        try {
            JsonElement content = JsonParser.parseString(response).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content");
            if (content == null || content.isJsonNull() || content.getAsString().isBlank()) {
                throw new ExternalApiException("LLM 응답에 내용이 없습니다", null);
            }
            return content.getAsString();
        } catch (JsonParseException | IllegalStateException | NullPointerException | IndexOutOfBoundsException
                 | ClassCastException | UnsupportedOperationException e) {
            throw new ExternalApiException("LLM 응답 형식이 예상과 다릅니다", e);
        }
    }

    private static void sleep(long ms, ExternalApiException cause) throws ExternalApiException {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }
}
