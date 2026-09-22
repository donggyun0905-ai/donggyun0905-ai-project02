package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.GapAnalysisItemDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RoadmapService 통합테스트. 실제 spec_odyssey_test DB에 대고 검증한다.
 * 격차 분석(GAP_ANALYSIS 등)은 다른 담당자 기능이라 Service가 없으므로, 이 테스트가 직접
 * DAO로 데이터를 만들어 "격차 분석이 이미 끝나 있다"는 상황을 흉내 낸다.
 */
class RoadmapServiceTest {

    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisItemDao gapAnalysisItemDao = new GapAnalysisItemDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final RoadmapService roadmapService = new RoadmapService();

    private Long userId;
    private Long jobId;
    private Long requiredSkillId;
    private Long preferredSkillId;
    private Long gapAnalysisId;
    private Long jobRequiredSkillId1;
    private Long jobRequiredSkillId2;
    private Long gapItemId1;
    private Long gapItemId2;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("roadmap_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        // BACKEND 카테고리 직무 + 그 카테고리 자격증이 시드에 이미 있다 (예: 정보처리기사).
        JobDto backendJob = jobDao.findAll().stream()
                .filter(j -> "BACKEND".equals(j.getJobCategory()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("시드 데이터에 BACKEND 직무가 없습니다"));
        jobId = backendJob.getId();

        List<CertificationDto> backendCerts = certificationDao.findByJobCategory("BACKEND");
        assertFalse(backendCerts.isEmpty(), "시드 데이터에 BACKEND 자격증이 있어야 한다");

        try (Connection conn = DBUtil.getConnection()) {
            requiredSkillId = TestFixtures.insertSkill(conn, "테스트필수스킬_" + System.nanoTime());
            preferredSkillId = TestFixtures.insertSkill(conn, "테스트우대스킬_" + System.nanoTime());

            JobRequiredSkillDto req = new JobRequiredSkillDto();
            req.setJobId(jobId);
            req.setSkillId(requiredSkillId);
            req.setImportance("REQUIRED");
            req.setSource("MANUAL");
            req.setEstimated(false);
            jobRequiredSkillId1 = jobRequiredSkillDao.insert(conn, req);

            JobRequiredSkillDto pref = new JobRequiredSkillDto();
            pref.setJobId(jobId);
            pref.setSkillId(preferredSkillId);
            pref.setImportance("PREFERRED");
            pref.setSource("MANUAL");
            pref.setEstimated(false);
            jobRequiredSkillId2 = jobRequiredSkillDao.insert(conn, pref);

            GapAnalysisDto analysis = new GapAnalysisDto();
            analysis.setUserId(userId);
            analysis.setJobId(jobId);
            analysis.setMatchRate(new BigDecimal("40.00"));
            analysis.setAnalyzedAt(LocalDateTime.now());
            gapAnalysisId = gapAnalysisDao.insert(conn, analysis);

            // 일부러 PREFERRED 것을 먼저 넣는다 — 점수 정렬이 삽입 순서가 아니라 점수로 되는지 확인하려고.
            GapAnalysisItemDto preferredItem = new GapAnalysisItemDto();
            preferredItem.setGapAnalysisId(gapAnalysisId);
            preferredItem.setSkillId(preferredSkillId);
            preferredItem.setStatus("MISSING");
            gapItemId2 = gapAnalysisItemDao.insert(conn, preferredItem);

            GapAnalysisItemDto requiredItem = new GapAnalysisItemDto();
            requiredItem.setGapAnalysisId(gapAnalysisId);
            requiredItem.setSkillId(requiredSkillId);
            requiredItem.setStatus("MISSING");
            gapItemId1 = gapAnalysisItemDao.insert(conn, requiredItem);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (RoadmapDto roadmap : roadmapDao.findByUserId(userId)) {
                for (RoadmapStepDto step : roadmapStepDao.findByRoadmapId(roadmap.getId())) {
                    TestFixtures.hardDelete(conn, "ROADMAP_STEP", step.getId());
                }
                TestFixtures.hardDelete(conn, "ROADMAP", roadmap.getId());
            }
            TestFixtures.hardDelete(conn, "GAP_ANALYSIS_ITEM", gapItemId1);
            TestFixtures.hardDelete(conn, "GAP_ANALYSIS_ITEM", gapItemId2);
            TestFixtures.hardDelete(conn, "GAP_ANALYSIS", gapAnalysisId);
            TestFixtures.hardDelete(conn, "JOB_REQUIRED_SKILL", jobRequiredSkillId1);
            TestFixtures.hardDelete(conn, "JOB_REQUIRED_SKILL", jobRequiredSkillId2);
            TestFixtures.hardDelete(conn, "SKILL", requiredSkillId);
            TestFixtures.hardDelete(conn, "SKILL", preferredSkillId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 격차분석_없으면_예외를_던진다() {
        assertThrows(RoadmapService.NoGapAnalysisException.class,
                () -> roadmapService.generate(999_999_999L));
    }

    @Test
    void 로드맵_생성시_자격증_프로젝트_스킬_순서로_배치되고_스킬은_점수순으로_정렬된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        assertNotNull(roadmapId);

        RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(userId);
        assertNotNull(roadmap);
        assertEquals(roadmapId, roadmap.getId());
        assertEquals(1, roadmap.getVersion());

        List<RoadmapStepDto> steps = roadmapService.getSteps(roadmapId);
        assertTrue(steps.size() >= 4, "CERT 1 + PROJECT 1 + SKILL 2개 이상이어야 한다: " + steps.size());

        assertEquals("CERT", steps.get(0).getStepType());
        assertNotNull(steps.get(0).getCertificationId());
        assertFalse(steps.get(0).getReason().isBlank());

        assertEquals("PROJECT", steps.get(1).getStepType());
        assertFalse(steps.get(1).getReason().isBlank());

        List<RoadmapStepDto> skillSteps = steps.subList(2, steps.size());
        assertEquals(2, skillSteps.size());
        // REQUIRED였던 requiredSkillId가 PREFERRED였던 preferredSkillId보다 먼저 나와야 한다 (점수 내림차순).
        assertEquals(requiredSkillId, skillSteps.get(0).getRelatedSkillId());
        assertEquals(preferredSkillId, skillSteps.get(1).getRelatedSkillId());
        assertTrue(skillSteps.get(0).getReason().contains("필수"));
        assertTrue(skillSteps.get(1).getReason().contains("우대"));

        for (int i = 0; i < steps.size(); i++) {
            assertEquals(i + 1, steps.get(i).getStepOrder());
        }
    }

    @Test
    void 완료_체크가_실제로_반영된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto firstStep = roadmapService.getSteps(roadmapId).get(0);
        assertFalse(firstStep.isCompleted());

        roadmapService.completeStep(userId, firstStep.getId(), true);

        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(firstStep.getId()))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
        assertNotNull(updated.getCompletedAt());
    }

    @Test
    void 다른_사용자_id로는_완료체크가_안된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto firstStep = roadmapService.getSteps(roadmapId).get(0);

        roadmapService.completeStep(userId + 999_999L, firstStep.getId(), true);

        RoadmapStepDto stillNotCompleted = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(firstStep.getId()))
                .findFirst().orElseThrow();
        assertFalse(stillNotCompleted.isCompleted());
    }

    // ROADMAP.gap_analysis_id는 UNIQUE(1:1)라, "재생성"은 같은 분석을 또 넣는 게 아니라
    // 실제로 새 GAP_ANALYSIS가 생겼을 때(프로필 변경 후 재분석 등)를 뜻한다.
    @Test
    void 새로운_격차분석이_생기면_재생성시_버전이_올라가고_이전_버전은_대표에서_빠진다() throws Exception {
        Long firstRoadmapId = roadmapService.generate(userId);

        Long secondGapAnalysisId;
        try (Connection conn = DBUtil.getConnection()) {
            GapAnalysisDto secondAnalysis = new GapAnalysisDto();
            secondAnalysis.setUserId(userId);
            secondAnalysis.setJobId(jobId);
            secondAnalysis.setMatchRate(new BigDecimal("55.00"));
            secondAnalysis.setAnalyzedAt(LocalDateTime.now().plusMinutes(1));
            secondGapAnalysisId = gapAnalysisDao.insert(conn, secondAnalysis);

            GapAnalysisItemDto item = new GapAnalysisItemDto();
            item.setGapAnalysisId(secondGapAnalysisId);
            item.setSkillId(preferredSkillId);
            item.setStatus("MISSING");
            gapAnalysisItemDao.insert(conn, item);
        }

        try {
            Long secondRoadmapId = roadmapService.generate(userId);

            assertFalse(firstRoadmapId.equals(secondRoadmapId));

            RoadmapDto primary = roadmapService.getPrimaryRoadmap(userId);
            assertEquals(secondRoadmapId, primary.getId());
            assertEquals(2, primary.getVersion());

            RoadmapDto first = roadmapDao.findByUserId(userId).stream()
                    .filter(r -> r.getId().equals(firstRoadmapId))
                    .findFirst().orElseThrow();
            assertFalse(first.isActive());
            assertFalse(first.isPrimary());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                for (RoadmapDto roadmap : roadmapDao.findByUserId(userId)) {
                    if (roadmap.getGapAnalysisId().equals(secondGapAnalysisId)) {
                        for (RoadmapStepDto step : roadmapStepDao.findByRoadmapId(roadmap.getId())) {
                            TestFixtures.hardDelete(conn, "ROADMAP_STEP", step.getId());
                        }
                        TestFixtures.hardDelete(conn, "ROADMAP", roadmap.getId());
                    }
                }
                TestFixtures.hardDeleteByColumn(conn, "GAP_ANALYSIS_ITEM", "gap_analysis_id", secondGapAnalysisId);
                TestFixtures.hardDelete(conn, "GAP_ANALYSIS", secondGapAnalysisId);
            }
        }
    }

    // 같은 격차분석으로 "생성" 버튼을 두 번 눌러도(중복 클릭) UNIQUE(gap_analysis_id) 위반 없이 안전해야 한다.
    @Test
    void 같은_격차분석으로_다시_생성해도_로드맵이_중복생성되지_않는다() throws Exception {
        Long firstRoadmapId = roadmapService.generate(userId);
        Long secondCallRoadmapId = roadmapService.generate(userId);

        assertEquals(firstRoadmapId, secondCallRoadmapId);
        assertEquals(1, roadmapDao.findByUserId(userId).size());
    }

    // 부족한 기술이 5개(MAX_ENTRY_SKILL_STEPS)를 넘으면, 넘는 만큼은 버리지 않고 tier=CORE로 남는다.
    @Test
    void 부족한_기술이_5개_넘으면_상위_5개만_ENTRY고_나머지는_CORE로_밀린다() throws Exception {
        List<Long> extraSkillIds = new ArrayList<>();
        Long manyGapAnalysisId;
        try (Connection conn = DBUtil.getConnection()) {
            GapAnalysisDto analysis = new GapAnalysisDto();
            analysis.setUserId(userId);
            analysis.setJobId(jobId);
            analysis.setMatchRate(new BigDecimal("20.00"));
            analysis.setAnalyzedAt(LocalDateTime.now().plusMinutes(2));
            manyGapAnalysisId = gapAnalysisDao.insert(conn, analysis);

            // REQUIRED 7개를 부족한 걸로 넣는다 — 전부 동점이라 삽입 순서대로 잘리는지도 같이 확인된다.
            for (int i = 0; i < 7; i++) {
                Long skillId = TestFixtures.insertSkill(conn, "대량스킬" + i + "_" + System.nanoTime());
                extraSkillIds.add(skillId);

                JobRequiredSkillDto req = new JobRequiredSkillDto();
                req.setJobId(jobId);
                req.setSkillId(skillId);
                req.setImportance("REQUIRED");
                req.setSource("MANUAL");
                req.setEstimated(false);
                jobRequiredSkillDao.insert(conn, req);

                GapAnalysisItemDto item = new GapAnalysisItemDto();
                item.setGapAnalysisId(manyGapAnalysisId);
                item.setSkillId(skillId);
                item.setStatus("MISSING");
                gapAnalysisItemDao.insert(conn, item);
            }
        }

        Long roadmapId;
        try {
            roadmapId = roadmapService.generate(userId);
            List<RoadmapStepDto> steps = roadmapService.getSteps(roadmapId);

            long entrySkillCount = steps.stream()
                    .filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier()))
                    .count();
            long coreSkillCount = steps.stream()
                    .filter(s -> "SKILL".equals(s.getStepType()) && "CORE".equals(s.getTier()))
                    .count();

            assertEquals(5, entrySkillCount, "ENTRY SKILL 단계는 최대 5개여야 한다");
            assertEquals(2, coreSkillCount, "나머지 2개는 CORE로 남아야 한다 (버려지면 안 됨)");

            // CORE 단계는 완료 체크 대상이 아니므로 step_order가 ENTRY 단계들 뒤에 와야 한다.
            int lastEntryOrder = steps.stream()
                    .filter(s -> "ENTRY".equals(s.getTier()))
                    .mapToInt(RoadmapStepDto::getStepOrder).max().orElseThrow();
            int firstCoreOrder = steps.stream()
                    .filter(s -> "CORE".equals(s.getTier()))
                    .mapToInt(RoadmapStepDto::getStepOrder).min().orElseThrow();
            assertTrue(firstCoreOrder > lastEntryOrder);
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                for (RoadmapDto roadmap : roadmapDao.findByUserId(userId)) {
                    if (roadmap.getGapAnalysisId().equals(manyGapAnalysisId)) {
                        for (RoadmapStepDto step : roadmapStepDao.findByRoadmapId(roadmap.getId())) {
                            TestFixtures.hardDelete(conn, "ROADMAP_STEP", step.getId());
                        }
                        TestFixtures.hardDelete(conn, "ROADMAP", roadmap.getId());
                    }
                }
                TestFixtures.hardDeleteByColumn(conn, "GAP_ANALYSIS_ITEM", "gap_analysis_id", manyGapAnalysisId);
                TestFixtures.hardDelete(conn, "GAP_ANALYSIS", manyGapAnalysisId);
                for (Long skillId : extraSkillIds) {
                    TestFixtures.hardDeleteByColumn(conn, "JOB_REQUIRED_SKILL", "skill_id", skillId);
                    TestFixtures.hardDelete(conn, "SKILL", skillId);
                }
            }
        }
    }
}
