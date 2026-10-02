package com.specodyssey.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq(OpenAI 호환 API)로 부르는 LlmClient 실제 구현. 관련 요구사항: FR-111, NFR-6
 * 설정: .env의 GROQ_API_KEY(필수), GROQ_MODEL(선택, 기본 openai/gpt-oss-120b) — TrendLlmService·ProjectIdeaService와 같은 키.
 *
 * JSON 모드(response_format=json_object)로 요청하고 choices[0].message.content를 type으로 파싱한다.
 * 응답이 JSON이 아니거나 type과 맞지 않으면 LlmRetryPolicy.FORMAT_ERROR(422)로 알린다.
 * 실패 시 직전 결과로 대체하는 건 CachingLlmClient가 맡는다 — 이 클래스는 호출과 재시도만 한다.
 */
public class GroqLlmClient implements LlmClient {

    public static final String DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    public static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
    // gpt-oss는 답하기 전에 추론에 시간을 쓴다 — 워크넷 기본 10초로는 부족하다 (docs/spec-odyssey-dao-guide.html 특이사항 ④)
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final String SYSTEM_PROMPT = "반드시 JSON 객체 하나만 출력한다. 설명 문장이나 코드 블록 없이 JSON만 쓴다.";

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final LlmRetryPolicy retryPolicy;

    GroqLlmClient(String endpoint, String apiKey, String model, Duration timeout, LlmRetryPolicy retryPolicy) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model == null ? DEFAULT_MODEL : model;
        this.timeout = timeout;
        this.retryPolicy = retryPolicy;
    }

    /** .env(또는 환경변수)의 GROQ_API_KEY·GROQ_MODEL로 만든다. 키가 없어도 만들어지고, 호출할 때 401로 실패한다. */
    public static GroqLlmClient fromConfig() {
        return new GroqLlmClient(DEFAULT_ENDPOINT, AppConfig.get("GROQ_API_KEY"), AppConfig.get("GROQ_MODEL"),
                TIMEOUT, LlmRetryPolicy.DEFAULT);
    }

    /**
     * 기능별로 모델을 따로 쓴다 (2026-10-01, ResumeFeedbackService 연결 — donghyeon 원 구현에서
     * 가져옴). Groq 무료 한도는 모델마다 하루 토큰을 따로 세므로, 자소서 첨삭처럼 토큰을 많이 쓰는
     * 기능과 트렌드 수집·로드맵 프로젝트 아이디어(GROQ_MODEL) 모델을 나누면 서로의 한도를 잡아먹지
     * 않는다.
     * @param modelConfigKey .env에서 모델명을 읽을 키 (예: RESUME_FEEDBACK_MODEL)
     * @param defaultModel   키가 없을 때 쓸 모델
     */
    public static GroqLlmClient fromConfig(String modelConfigKey, String defaultModel) {
        String model = AppConfig.get(modelConfigKey);
        return new GroqLlmClient(DEFAULT_ENDPOINT, AppConfig.get("GROQ_API_KEY"),
                model == null ? defaultModel : model, TIMEOUT, LlmRetryPolicy.DEFAULT);
    }

    @Override
    public <T> T completeJson(String prompt, Class<T> type) throws ExternalApiException {
        if (apiKey == null) {
            // 설정 문제라 다시 시도해도 같다 — 재시도 대상이 아닌 401로 알린다
            throw new ExternalApiException("GROQ_API_KEY가 설정되지 않았습니다 (.env 또는 환경변수)", null, 401);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("reasoning_effort", "low");
        body.put("max_completion_tokens", 2000);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)));
        Map<String, String> headers = Map.of("Authorization", "Bearer " + apiKey);

        long start = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                String response = ExternalApiClient.postJson(endpoint, body, headers, timeout);
                return parse(response, type);
            } catch (ExternalApiException e) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
                if (!retryPolicy.shouldRetry(e.getStatusCode(), attempt, elapsed)) {
                    throw e;
                }
                sleep(retryPolicy.backoffMillis(attempt), e);
            }
        }
    }

    // LLM 출력은 신뢰하지 않는다 — 형식이 틀리면 전부 FORMAT_ERROR로 모은다 (NFR-6)
    private static <T> T parse(String response, Class<T> type) throws ExternalApiException {
        try {
            JsonObject choice = JsonParser.parseString(response).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject();
            JsonElement finishReason = choice.get("finish_reason");
            if (finishReason != null && "length".equals(finishReason.getAsString())) {
                throw new IllegalStateException("응답이 토큰 한도에서 잘림");
            }
            String content = choice.getAsJsonObject("message").get("content").getAsString();
            T result = ExternalApiClient.parseJson(content, type);
            if (result == null) {
                throw new IllegalStateException("응답 내용이 비어 있음");
            }
            return result;
        } catch (RuntimeException | ExternalApiException e) {
            throw new ExternalApiException("LLM 응답 형식 오류: " + e.getMessage(), e, LlmRetryPolicy.FORMAT_ERROR);
        }
    }

    private static void sleep(long millis, ExternalApiException cause) throws ExternalApiException {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }
}
