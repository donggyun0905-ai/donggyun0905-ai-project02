package com.specodyssey.dao;

import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
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

class RoadmapStepDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private static final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao dao = new RoadmapStepDao();

    private static Long userId;
    private static Long gapAnalysisId;
    private static Long roadmapId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_step_user_" + System.nanoTime());
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

        RoadmapDto roadmap = new RoadmapDto();
        roadmap.setUserId(userId);
        roadmap.setVersion(1);
        roadmap.setActive(true);
        roadmap.setPrimary(true);
        try (Connection conn = DBUtil.getConnection()) {
            gapAnalysisId = gapAnalysisDao.insert(conn, analysis);
            roadmap.setGapAnalysisId(gapAnalysisId);
            roadmapId = roadmapDao.insert(conn, roadmap);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "ROADMAP", roadmapId);
            TestFixtures.hardDelete(conn, "GAP_ANALYSIS", gapAnalysisId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByRoadmapId_updateCompleted() throws Exception {
        RoadmapStepDto step = new RoadmapStepDto();
        step.setRoadmapId(roadmapId);
        step.setStepOrder(1);
        step.setStepType("CERT");
        step.setTier("ENTRY");
        step.setReason("정보처리기사가 기본 요건이라");
        step.setCompleted(false);

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, step);
        }
        try {
            assertNotNull(id);
            List<RoadmapStepDto> steps = dao.findByRoadmapId(roadmapId);
            assertEquals(1, steps.size());
            assertFalse(steps.get(0).isCompleted());

            LocalDateTime now = LocalDateTime.now();

            // 다른 사용자 id로는 갱신되지 않아야 한다 (JOIN을 통한 소유자 확인)
            try (Connection conn = DBUtil.getConnection()) {
                dao.updateCompleted(conn, id, userId + 999_999L, true, now);
            }
            assertFalse(dao.findByRoadmapId(roadmapId).get(0).isCompleted());

            try (Connection conn = DBUtil.getConnection()) {
                dao.updateCompleted(conn, id, userId, true, now);
            }
            RoadmapStepDto updated = dao.findByRoadmapId(roadmapId).get(0);
            assertTrue(updated.isCompleted());
            assertNotNull(updated.getCompletedAt());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "ROADMAP_STEP", id);
            }
        }
    }
}
