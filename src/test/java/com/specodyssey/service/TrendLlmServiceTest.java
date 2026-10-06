package com.specodyssey.service;

import com.google.gson.Gson;
import com.specodyssey.dto.JobDto;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.LlmRetryPolicy;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 트렌드 기술 정리 (FR-54·55). 공용 LlmClient로 옮긴 뒤에도 검증 규칙이 같은지 StubLlmClient로 본다 (2026-10-06). DB 불필요. */
class TrendLlmServiceTest {

    private static TrendLlmService.Response response(String json) {
        return new Gson().fromJson(json, TrendLlmService.Response.class);
    }

    private static JobDto job(long id) {
        JobDto job = new JobDto();
        job.setId(id);
        job.setJobName("직무" + id);
        return job;
    }

    @Test
    void 정상_항목은_기술명_설명_확신도_직무_연관도로_정리된다() throws Exception {
        var items = TrendLlmService.toItems(response(
                "{\"items\":[{\"index\":1,\"tech_name\":\" Kubernetes \",\"summary\":\"컨테이너 관리 도구\",\"confidence\":0.95,"
                        + "\"jobs\":[{\"job_id\":7,\"relevance\":0.9},{\"job_id\":8,\"relevance\":0.3},{\"job_id\":99,\"relevance\":0.9}]}]}"),
                3, Set.of(7L, 8L));

        assertEquals(1, items.size());
        assertEquals(1, items.get(0).candidateIndex());
        assertEquals("Kubernetes", items.get(0).techName());
        assertEquals(new BigDecimal("0.95"), items.get(0).confidence());
        assertEquals(Map.of(7L, new BigDecimal("0.9000")), items.get(0).jobRelevance(),
                "연관도 0.5 미만(8)과 모르는 직무(99)는 버린다");
    }

    @Test
    void 범위_밖_번호_빈_이름_중복_기술은_버리고_확신도는_0에서_1로_자른다() throws Exception {
        var items = TrendLlmService.toItems(response("{\"items\":["
                + "{\"index\":5,\"tech_name\":\"A\",\"summary\":\"s\"},"
                + "{\"index\":0,\"tech_name\":\"\",\"summary\":\"s\"},"
                + "{\"index\":0,\"tech_name\":\"Rust\",\"summary\":\"s\",\"confidence\":3},"
                + "{\"index\":1,\"tech_name\":\"rust\",\"summary\":\"s\"},"
                + "{\"index\":2,\"tech_name\":\"Go\",\"summary\":\"s\"},"
                + "{\"tech_name\":\"번호없음\",\"summary\":\"s\"}]}"), 3, Set.of());

        assertEquals(List.of("Rust", "Go"), items.stream().map(TrendLlmService.TrendItem::techName).toList());
        assertEquals(BigDecimal.ONE, items.get(0).confidence());
        assertEquals(BigDecimal.ZERO, items.get(1).confidence(), "확신도가 없으면 0 — 통과시키지 않는다");
    }

    @Test
    void items가_없으면_형식_오류로_실패한다() {
        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> TrendLlmService.toItems(response("{}"), 3, Set.of()));
        assertEquals(LlmRetryPolicy.FORMAT_ERROR, e.getStatusCode());
    }

    @Test
    void 후보가_80건을_넘으면_나눠_부르고_번호를_전체_기준으로_바꾼다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LlmClient llm = new LlmClient() {
            @Override
            public <T> T completeJson(String prompt, Class<T> type) {
                int call = calls.incrementAndGet();
                return new Gson().fromJson("{\"items\":[{\"index\":0,\"tech_name\":\"기술" + call + "\",\"summary\":\"s\"}]}", type);
            }
        };
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            candidates.add("글 " + i);
        }

        var items = new TrendLlmService(llm).organize(candidates, List.of(job(1)));

        assertEquals(2, calls.get());
        assertEquals(List.of(0, 80), items.stream().map(TrendLlmService.TrendItem::candidateIndex).toList());
    }

    @Test
    void LLM이_실패하면_예외를_그대로_올려_호출부가_직전_데이터를_유지하게_한다() {
        TrendLlmService service = new TrendLlmService(StubLlmClient.failing(429));

        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> service.organize(List.of("글"), List.of(job(1))));
        assertEquals(429, e.getStatusCode());
    }
}
