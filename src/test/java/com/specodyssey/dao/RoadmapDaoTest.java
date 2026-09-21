package com.specodyssey.dao;

import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.RoadmapDto;
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

class RoadmapDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final RoadmapDao dao = new RoadmapDao();

    private static Long userId;
    private static Long gapAnalysisId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_roadmap_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        Long jobId = new JobDao().findAll().get(0).getId();
        GapAnalysisDto analysis = new GapAnalysisDto();
        analysis.setUserId(userId);
        analysis.setJobId(jobId);
        analysis.setMatchRate(new BigDecimal("30.00"));
        analysis.setAnalyzedAt(LocalDateTime.now());
        try (Connection conn = DBUtil.getConnection()) {
            gapAnalysisId = gapAnalysisDao.insert(conn, analysis);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "GAP_ANALYSIS", gapAnalysisId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_findPrimary_updateActiveAndPrimary() throws Exception {
        RoadmapDto roadmap = new RoadmapDto();
        roadmap.setUserId(userId);
        roadmap.setGapAnalysisId(gapAnalysisId);
        roadmap.setVersion(1);
        roadmap.setActive(true);
        roadmap.setPrimary(true);
        roadmap.setTargetLevel("EXPERT");

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, roadmap);
        }
        try {
            assertNotNull(id);
            List<RoadmapDto> roadmaps = dao.findByUserId(userId);
            assertEquals(1, roadmaps.size());

            RoadmapDto primary = dao.findPrimaryByUserId(userId);
            assertNotNull(primary);
            assertEquals(id, primary.getId());

            // 다른 사용자 id로는 갱신되지 않아야 한다 (소유자 확인)
            try (Connection conn = DBUtil.getConnection()) {
                dao.updateActiveAndPrimary(conn, id, userId + 999_999L, false, false);
            }
            assertNotNull(dao.findPrimaryByUserId(userId));

            try (Connection conn = DBUtil.getConnection()) {
                dao.updateActiveAndPrimary(conn, id, userId, false, false);
            }
            assertNull(dao.findPrimaryByUserId(userId));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "ROADMAP", id);
            }
        }
    }
}
