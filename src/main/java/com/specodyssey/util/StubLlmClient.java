package com.specodyssey.util;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 벤더가 정해지기 전까지 쓰는 가짜 LlmClient. 실제 호출은 하지 않는다.
 * 관련 요구사항: FR-111 (실패 시 대체 경로 확인용 failing)
 *
 * 응답 모양이 기능마다 달라서 JSON 하나로는 모든 타입을 채울 수 없다 — 쓰는 쪽이 자기 DTO와 예시 JSON을 등록한다.
 * <pre>
 * LlmClient llm = new StubLlmClient()
 *         .register(DiscoveryReason.class, "{\"reason\":\"백엔드 기술 3개가 겹칩니다\"}");
 * LlmClient down = StubLlmClient.failing(429);   // 항상 HTTP 429로 실패
 * </pre>
 * 프롬프트는 보지 않는다. 같은 타입이면 항상 같은 응답을 돌려준다.
 */
public class StubLlmClient implements LlmClient {

    // 등록 누락은 다시 시도해도 결과가 같으므로 재시도 대상이 아닌 400으로 알린다 (-1이면 재시도 대상이 된다)
    private static final int NOT_REGISTERED_STATUS = 400;

    private final Map<Class<?>, String> responses = new ConcurrentHashMap<>();
    private final Integer failStatusCode;

    public StubLlmClient() {
        this(null);
    }

    private StubLlmClient(Integer failStatusCode) {
        this.failStatusCode = failStatusCode;
    }

    /** 등록과 상관없이 항상 statusCode로 실패하는 Stub. -1은 타임아웃·네트워크 오류 흉내. */
    public static StubLlmClient failing(int statusCode) {
        return new StubLlmClient(statusCode);
    }

    /** type을 요청받으면 json을 파싱해 돌려준다. 같은 type을 다시 등록하면 덮어쓴다. */
    public StubLlmClient register(Class<?> type, String json) {
        Objects.requireNonNull(type, "type");
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Stub 응답 JSON이 비어 있음: " + type.getName());
        }
        responses.put(type, json);
        return this;
    }

    @Override
    public <T> T completeJson(String prompt, Class<T> type) throws ExternalApiClient.ExternalApiException {
        if (failStatusCode != null) {
            throw new ExternalApiClient.ExternalApiException(
                    "Stub LLM 실패 흉내: HTTP " + failStatusCode, null, failStatusCode);
        }
        String json = responses.get(type);
        if (json == null) {
            throw new ExternalApiClient.ExternalApiException(
                    "Stub LLM에 등록되지 않은 응답 타입: " + type.getName(), null, NOT_REGISTERED_STATUS);
        }
        return ExternalApiClient.parseJson(json, type);
    }
}
