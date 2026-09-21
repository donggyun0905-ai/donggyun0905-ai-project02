package com.specodyssey.dao;

import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GapAnalysisDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final JobDao jobDao = new JobDao();
    private final GapAnalysisDao dao = new GapAnalysisDao();

    private static Long userId;
    private static Long jobId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_gap_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
        jobId = jobDao.findAll().get(0).getId();
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_findById() throws Exception {
        GapAnalysisDto analysis = new GapAnalysisDto();
        analysis.setUserId(userId);
        analysis.setJobId(jobId);
        analysis.setMatchRate(new BigDecimal("62.50"));
        analysis.setAnalyzedAt(LocalDateTime.now());

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, analysis);
        }
        try {
            assertNotNull(id);
            List<GapAnalysisDto> analyses = dao.findByUserId(userId);
            assertEquals(1, analyses.size());
            assertEquals(0, new BigDecimal("62.50").compareTo(analyses.get(0).getMatchRate()));

            assertNotNull(dao.findById(id));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "GAP_ANALYSIS", id);
            }
        }
    }
}
