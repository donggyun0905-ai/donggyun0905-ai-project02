package com.specodyssey.dao;

import com.specodyssey.dto.JobAliasDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class JobAliasDaoTest {

    private final JobAliasDao jobAliasDao = new JobAliasDao();
    private final JobDao jobDao = new JobDao();

    @Test
    void findAll_and_findByAliasName() throws Exception {
        JobDto job = jobDao.findAll().get(0);
        String aliasName = "TestAlias_" + System.nanoTime();

        long aliasId;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement(
                     "INSERT INTO JOB_ALIAS (job_id, alias_name, match_type) VALUES (?, ?, 'MANUAL')",
                     Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, job.getId());
            p.setString(2, aliasName);
            p.executeUpdate();
            try (ResultSet rs = p.getGeneratedKeys()) {
                rs.next();
                aliasId = rs.getLong(1);
            }
        }

        try {
            JobAliasDto found = jobAliasDao.findByAliasName(aliasName);
            assertNotNull(found);
            assertEquals(job.getId(), found.getJobId());
            assertTrue(jobAliasDao.findAll().stream().anyMatch(a -> a.getId().equals(aliasId)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "JOB_ALIAS", aliasId);
            }
        }
    }
}
