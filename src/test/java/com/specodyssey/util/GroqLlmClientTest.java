package com.specodyssey.util;

import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroqLlmClient 단위테스트 — 실제 호출 없이 전송부를 바꿔 끼워 재시도·파싱 규칙을 확인한다.
 * 키도 생성자로 넣으므로 .env에 GROQ_API_KEY가 없어도 돈다.
 */
class GroqLlmClientTest {

    record Answer(String reason) {
    }

    private static String envelope(String contentJson) {
        String escaped = contentJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"}}]}";
    }

    @Test
    void 응답에서_content_JSON을_꺼낸다() throws ExternalApiException {
        assertEquals("{\"reason\":\"좋음\"}", GroqLlmClient.extractContent(envelope("{\"reason\":\"좋음\"}")));
    }

    @Test
    void 형식이_다른_응답은_예외() {
        assertThrows(ExternalApiException.class, () -> GroqLlmClient.extractContent("{\"choices\":[]}"));
        assertThrows(ExternalApiException.class, () -> GroqLlmClient.extractContent("{\"error\":\"x\"}"));
        assertThrows(ExternalApiException.class, () -> GroqLlmClient.extractContent("not json"));
        assertThrows(ExternalApiException.class, () -> GroqLlmClient.extractContent(envelope("")));
    }

    @Test
    void 재시도는_429_5xx_네트워크오류만() {
        assertTrue(GroqLlmClient.isRetryable(429));
        assertTrue(GroqLlmClient.isRetryable(500));
        assertTrue(GroqLlmClient.isRetryable(503));
        assertTrue(GroqLlmClient.isRetryable(-1));
        assertFalse(GroqLlmClient.isRetryable(400));
        assertFalse(GroqLlmClient.isRetryable(401));
        assertFalse(GroqLlmClient.isRetryable(404));
    }

    @Test
    void 기능별로_지정한_모델로_요청한다() throws ExternalApiException {
        java.util.concurrent.atomic.AtomicReference<Object> sentModel = new java.util.concurrent.atomic.AtomicReference<>();
        LlmClient llm = new GroqLlmClient((body, key) -> {
            sentModel.set(body.get("model"));
            return envelope("{\"reason\":\"ok\"}");
        }, 0, () -> "test-key", () -> "openai/gpt-oss-20b");

        llm.completeJson("p", Answer.class);
        assertEquals("openai/gpt-oss-20b", sentModel.get());
    }

    @Test
    void 키가_없으면_호출하지_않고_실패한다() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient llm = new GroqLlmClient((body, key) -> {
            calls.incrementAndGet();
            return envelope("{\"reason\":\"x\"}");
        }, 0, () -> null);

        assertThrows(ExternalApiException.class, () -> llm.completeJson("p", Answer.class));
        assertEquals(0, calls.get());
    }

    @Test
    void 일시적_실패는_다시_시도해서_성공한다() throws ExternalApiException {
        AtomicInteger calls = new AtomicInteger();
        LlmClient llm = new GroqLlmClient((body, key) -> {
            if (calls.incrementAndGet() < 3) {
                throw new ExternalApiException("rate limited", null, 429);
            }
            return envelope("{\"reason\":\"세 번째에 성공\"}");
        }, 0, () -> "test-key");

        assertEquals("세 번째에 성공", llm.completeJson("prompt", Answer.class).reason());
        assertEquals(3, calls.get());
    }

    @Test
    void 잘못된_요청_400은_다시_시도하지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient llm = new GroqLlmClient((body, key) -> {
            calls.incrementAndGet();
            throw new ExternalApiException("bad request", null, 400);
        }, 0, () -> "test-key");

        ExternalApiException e = assertThrows(ExternalApiException.class, () -> llm.completeJson("p", Answer.class));
        assertEquals(400, e.getStatusCode());
        assertEquals(1, calls.get());
    }

    @Test
    void 계속_실패하면_세_번까지만_시도한다() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient llm = new GroqLlmClient((body, key) -> {
            calls.incrementAndGet();
            throw new ExternalApiException("server error", null, 503);
        }, 0, () -> "test-key");

        assertThrows(ExternalApiException.class, () -> llm.completeJson("p", Answer.class));
        assertEquals(3, calls.get());
    }

    @Test
    void 모델이_형식에_안_맞는_JSON을_주면_예외() {
        LlmClient llm = new GroqLlmClient((body, key) -> envelope("{\"reason\": [1, 2"), 0, () -> "test-key");

        assertThrows(ExternalApiException.class, () -> llm.completeJson("p", Answer.class));
    }
}
