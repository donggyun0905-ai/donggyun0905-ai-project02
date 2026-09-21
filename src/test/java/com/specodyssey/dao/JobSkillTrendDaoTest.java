package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobSkillTrendDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobSkillTrendDaoTest {

    private final JobSkillTrendDao dao = new JobSkillTrendDao();
    private final JobDao jobDao = new JobDao();

    @Test
    void insert_findByJobId() throws Exception {
        JobDto job = jobDao.findAll().get(0);
        long skillId;
        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, "TestSkill_trend_" + System.nanoTime());
        }

        JobSkillTrendDto trend = new JobSkillTrendDto();
        trend.setJobId(job.getId());
        trend.setSkillId(skillId);
        trend.setPeriodYm("202601");
        trend.setMentionCount(42);
        trend.setMentionRatio(new BigDecimal("12.50"));

        Long id;
        try {
            try (Connection conn = DBUtil.getConnection()) {
                id = dao.insert(conn, trend);
            }
            assertNotNull(id);

            List<JobSkillTrendDto> trends = dao.findByJobId(job.getId());
            assertTrue(trends.stream().anyMatch(t -> t.getId().equals(id) && "202601".equals(t.getPeriodYm())));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "JOB_SKILL_TREND", "skill_id", skillId);
                TestFixtures.hardDelete(conn, "SKILL", skillId);
            }
        }
    }
}
