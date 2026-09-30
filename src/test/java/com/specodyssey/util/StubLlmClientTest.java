package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StubLlmClientTest {

    static class Reason {
        String reason;
        List<String> skills;
    }

    static class Benchmark {
        String jobName;
        int certCount;
    }

    @Test
    void 등록한_타입은_JSON을_파싱해_돌려준다() throws Exception {
        LlmClient llm = new StubLlmClient()
                .register(Reason.class, "{\"reason\":\"백엔드 기술이 겹칩니다\",\"skills\":[\"Java\",\"Spring\"]}");

        Reason r = llm.completeJson("아무 프롬프트", Reason.class);

        assertEquals("백엔드 기술이 겹칩니다", r.reason);
        assertEquals(List.of("Java", "Spring"), r.skills);
    }

    @Test
    void 타입마다_다른_응답을_돌려주고_프롬프트는_보지_않는다() throws Exception {
        LlmClient llm = new StubLlmClient()
                .register(Reason.class, "{\"reason\":\"이유\"}")
                .register(Benchmark.class, "{\"jobName\":\"백엔드\",\"certCount\":2}");

        assertEquals("이유", llm.completeJson("프롬프트 A", Reason.class).reason);
        assertEquals("이유", llm.completeJson("프롬프트 B", Reason.class).reason);
        Benchmark b = llm.completeJson(null, Benchmark.class);
        assertEquals("백엔드", b.jobName);
        assertEquals(2, b.certCount);
    }

    @Test
    void 등록하지_않은_타입은_재시도_불가_400으로_실패한다() {
        LlmClient llm = new StubLlmClient().register(Reason.class, "{\"reason\":\"이유\"}");

        ExternalApiClient.ExternalApiException e = assertThrows(ExternalApiClient.ExternalApiException.class,
                () -> llm.completeJson("프롬프트", Benchmark.class));

        assertEquals(400, e.getStatusCode());
    }

    @Test
    void 등록한_JSON이_깨져_있으면_호출할_때_ExternalApiException으로_알린다() {
        LlmClient llm = new StubLlmClient().register(Reason.class, "{이건 JSON이 아님");

        assertThrows(ExternalApiClient.ExternalApiException.class,
                () -> llm.completeJson("프롬프트", Reason.class));
    }

    @Test
    void 빈_JSON은_등록할_수_없다() {
        StubLlmClient stub = new StubLlmClient();

        assertThrows(IllegalArgumentException.class, () -> stub.register(Reason.class, null));
        assertThrows(IllegalArgumentException.class, () -> stub.register(Reason.class, "  "));
    }

    @Test
    void failing은_등록과_상관없이_지정한_상태코드로_실패한다() {
        for (int status : new int[] {429, 500, 503, -1, 400, 401}) {
            LlmClient llm = StubLlmClient.failing(status).register(Reason.class, "{\"reason\":\"이유\"}");

            ExternalApiClient.ExternalApiException e = assertThrows(ExternalApiClient.ExternalApiException.class,
                    () -> llm.completeJson("프롬프트", Reason.class));

            assertEquals(status, e.getStatusCode());
        }
    }
}
