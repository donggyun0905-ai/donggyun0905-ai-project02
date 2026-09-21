package com.specodyssey.dao;

import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
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

class GapAnalysisItemDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final JobDao jobDao = new JobDao();
    private static final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisItemDao dao = new GapAnalysisItemDao();

    private static Long userId;
    private static Long gapAnalysisId;
    private static long skillId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_gapitem_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        Long jobId = jobDao.findAll().get(0).getId();

        GapAnalysisDto analysis = new GapAnalysisDto();
        analysis.setUserId(userId);
        analysis.setJobId(jobId);
        analysis.setMatchRate(new BigDecimal("50.00"));
        analysis.setAnalyzedAt(LocalDateTime.now());
        try (Connection conn = DBUtil.getConnection()) {
            gapAnalysisId = gapAnalysisDao.insert(conn, analysis);
            skillId = TestFixtures.insertSkill(conn, "TestSkill_gapitem_" + System.nanoTime());
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "GAP_ANALYSIS", gapAnalysisId);
            TestFixtures.hardDelete(conn, "SKILL", skillId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByGapAnalysisId() throws Exception {
        GapAnalysisItemDto item = new GapAnalysisItemDto();
        item.setGapAnalysisId(gapAnalysisId);
        item.setSkillId(skillId);
        item.setStatus("MISSING");
        item.setSimilarityScore(new BigDecimal("0.1000"));

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, item);
        }
        try {
            assertNotNull(id);
            List<GapAnalysisItemDto> items = dao.findByGapAnalysisId(gapAnalysisId);
            assertTrue(items.stream().anyMatch(i -> i.getId().equals(id) && "MISSING".equals(i.getStatus())));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "GAP_ANALYSIS_ITEM", id);
            }
        }
    }
}
