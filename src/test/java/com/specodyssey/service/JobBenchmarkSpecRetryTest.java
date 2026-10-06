package com.specodyssey.service;

import com.google.gson.Gson;
import com.specodyssey.dao.JobBenchmarkSpecDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dto.JobBenchmarkSpecDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.LlmRetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 합격자 참고 루트(FR-46)를 공용 LlmClient로 옮긴 뒤의 실패 처리 (FR-111, 2026-10-06).
 * 실제 Groq 대신 항상 실패하는 가짜 클라이언트를 쓰므로 DB에 새 행이 생기지 않는다.
 */
class JobBenchmarkSpecRetryTest {

    @Test
    void 실패하면_빈_목록을_돌려주고_10분_동안은_LLM을_다시_부르지_않는다() throws Exception {
        Long jobId = jobWithoutBenchmark();
        AtomicInteger calls = new AtomicInteger();
        LlmClient down = new LlmClient() {
            @Override
            public <T> T completeJson(String prompt, Class<T> type) throws ExternalApiException {
                calls.incrementAndGet();
                throw new ExternalApiException("한도 초과", null, 429);
            }
        };
        AtomicLong now = new AtomicLong(1_000_000_000L + jobId); // 다른 테스트 실행과 겹치지 않게 직무마다 다른 시각
        JobBenchmarkSpecService service = new JobBenchmarkSpecService(down, now::get);

        assertTrue(service.getOrGenerate(jobId).isEmpty());
        assertTrue(service.isTemporarilyUnavailable(jobId), "화면이 실패 안내를 띄울 수 있어야 한다");
        assertEquals(1, calls.get());

        now.addAndGet(JobBenchmarkSpecService.FAILURE_COOLDOWN.toMillis() - 1);
        assertTrue(service.getOrGenerate(jobId).isEmpty());
        assertEquals(1, calls.get(), "쉬는 동안은 LLM을 부르지 않아 화면이 바로 열린다");

        now.addAndGet(1);
        assertFalse(service.isTemporarilyUnavailable(jobId));
        service.getOrGenerate(jobId);
        assertEquals(2, calls.get(), "10분이 지나면 다시 시도한다");
    }

    @Test
    void 응답의_형식이_틀린_항목은_건너뛰고_길면_자른다() throws Exception {
        JobBenchmarkSpecService.Response response = new Gson().fromJson("{\"items\":["
                + "{\"tier\":\"entry\",\"specType\":\"skill\",\"content\":\" Java 기초 \"},"
                + "{\"tier\":\"UNKNOWN\",\"specType\":\"SKILL\",\"content\":\"버림\"},"
                + "{\"tier\":\"CORE\",\"specType\":\"PROJECT\",\"content\":\"" + "가".repeat(200) + "\"},"
                + "{\"tier\":\"CORE\"}]}", JobBenchmarkSpecService.Response.class);

        List<JobBenchmarkSpecDto> specs = JobBenchmarkSpecService.toSpecs(response, 1L);

        assertEquals(2, specs.size());
        assertEquals("ENTRY", specs.get(0).getTier());
        assertEquals("SKILL", specs.get(0).getSpecType());
        assertEquals("Java 기초", specs.get(0).getContent());
        assertEquals(120, specs.get(1).getContent().length());
    }

    @Test
    void 쓸_항목이_없으면_형식_오류로_실패한다() {
        ExternalApiException e = assertThrows(ExternalApiException.class, () -> JobBenchmarkSpecService.toSpecs(
                new Gson().fromJson("{\"items\":[{\"tier\":\"X\",\"specType\":\"SKILL\",\"content\":\"c\"}]}",
                        JobBenchmarkSpecService.Response.class), 1L));
        assertEquals(LlmRetryPolicy.FORMAT_ERROR, e.getStatusCode());
    }

    // 참고 루트가 아직 없는 직무 — 있으면 저장된 걸 그대로 돌려줘 LLM 경로를 볼 수 없다
    private static Long jobWithoutBenchmark() throws Exception {
        JobBenchmarkSpecDao dao = new JobBenchmarkSpecDao();
        for (JobDto job : new JobDao().findAll()) {
            if (dao.findByJobId(job.getId()).isEmpty()) {
                return job.getId();
            }
        }
        assumeTrue(false, "참고 루트가 없는 직무가 없어 건너뜀");
        return null;
    }
}
