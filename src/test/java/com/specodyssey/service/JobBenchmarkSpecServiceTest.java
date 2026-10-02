package com.specodyssey.service;

import com.specodyssey.dao.JobBenchmarkSpecDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dto.JobBenchmarkSpecDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JobBenchmarkSpecService 통합테스트. 관련 요구사항: FR-46
 */
class JobBenchmarkSpecServiceTest {

    private final JobDao jobDao = new JobDao();
    private final JobBenchmarkSpecDao jobBenchmarkSpecDao = new JobBenchmarkSpecDao();
    private final JobBenchmarkSpecService service = new JobBenchmarkSpecService();

    private Long insertedSpecId;

    @AfterEach
    void tearDown() throws Exception {
        if (insertedSpecId != null) {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "JOB_BENCHMARK_SPEC", insertedSpecId);
            }
            insertedSpecId = null;
        }
    }

    @Test
    void 이미_저장된_데이터가_있으면_LLM을_다시_부르지_않고_그대로_묶어서_돌려준다() throws Exception {
        Long jobId = jobDao.findAll().get(0).getId();

        JobBenchmarkSpecDto spec = new JobBenchmarkSpecDto();
        spec.setJobId(jobId);
        spec.setTier("ENTRY");
        spec.setSpecType("SKILL");
        spec.setContent("테스트_캐시확인_항목_" + System.nanoTime());
        spec.setEstimated(true);
        insertedSpecId = jobBenchmarkSpecDao.insert(spec);

        List<JobBenchmarkSpecService.TierBenchmark> result = service.getOrGenerate(jobId);

        assertEquals(1, result.size());
        assertEquals("ENTRY", result.get(0).tier());
        assertEquals(1, result.get(0).items().size());
        assertEquals(spec.getContent(), result.get(0).items().get(0).content());
        // 캐시 적중이면 새 행이 추가로 생기지 않아야 한다(= LLM을 다시 안 불렀다는 뜻).
        assertEquals(1, jobBenchmarkSpecDao.findByJobId(jobId).size());
    }

    // 실제 LLM(Groq) 호출 경로는 이 프로젝트의 다른 서비스들(ProjectIdeaService·TrendLlmService)도
    // 전용 테스트를 안 둔 것과 같은 이유로 여기서도 전용 테스트를 두지 않는다 — 외부 API 속도 제한
    // (429)에 그대로 노출돼서 테스트가 느려지고 깨지기 쉽다(직접 겪음, 2026-10-01: 재시도 4회를
    // 다 쓰고도 429가 계속 나서 실패). post()/parse() 로직은 ProjectIdeaService와 구조가 완전히
    // 같아서(같은 재시도·JSON 파싱 패턴을 그대로 재사용) 그쪽에서 이미 검증된 것으로 간주한다.
}
