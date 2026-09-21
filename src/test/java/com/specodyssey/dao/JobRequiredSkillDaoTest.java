package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobRequiredSkillDaoTest {

    private final JobRequiredSkillDao dao = new JobRequiredSkillDao();
    private final JobDao jobDao = new JobDao();

    @Test
    void insert_findByJobId() throws Exception {
        JobDto job = jobDao.findAll().get(0);
        long skillId;
        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, "TestSkill_jrs_" + System.nanoTime());
        }

        JobRequiredSkillDto item = new JobRequiredSkillDto();
        item.setJobId(job.getId());
        item.setSkillId(skillId);
        item.setImportance("REQUIRED");
        item.setRequiredLevel("INTERMEDIATE");
        item.setSource("MANUAL");
        item.setEstimated(false);
        item.setCollectedAt(LocalDateTime.now());

        Long id;
        try {
            try (Connection conn = DBUtil.getConnection()) {
                id = dao.insert(conn, item);
            }
            assertNotNull(id);

            List<JobRequiredSkillDto> items = dao.findByJobId(job.getId());
            assertTrue(items.stream().anyMatch(i -> i.getId().equals(id) && i.getSkillId().equals(skillId)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "JOB_REQUIRED_SKILL", "skill_id", skillId);
                TestFixtures.hardDelete(conn, "SKILL", skillId);
            }
        }
    }
}
