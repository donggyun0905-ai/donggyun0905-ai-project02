package com.specodyssey.service;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobPostingDao;
import com.specodyssey.dao.JobSkillTrendDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobPostingDto;
import com.specodyssey.dto.JobSkillTrendDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JobSkillTrendService 통합테스트 — 실제 DB에 테스트용 JOB_POSTING을 심어두고
 * refreshAll()이 JOB_SKILL_TREND를 규칙 기반(비-LLM)으로 정확히 집계하는지 확인한다.
 * 실제 서비스 데이터(2026-06~09)와 겹치지 않도록 미래 월(2099-01)을 써서 격리한다.
 */
class JobSkillTrendServiceTest {

    private final JobDao jobDao = new JobDao();
    private final JobPostingDao jobPostingDao = new JobPostingDao();
    private final JobSkillTrendDao jobSkillTrendDao = new JobSkillTrendDao();
    private final JobSkillTrendService service =
            new JobSkillTrendService(jobPostingDao, jobSkillTrendDao, new ExactMatcher());

    @Test
    void refreshAll_집계된_mention_count와_ratio가_맞는다() throws Exception {
        JobDto job = jobDao.findAll().get(0);
        String skillName = "TrendSvcSkill_" + System.nanoTime();
        long skillId;
        long postingId1;
        long postingId2;
        long postingId3;

        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, skillName);

            // 3건 중 2건에 skillName 토큰이 들어감 (mention_count=2, ratio=66.67%)
            postingId1 = insertPosting(conn, job.getId(), skillName + ",OtherUnknownTech");
            postingId2 = insertPosting(conn, job.getId(), "OtherUnknownTech," + skillName);
            postingId3 = insertPosting(conn, job.getId(), "OtherUnknownTech");
        }

        try {
            service.refreshAll();

            List<JobSkillTrendDto> trends = jobSkillTrendDao.findByJobId(job.getId());
            JobSkillTrendDto trend = trends.stream()
                    .filter(t -> t.getSkillId().equals(skillId) && "209901".equals(t.getPeriodYm()))
                    .findFirst()
                    .orElse(null);

            assertTrue(trend != null, "집계된 JOB_SKILL_TREND 행을 찾지 못했다");
            assertEquals(2, trend.getMentionCount());
            assertEquals(0, new BigDecimal("66.67").compareTo(trend.getMentionRatio()));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "JOB_SKILL_TREND", "skill_id", skillId);
                TestFixtures.hardDelete(conn, "JOB_POSTING", postingId1);
                TestFixtures.hardDelete(conn, "JOB_POSTING", postingId2);
                TestFixtures.hardDelete(conn, "JOB_POSTING", postingId3);
                TestFixtures.hardDelete(conn, "SKILL", skillId);
            }
        }
    }

    private long insertPosting(Connection conn, Long jobId, String techStack) throws Exception {
        JobPostingDto posting = new JobPostingDto();
        posting.setJobId(jobId);
        posting.setSource("TEST");
        posting.setSourceUrl("test://job-skill-trend-service/" + System.nanoTime());
        posting.setTitle("테스트 공고");
        posting.setTechStack(techStack);
        posting.setPostedAt(LocalDate.of(2099, 1, 15));
        return jobPostingDao.insert(conn, posting);
    }
}
