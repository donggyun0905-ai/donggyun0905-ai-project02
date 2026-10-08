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

    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_COMPLETION_TOKENS = 2000;

    private static final String SYSTEM_PROMPT = "반드시 JSON 객체 하나만 출력한다. 설명 문장이나 코드 블록 없이 JSON만 쓴다.";

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    /**
     * 분당 호출 한도 — Groq 무료 한도가 분당 요청 수로 걸린다. 넉넉히 30으로 두고 한꺼번에 10개까지
     * 몰아 쓸 수 있게 한다(화면 하나가 추천 이유를 여러 개 만들 때 쓴다). 운영에서는 JVM 전체에서
     * 하나를 공유한다 — 한도는 API 키 단위라 클라이언트를 몇 개 만들든 합쳐서 세야 한다.
     */
    private static final TokenBucket SHARED_RATE_LIMIT = new TokenBucket(10, 30);
    /**
     * 연속 5번 실패하면 1분 끊는다. 한도 초과·키 만료·장애가 모두 여기로 들어온다 —
     * 매 요청이 같은 실패를 반복하며 타임아웃만큼 기다리는 것을 막는다.
     */
    private static final CircuitBreaker SHARED_BREAKER = new CircuitBreaker("Groq", 5, 60_000);

    // 인스턴스 필드로 둔다 — 기본값은 위의 공유 객체지만, 테스트는 자기 것을 끼운다.
    // 공유 객체를 그대로 쓰면 테스트 수백 개가 같은 버킷을 비워 뒤쪽 테스트가 429로 깨진다(실제로 그랬다).
    private final TokenBucket rateLimit;
    private final CircuitBreaker breaker;

    private final LlmRetryPolicy retryPolicy;
    private final double temperature;
    private final int maxCompletionTokens;

    GroqLlmClient(String endpoint, String apiKey, String model, Duration timeout, LlmRetryPolicy retryPolicy) {
        this(endpoint, apiKey, model, timeout, retryPolicy, DEFAULT_TEMPERATURE, DEFAULT_MAX_COMPLETION_TOKENS);
    }

    /** 테스트용 — 호출 한도·서킷을 직접 끼운다. 운영 코드는 공유 객체를 쓰는 위 생성자를 쓴다. */
    GroqLlmClient(String endpoint, String apiKey, String model, Duration timeout, LlmRetryPolicy retryPolicy,
            TokenBucket rateLimit, CircuitBreaker breaker) {
        this(endpoint, apiKey, model, timeout, retryPolicy, DEFAULT_TEMPERATURE, DEFAULT_MAX_COMPLETION_TOKENS,
                rateLimit, breaker);
    }

    private GroqLlmClient(String endpoint, String apiKey, String model, Duration timeout, LlmRetryPolicy retryPolicy,
            double temperature, int maxCompletionTokens) {
        this(endpoint, apiKey, model, timeout, retryPolicy, temperature, maxCompletionTokens,
                SHARED_RATE_LIMIT, SHARED_BREAKER);
    }

    private GroqLlmClient(String endpoint, String apiKey, String model, Duration timeout, LlmRetryPolicy retryPolicy,
            double temperature, int maxCompletionTokens, TokenBucket rateLimit, CircuitBreaker breaker) {
        this.rateLimit = rateLimit;
        this.breaker = breaker;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model == null ? DEFAULT_MODEL : model;
        this.timeout = timeout;
        this.retryPolicy = retryPolicy;
        this.temperature = temperature;
        this.maxCompletionTokens = maxCompletionTokens;
    }

    /**
     * 생성 설정을 바꾼 사본 (2026-10-06, TrendLlmService·JobBenchmarkSpecService를 이 클라이언트로 옮기면서).
     * 예: 트렌드 정리는 후보 80건을 한 번에 보내 출력이 길어서 토큰 한도를 8000으로 넉넉히 둔다.
     */
    public GroqLlmClient withSettings(double temperature, int maxCompletionTokens) {
        // 사본도 같은 호출 한도·서킷을 쓴다 — 설정만 바꾼 같은 API 키이므로 한도를 따로 세면 안 된다
        return new GroqLlmClient(endpoint, apiKey, model, timeout, retryPolicy, temperature, maxCompletionTokens,
                rateLimit, breaker);
    }

    /** 재시도 규칙을 바꾼 사본 — 화면 요청은 DEFAULT(짧게), 스케줄러 배치는 BATCH(429에 오래 기다림) */
    public GroqLlmClient withRetryPolicy(LlmRetryPolicy policy) {
        return new GroqLlmClient(endpoint, apiKey, model, timeout, policy, temperature, maxCompletionTokens,
                rateLimit, breaker);
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
        body.put("temperature", temperature);
        body.put("reasoning_effort", "low");
        body.put("max_completion_tokens", maxCompletionTokens);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)));
        Map<String, String> headers = Map.of("Authorization", "Bearer " + apiKey);

        // 보내기 전에 막는다 (2026-10-08) — 한도를 넘거나 상대가 죽어 있으면 어차피 거절될 호출이다.
        // 429를 받고 20·40·60초 기다리는 것보다, 바로 실패로 돌려 호출부가 캐시·안내 문구로 넘어가는 게 낫다(FR-111).
        if (!rateLimit.tryAcquire()) {
            throw new ExternalApiException("LLM 분당 호출 한도에 걸렸습니다 ("
                    + rateLimit.millisUntilNext() / 1000 + "초 뒤 다시 가능)", null, 429);
        }
        if (!breaker.allowRequest()) {
            throw new ExternalApiException("LLM 호출이 연속 실패해 잠시 끊었습니다 ("
                    + breaker.millisUntilRetry() / 1000 + "초 뒤 다시 시도)", null, 503);
        }

        long start = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                String response = ExternalApiClient.postJson(endpoint, body, headers, timeout);
                T parsed = parse(response, type);
                breaker.recordSuccess();
                return parsed;
            } catch (ExternalApiException e) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
                if (!retryPolicy.shouldRetry(e.getStatusCode(), attempt, elapsed)) {
                    // 형식 오류(422)는 상대가 살아 있다는 뜻이라 서킷 실패로 세지 않는다 — 우리 프롬프트 문제다
                    if (e.getStatusCode() != LlmRetryPolicy.FORMAT_ERROR) {
                        breaker.recordFailure();
                    }
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
