package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** AdminRoadmapService 통합테스트. 완료 체크가 꼬인 단계를 관리자가 직접 고치는 지원 도구. */
class AdminRoadmapServiceTest {

    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();
    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final AdminRoadmapService service = new AdminRoadmapService();

    private Long userId;
    private Long gapAnalysisId;
    private Long roadmapId;
    private Long stepId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("admin_roadmap_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        Long jobId = jobDao.findAll().get(0).getId();
        try (Connection conn = DBUtil.getConnection()) {
            GapAnalysisDto analysis = new GapAnalysisDto();
            analysis.setUserId(userId);
            analysis.setJobId(jobId);
            analysis.setMatchRate(new BigDecimal("50.00"));
            analysis.setAnalyzedAt(LocalDateTime.now());
            gapAnalysisId = gapAnalysisDao.insert(conn, analysis);

            RoadmapDto roadmap = new RoadmapDto();
            roadmap.setUserId(userId);
            roadmap.setGapAnalysisId(gapAnalysisId);
            roadmap.setVersion(1);
            roadmap.setActive(true);
            roadmap.setPrimary(true);
            roadmap.setTargetLevel("EXPERT");
            roadmapId = roadmapDao.insert(conn, roadmap);

            RoadmapStepDto step = new RoadmapStepDto();
            step.setRoadmapId(roadmapId);
            step.setStepOrder(1);
            step.setStepType("SKILL");
            step.setTier("ENTRY");
            step.setReason("테스트");
            step.setCompleted(false);
            stepId = roadmapStepDao.insert(conn, step);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "ROADMAP_STEP", stepId);
            TestFixtures.hardDelete(conn, "ROADMAP", roadmapId);
            TestFixtures.hardDelete(conn, "GAP_ANALYSIS", gapAnalysisId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 완료로_바꿀_수_있다() throws Exception {
        service.setCompleted(stepId, userId, true);

        List<RoadmapStepDto> steps = service.listSteps(roadmapId);
        assertTrue(steps.get(0).isCompleted());
        assertNotNull(steps.get(0).getCompletedAt());
    }

    @Test
    void 완료를_미완료로_되돌릴_수_있다() throws Exception {
        service.setCompleted(stepId, userId, true);
        service.setCompleted(stepId, userId, false);

        assertFalse(service.listSteps(roadmapId).get(0).isCompleted());
    }

    @Test
    void 다른_사용자의_단계는_못_바꾼다() throws Exception {
        UserDto other = new UserDto();
        other.setUserType("APPLICANT");
        other.setLoginId("admin_roadmap_other_" + System.nanoTime());
        other.setPasswordHash("dummy_hash");
        other.setDesiredJobStatus("UNSET");
        other.setPrivacyConsentAt(LocalDateTime.now());
        Long otherId = userDao.insert(other);
        try {
            assertThrows(IllegalArgumentException.class, () -> service.setCompleted(stepId, otherId, true));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "USERS", otherId);
            }
        }
    }

    @Test
    void 미완료_단계는_지울_수_있다() throws Exception {
        service.deleteIncompleteStep(stepId, userId);

        assertTrue(service.listSteps(roadmapId).isEmpty());
    }

    @Test
    void 이미_완료된_단계는_못_지운다() throws Exception {
        service.setCompleted(stepId, userId, true);

        assertThrows(IllegalArgumentException.class, () -> service.deleteIncompleteStep(stepId, userId));
    }
}
