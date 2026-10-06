package com.specodyssey.service;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobPostingDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.service.GapAnalysisService.RequirementSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 격차 분석 화면의 "예시적 추정" 표시 재료 (FR-113 · TD-2 투명성, 2026-10-06).
 * 공유 DB에는 읽기만 한다 — 실제 직무 데이터를 그대로 대조한다.
 */
class GapAnalysisRequirementSourceTest {

    @Test
    void 공고가_기준보다_적으면_적은_직무로_본다() {
        assertTrue(new RequirementSource(Set.of(1L), 0).isFewPostings());
        assertTrue(new RequirementSource(Set.of(1L), GapAnalysisService.FEW_POSTINGS_THRESHOLD - 1).isFewPostings());
        assertFalse(new RequirementSource(Set.of(1L), GapAnalysisService.FEW_POSTINGS_THRESHOLD).isFewPostings());
    }

    @Test
    void 추정치인_기술만_예시적_추정으로_표시한다() {
        RequirementSource source = new RequirementSource(Set.of(1L, 2L), 100);

        assertTrue(source.isEstimated(1L));
        assertFalse(source.isEstimated(3L));
        assertFalse(source.isEstimated(null));
        assertTrue(source.isAnyEstimated());
        assertFalse(new RequirementSource(Set.of(), 0).isAnyEstimated());
    }

    @Test
    void 실제_직무의_추정_여부와_공고_수를_DB_그대로_돌려준다() throws Exception {
        List<JobDto> jobs = new JobDao().findAll();
        assumeTrue(!jobs.isEmpty(), "직무 데이터가 없어 건너뜀");
        GapAnalysisService service = new GapAnalysisService(new ExactMatcher());

        for (JobDto job : jobs) {
            RequirementSource source = service.getRequirementSource(job.getId());
            List<JobRequiredSkillDto> required = new JobRequiredSkillDao().findByJobId(job.getId());

            assertEquals(new JobPostingDao().findByJobId(job.getId()).size(), source.getPostingCount(), job.getJobName());
            for (JobRequiredSkillDto r : required) {
                assertEquals(r.isEstimated(), source.isEstimated(r.getSkillId()), job.getJobName() + " skill " + r.getSkillId());
            }
        }
    }
}
