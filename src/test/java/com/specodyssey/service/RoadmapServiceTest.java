package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.GapAnalysisItemDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.ProjectDocumentItemDao;
import com.specodyssey.dao.ProjectTechNoteDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private final SkillDao skillDao = new SkillDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisItemDao gapAnalysisItemDao = new GapAnalysisItemDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    // 실제 Groq를 부르지 않는다 — 전체 테스트를 돌릴 때마다 429(한도 초과)로 20·40·60초씩
    // 기다려 1시간 넘게 걸리고 Groq 무료 한도도 같이 소모되던 문제(2026-10-01, youngjun 제안).
    private final RoadmapService roadmapService = new RoadmapService(new ProjectIdeaService(
            new StubLlmClient().register(ProjectIdeaService.ProjectIdea.class,
                    "{\"title\":\"테스트 프로젝트\",\"description\":\"테스트용 고정 설명\"}")));
    private final ScoreService scoreService = new ScoreService();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final DocumentDao documentDao = new DocumentDao();

    private final List<Long> trendTechIds = new ArrayList<>();
    private Long userId;
    private Long jobId;
    private Long requiredSkillId;
    private Long preferredSkillId;
    private Long gapAnalysisId;

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
            jobRequiredSkillDao.insert(conn, req);

            JobRequiredSkillDto pref = new JobRequiredSkillDto();
            pref.setJobId(jobId);
            pref.setSkillId(preferredSkillId);
            pref.setImportance("PREFERRED");
            pref.setSource("MANUAL");
            pref.setEstimated(false);
            jobRequiredSkillDao.insert(conn, pref);

            GapAnalysisDto analysis = new GapAnalysisDto();
            analysis.setUserId(userId);
            analysis.setJobId(jobId);
            analysis.setMatchRate(new BigDecimal("40.00"));
            analysis.setJobRequirementVersion(jobDao.findById(jobId).getRequirementVersion());
            analysis.setAnalyzedAt(LocalDateTime.now());
            gapAnalysisId = gapAnalysisDao.insert(conn, analysis);

            // 일부러 PREFERRED 것을 먼저 넣는다 — 점수 정렬이 삽입 순서가 아니라 점수로 되는지 확인하려고.
            GapAnalysisItemDto preferredItem = new GapAnalysisItemDto();
            preferredItem.setGapAnalysisId(gapAnalysisId);
            preferredItem.setSkillId(preferredSkillId);
            preferredItem.setStatus("MISSING");
            gapAnalysisItemDao.insert(conn, preferredItem);

            GapAnalysisItemDto requiredItem = new GapAnalysisItemDto();
            requiredItem.setGapAnalysisId(gapAnalysisId);
            requiredItem.setSkillId(requiredSkillId);
            requiredItem.setStatus("MISSING");
            gapAnalysisItemDao.insert(conn, requiredItem);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            // DOCUMENTS.project_id -> USER_PROJECTS, DOCUMENTS.roadmap_step_id -> ROADMAP_STEP가 둘 다
            // RESTRICT라 문서를 가장 먼저 지워야 한다(2026-09-30 SKILL 학습 검증 PDF 증빙 추가로
            // roadmap_step_id FK가 생기면서, ROADMAP_STEP보다 먼저 지우는 순서가 더 중요해졌다).
            for (Long trendId : trendTechIds) {
                TestFixtures.hardDeleteByColumn(conn, "TREND_TECH_JOB", "trend_tech_id", trendId);
                TestFixtures.hardDelete(conn, "TREND_TECH", trendId);
            }
            trendTechIds.clear();
            // 프로젝트 문서 체크리스트·기술 설명서는 DOCUMENTS/SKILL/USER_PROJECTS를 가리키므로 그보다 먼저.
            for (UserProjectDto project : userProjectDao.findByUserId(userId)) {
                TestFixtures.hardDeleteByColumn(conn, "PROJECT_DOCUMENT_ITEM", "project_id", project.getId());
                TestFixtures.hardDeleteByColumn(conn, "PROJECT_TECH_NOTE", "project_id", project.getId());
                TestFixtures.hardDeleteByColumn(conn, "PROJECT_LINK", "project_id", project.getId());
            }
            TestFixtures.hardDeleteByColumn(conn, "DOCUMENTS", "user_id", userId);
            // DAO 조회는 어느 단계에서든 is_deleted = FALSE만 돌려준다 — 업그레이드·복습 주기나
            // 재생성이 논리 삭제한 ROADMAP·ROADMAP_STEP은 거기 안 걸려 그대로 남고, SKILL·USERS를
            // 하드 삭제할 때 FK로 막는다. 정리는 소프트 삭제 여부와 상관없이 FK 컬럼으로 전부 지운다.
            for (Long roadmapId : TestFixtures.findIdsByColumn(conn, "ROADMAP", "user_id", userId)) {
                TestFixtures.hardDeleteByColumn(conn, "ROADMAP_STEP", "roadmap_id", roadmapId);
            }
            TestFixtures.hardDeleteByColumn(conn, "ROADMAP", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "SCORE_LOG", "user_id", userId);
            // 공유 DB라 다른 실행(대시보드·자정 스케줄러)이 테스트 사용자에게 점수 기록을 남길 수 있다 — 안 지우면 USERS 삭제가 FK에 막힌다
            TestFixtures.hardDeleteByColumn(conn, "SPEC_SCORE_HISTORY", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SCORE_SUMMARY", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SPECS", "user_id", userId);
            // USER_PROJECTS.upgraded_from_project_id는 자기참조 FK라, 업그레이드 체인이 있으면
            // 한 DELETE 문 안에서도 부모가 먼저 지워질 경우 RESTRICT에 걸릴 수 있다 — 지우기 전에
            // 참조부터 끊는다(2026-09-30 CORE/ADVANCED 업그레이드 기능 추가로 생긴 케이스).
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE USER_PROJECTS SET upgraded_from_project_id = NULL WHERE user_id = ?")) {
                pstmt.setLong(1, userId);
                pstmt.executeUpdate();
            }
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
            // 재분석은 GAP_ANALYSIS를 새로 만든다 — setUp이 만든 하나만 지우면 나머지가 남아
            // SKILL 하드 삭제를 막으므로, 이 사용자의 분석을 전부 지운다.
            for (Long analysisId : TestFixtures.findIdsByColumn(conn, "GAP_ANALYSIS", "user_id", userId)) {
                TestFixtures.hardDeleteByColumn(conn, "GAP_ANALYSIS_ITEM", "gap_analysis_id", analysisId);
            }
            TestFixtures.hardDeleteByColumn(conn, "GAP_ANALYSIS", "user_id", userId);
            // 이 테스트 기술을 팀 공용 BACKEND 직무의 요구 기술로 끼워 넣기 때문에, 그 사이에 분석을
            // 돌린 다른 테스트 사용자의 GAP_ANALYSIS_ITEM·ROADMAP_STEP도 이 기술을 물고 있을 수 있다.
            // 내가 만든 기술은 반드시 지워지도록 소유자와 상관없이 이 기술을 가리키는 행을 모두 끊는다.
            for (Long skillId : List.of(requiredSkillId, preferredSkillId)) {
                TestFixtures.hardDeleteByColumn(conn, "GAP_ANALYSIS_ITEM", "skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "ROADMAP_STEP", "related_skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "SKILL_ALIAS", "skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "JOB_REQUIRED_SKILL", "skill_id", skillId);
                TestFixtures.hardDelete(conn, "SKILL", skillId);
            }
            // SpecScoreScheduler는 웹앱이 뜨는 순간 전체 사용자에게 스냅샷을 남긴다 — 누가 같은 공유 DB로
            // 서버를 띄워 두면 테스트가 방금 만든 사용자 몫까지 생긴다. USERS 바로 앞에서 지워 그 창을 줄인다.
            TestFixtures.hardDeleteByColumn(conn, "SPEC_SCORE_HISTORY", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    // 기술 단계 점수의 기대값 — 직무 사다리 총점을 가중치로 나눈 값(StepPointCalculator). 이 테스트의 기술들은
    // 모두 첫 로드맵보다 먼저 만들어져 있어서 기준 시각 없이 계산해도 같다.
    private int skillPoints(Long skillId, String tier) throws Exception {
        return StepPointCalculator.skillStepPoints(jobRequiredSkillDao.findByJobId(jobId), null, skillId, tier);
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

        // 직무 자격증 다음에 공통 자격증(어학·컴활)이 한 칸 더 올 수 있고, 자격증들 바로 뒤가 프로젝트다
        int afterCerts = 0;
        while ("CERT".equals(steps.get(afterCerts).getStepType())) {
            afterCerts++;
        }
        assertTrue(afterCerts <= 2, "자격증은 직무 1 + 공통 1까지: " + afterCerts);
        assertEquals("PROJECT", steps.get(afterCerts).getStepType());
        assertFalse(steps.get(afterCerts).getReason().isBlank());

        // 기술별 사다리 — 시드 직무에 요구 기술이 더 있어 5개(라운드)로 보충되고, 기술마다 4개 티어 단계가 생긴다.
        // 입문 티어의 SKILL 단계는 점수 내림차순이어야 한다.
        List<RoadmapStepDto> entrySkillSteps = steps.stream()
                .filter(st -> "SKILL".equals(st.getStepType()) && "ENTRY".equals(st.getTier()))
                .toList();
        assertEquals(5, entrySkillSteps.size());
        // REQUIRED였던 requiredSkillId가 PREFERRED였던 preferredSkillId보다 먼저 나와야 한다 (점수 내림차순).
        assertEquals(requiredSkillId, entrySkillSteps.get(0).getRelatedSkillId());
        assertTrue(entrySkillSteps.get(0).getReason().contains("필수"));
        assertTrue(entrySkillSteps.stream().anyMatch(st -> preferredSkillId.equals(st.getRelatedSkillId())));
        assertEquals(20, steps.stream().filter(st -> "SKILL".equals(st.getStepType())).count(),
                "5개 기술 × 4개 티어");

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
    void 완료_체크하면_점수가_100점_적립되고_취소해도_안_깎인다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto firstStep = roadmapService.getSteps(roadmapId).get(0);

        roadmapService.completeStep(userId, firstStep.getId(), true);
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());

        // 같은 단계를 다시 완료 처리해도(중복 클릭) 중복 적립되지 않는다.
        roadmapService.completeStep(userId, firstStep.getId(), true);
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());

        // 완료 취소해도 이미 받은 점수는 유지된다 (팀 합의).
        roadmapService.completeStep(userId, firstStep.getId(), false);
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());
    }

    // 로드맵의 "완료하기"를 거치지 않고 프로필에서 직접 자격증을 추가한 경우 — 같은 자격증을
    // 요구하던 CERT 단계가 있으면 그것도 완료 처리돼야 한다 (팀 합의, 2026-09-23).
    @Test
    void 프로필에서_직접_자격증을_추가하면_일치하는_CERT_단계도_완료되고_점수도_적립된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto certStep = roadmapService.getSteps(roadmapId).get(0);
        assertEquals("CERT", certStep.getStepType());

        CertificationDto cert;
        try (Connection conn = DBUtil.getConnection()) {
            cert = certificationDao.findById(conn, certStep.getCertificationId());
        }

        // 로드맵을 거치지 않고 프로필 페이지에서 직접 추가한 것처럼 USER_SPECS에 바로 넣는다.
        UserSpecDto spec = new UserSpecDto();
        spec.setUserId(userId);
        spec.setSpecType("CERT");
        spec.setTitle(cert.getCertName());
        try (Connection conn = DBUtil.getConnection()) {
            userSpecDao.insert(conn, spec);
        }

        roadmapService.syncCertAddedFromProfile(userId, cert.getCertName());

        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(certStep.getId()))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());

        // 중복 등록 없이 그대로 1건이어야 한다 (completeStep이 다시 넣으려다 가드에 막힘).
        long matching = userSpecDao.findByUserId(userId).stream()
                .filter(s -> "CERT".equals(s.getSpecType()) && cert.getCertName().equals(s.getTitle()))
                .count();
        assertEquals(1, matching);
    }

    @Test
    void 이미_보유한_자격증과_일치하는_CERT_단계가_없으면_아무_일도_안_일어난다() throws Exception {
        roadmapService.generate(userId);
        // 로드맵과 무관한 이름의 자격증 — 어떤 CERT 단계와도 안 겹친다.
        roadmapService.syncCertAddedFromProfile(userId, "존재하지_않는_자격증_" + System.nanoTime());
        assertNull(scoreService.getSummary(userId));
    }

    // CERT와 대칭인 케이스 — 자격증뿐 아니라 스킬도 프로필에서 직접 추가하면 겹칠 수 있다(사용자 지적,
    // 2026-09-23). 다만 1주차 설계상 수동 입력은 skill_id가 항상 NULL이라(UserSkillDao 주석 참고),
    // 이름이 같으면 새 행을 또 만들지 않고 기존 행에 skill_id를 채워 넣는지까지 같이 검증한다.
    @Test
    void 프로필에서_직접_스킬을_추가하면_일치하는_SKILL_단계도_완료되고_중복행_없이_병합된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto skillStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()))
                .findFirst().orElseThrow();
        SkillDto skill = skillDao.findById(skillStep.getRelatedSkillId());

        // 로드맵을 거치지 않고 프로필 페이지에서 직접 추가한 것처럼(수동 입력 = skill_id NULL) 넣는다.
        UserSkillDto manual = new UserSkillDto();
        manual.setUserId(userId);
        manual.setRawInput(skill.getSkillName());
        Long manualId;
        try (Connection conn = DBUtil.getConnection()) {
            manualId = userSkillDao.insert(conn, manual);
        }

        roadmapService.syncSkillAddedFromProfile(userId, skill.getSkillName());

        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(skillStep.getId()))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
        assertEquals(skillPoints(skillStep.getRelatedSkillId(), skillStep.getTier()), scoreService.getSummary(userId).getTotalScore());

        List<UserSkillDto> skills = userSkillDao.findByUserId(userId);
        assertEquals(1, skills.size(), "새 행이 또 생기지 않고 기존 수동 입력 행에 합쳐져야 한다");
        assertEquals(manualId, skills.get(0).getId());
        assertEquals(skillStep.getRelatedSkillId(), skills.get(0).getSkillId());
        assertEquals("BEGINNER", skills.get(0).getProficiency());
    }

    @Test
    void 이미_완료된_PROJECT_단계에_다시_제출하면_completeProjectStep이_false를_반환한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        assertTrue(roadmapService.completeProjectStep(userId, projectStep.getId(), sampleSubmission()));
        // 재제출 — 호출부(서블릿)가 이 반환값으로 "방금 저장한 파일을 지워야 하는지" 판단한다.
        assertFalse(roadmapService.completeProjectStep(userId, projectStep.getId(), sampleSubmission()));
    }

    @Test
    void 다른_사용자_id로_PROJECT_단계를_제출하면_completeProjectStep이_false를_반환한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        assertFalse(roadmapService.completeProjectStep(userId + 999_999L, projectStep.getId(), sampleSubmission()));
        assertNull(scoreService.getSummary(userId));
    }

    @Test
    void SKILL_단계를_완료하면_USER_SKILLS에_비기너로_자동_등록된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto skillStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()))
                .findFirst().orElseThrow();

        roadmapService.completeStep(userId, skillStep.getId(), true);

        UserSkillDto userSkill;
        try (Connection conn = DBUtil.getConnection()) {
            userSkill = userSkillDao.findByUserIdAndSkillId(conn, userId, skillStep.getRelatedSkillId());
        }
        assertNotNull(userSkill);
        assertEquals("BEGINNER", userSkill.getProficiency());
    }

    @Test
    void CERT_단계를_완료하면_USER_SPECS에_자동_등록되고_중복_완료해도_한번만_등록된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto certStep = roadmapService.getSteps(roadmapId).get(0);
        assertEquals("CERT", certStep.getStepType());

        CertificationDto cert;
        try (Connection conn = DBUtil.getConnection()) {
            cert = certificationDao.findById(conn, certStep.getCertificationId());
        }

        roadmapService.completeStep(userId, certStep.getId(), true);
        // 완료 취소 후 재완료해도(다시 체크) 중복 등록되지 않는다.
        roadmapService.completeStep(userId, certStep.getId(), false);
        roadmapService.completeStep(userId, certStep.getId(), true);

        List<UserSpecDto> specs = userSpecDao.findByUserId(userId);
        long matching = specs.stream()
                .filter(s -> "CERT".equals(s.getSpecType()) && cert.getCertName().equals(s.getTitle()))
                .count();
        assertEquals(1, matching);
    }

    @Test
    void 같은_스킬_관련_완료가_쌓이면_숙련도가_승급하고_취소해도_안_내려간다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto skillStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()))
                .findFirst().orElseThrow();
        Long skillId = skillStep.getRelatedSkillId();

        // 같은 스킬을 겨냥한 SKILL 단계 29개를 추가로 만든다 (기존 1개 + 29개 = 30번째에서 ADVANCED 승급 확인).
        List<Long> extraStepIds = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection()) {
            for (int i = 0; i < 29; i++) {
                RoadmapStepDto step = new RoadmapStepDto();
                step.setRoadmapId(roadmapId);
                step.setStepOrder(100 + i);
                step.setStepType("SKILL");
                step.setTier("ENTRY"); // 단계 기준 승급(핵심 이상)과 섞이지 않게 횟수 기준만 검증한다
                step.setRelatedSkillId(skillId);
                step.setReason("승급 테스트용");
                step.setCompleted(false);
                extraStepIds.add(roadmapStepDao.insert(conn, step));
            }
        }

        roadmapService.completeStep(userId, skillStep.getId(), true);
        for (int i = 0; i < 8; i++) {
            roadmapService.completeStep(userId, extraStepIds.get(i), true);
        }
        // 여기까지 9번 완료 — 아직 INTERMEDIATE 문턱(10) 전이라 BEGINNER 그대로.
        assertEquals("BEGINNER", currentProficiency(skillId));

        roadmapService.completeStep(userId, extraStepIds.get(8), true);
        // 10번째 완료 — INTERMEDIATE로 승급.
        assertEquals("INTERMEDIATE", currentProficiency(skillId));

        for (int i = 9; i < 28; i++) {
            roadmapService.completeStep(userId, extraStepIds.get(i), true);
        }
        // 29번째 완료 — 아직 ADVANCED 문턱(30) 전.
        assertEquals("INTERMEDIATE", currentProficiency(skillId));

        roadmapService.completeStep(userId, extraStepIds.get(28), true);
        // 30번째 완료 — ADVANCED로 승급.
        assertEquals("ADVANCED", currentProficiency(skillId));

        // 완료를 취소해도(체크 해제) 이미 딴 숙련도는 내려가지 않는다 (팀 합의).
        roadmapService.completeStep(userId, extraStepIds.get(28), false);
        assertEquals("ADVANCED", currentProficiency(skillId));
    }

    private String currentProficiency(Long skillId) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            return userSkillDao.findByUserIdAndSkillId(conn, userId, skillId).getProficiency();
        }
    }

    @Test
    void PROJECT_단계는_README와_실행화면_없이는_완료할_수_없다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        // README·실행 화면 없이 프로젝트 정보만 낸 경우
        ProjectSubmission noDocs = new ProjectSubmission(sampleProject());
        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.completeProjectStep(userId, projectStep.getId(), noDocs));
        assertFalse(roadmapService.getSteps(roadmapId).stream()
                .filter(st -> st.getId().equals(projectStep.getId())).findFirst().orElseThrow().isCompleted());
        assertTrue(userProjectDao.findByUserId(userId).isEmpty(), "검증에 실패하면 프로젝트도 만들면 안 된다");
    }

    @Test
    void PROJECT_단계를_파일과_함께_완료하면_프로젝트와_문서가_등록되고_점수도_적립된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        roadmapService.completeProjectStep(userId, projectStep.getId(), sampleSubmission());

        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(projectStep.getId()))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());

        List<UserProjectDto> projects = userProjectDao.findByUserId(userId);
        assertEquals(1, projects.size());
        assertEquals("테스트 프로젝트", projects.get(0).getTitle());

        List<DocumentDto> documents = documentDao.findByUserId(userId);
        assertEquals(2, documents.size(), "README와 실행 화면 두 개");
        assertEquals(projects.get(0).getId(), documents.get(0).getProjectId());
        assertEquals(projects.get(0).getId(), updated.getEvidenceProjectId(), "단계와 프로젝트가 연결돼야 한다");
    }

    @Test
    void 이미_완료된_PROJECT_단계를_다시_제출해도_프로젝트가_중복_생성되지_않는다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        roadmapService.completeProjectStep(userId, projectStep.getId(), sampleSubmission());
        roadmapService.completeProjectStep(userId, projectStep.getId(), sampleSubmission());

        assertEquals(1, userProjectDao.findByUserId(userId).size());
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());
    }

    // 완료 취소는 표시만 푼다 — 프로젝트·서류는 남고, 다시 완료해도 프로젝트가 새로 생기지 않는다(개발일지 4-4).
    @Test
    void PROJECT_단계를_완료_취소하면_표시만_풀리고_프로젝트와_서류는_남는다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType())).findFirst().orElseThrow();
        roadmapService.completeProjectStep(userId, projectStep.getId(), sampleSubmission());

        roadmapService.completeStep(userId, projectStep.getId(), false);

        RoadmapStepDto cancelled = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(projectStep.getId())).findFirst().orElseThrow();
        assertFalse(cancelled.isCompleted());
        assertEquals(1, userProjectDao.findByUserId(userId).size());
        assertEquals(2, documentDao.findByUserId(userId).size());
        assertEquals(100, scoreService.getSummary(userId).getTotalScore(), "받은 점수는 그대로");
    }

    @Test
    void 완료_취소_뒤_다시_완료하면_같은_프로젝트를_갱신하고_이미_낸_필수_서류는_다시_안_올려도_된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType())).findFirst().orElseThrow();
        roadmapService.completeProjectStep(userId, projectStep.getId(), sampleSubmission());
        Long projectId = userProjectDao.findByUserId(userId).get(0).getId();
        roadmapService.completeStep(userId, projectStep.getId(), false);

        // 이번엔 파일 없이, 회고만 고쳐서 다시 완료
        UserProjectDto edited = sampleProject();
        edited.setTitle("고친 제목");
        edited.setRepoUrl("https://github.com/example/repo");
        edited.setRetrospective("다시 해보니 좋았다");
        assertTrue(roadmapService.completeProjectStep(userId, projectStep.getId(), new ProjectSubmission(edited)));

        List<UserProjectDto> projects = userProjectDao.findByUserId(userId);
        assertEquals(1, projects.size(), "프로젝트가 중복으로 생기면 안 된다");
        assertEquals(projectId, projects.get(0).getId());
        assertEquals("고친 제목", projects.get(0).getTitle());
        assertEquals("https://github.com/example/repo", projects.get(0).getRepoUrl());
        assertEquals("다시 해보니 좋았다", projects.get(0).getRetrospective());
        assertEquals(2, documentDao.findByUserId(userId).size());
    }

    @Test
    void 서류_보관함에서_README를_지우면_다시_완료할_때_README를_다시_내야_한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType())).findFirst().orElseThrow();
        roadmapService.completeProjectStep(userId, projectStep.getId(), sampleSubmission());
        roadmapService.completeStep(userId, projectStep.getId(), false);

        Long readmeId = new ProjectDocumentItemDao().findByProjectId(userProjectDao.findByUserId(userId).get(0).getId())
                .stream().filter(i -> "README".equals(i.getDocType())).findFirst().orElseThrow().getDocumentId();
        assertTrue(new DocumentService().delete(userId, readmeId));

        assertThrows(IllegalArgumentException.class, () -> roadmapService.completeProjectStep(
                userId, projectStep.getId(), new ProjectSubmission(sampleProject())));
        // README를 다시 내면 완료된다
        ProjectSubmission again = new ProjectSubmission(sampleProject());
        again.getDocs().put("README", ProjectSubmission.DocSlot.submitted(sampleDocument()));
        assertTrue(roadmapService.completeProjectStep(userId, projectStep.getId(), again));
        assertEquals(1, userProjectDao.findByUserId(userId).size());
    }

    @Test
    void 필수_문서를_해당_없음으로_두거나_주소_형식이_틀리면_완료할_수_없다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType())).findFirst().orElseThrow();

        ProjectSubmission na = sampleSubmission();
        na.getDocs().put("README", ProjectSubmission.DocSlot.notApplicable());
        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.completeProjectStep(userId, projectStep.getId(), na));

        ProjectSubmission badUrl = sampleSubmission();
        badUrl.getProject().setRepoUrl("javascript:alert(1)");
        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.completeProjectStep(userId, projectStep.getId(), badUrl));

        assertTrue(userProjectDao.findByUserId(userId).isEmpty());
    }

    @Test
    void 선택_문서는_해당_없음으로_저장되고_등록된_기술만_활용_설명서가_저장된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType())).findFirst().orElseThrow();
        String knownSkill = new SkillDao().findById(requiredSkillId).getSkillName();

        ProjectSubmission submission = sampleSubmission();
        submission.getDocs().put("API_SPEC", ProjectSubmission.DocSlot.notApplicable());
        submission.getTechNotes().add(new ProjectSubmission.TechNote(knownSkill, "핵심 로직에 사용", true));
        submission.getTechNotes().add(new ProjectSubmission.TechNote("없는기술xyz", "설명", false));
        roadmapService.completeProjectStep(userId, projectStep.getId(), submission);

        Long projectId = userProjectDao.findByUserId(userId).get(0).getId();
        var items = new ProjectDocumentItemDao().findByProjectId(projectId);
        assertEquals("NOT_APPLICABLE", items.stream().filter(i -> "API_SPEC".equals(i.getDocType()))
                .findFirst().orElseThrow().getStatus());
        var notes = new ProjectTechNoteDao().findByProjectId(projectId);
        assertEquals(1, notes.size());
        assertEquals("핵심 로직에 사용", notes.get(0).getDescription());
        assertEquals(List.of("없는기술xyz"), submission.getSkippedTechNotes());
    }

    // ---- 기술 복습(끝없는 로드맵) — 단계별 차등 주기, 기존 여정 뒤에 이어 붙이기, 점수 감쇠

    @Test
    void 복습_주기는_단계가_높을수록_길고_점수는_복습할수록_줄어든다() {
        assertEquals(30, RoadmapService.reviewIntervalDays("ENTRY"));
        assertEquals(60, RoadmapService.reviewIntervalDays("CORE"));
        assertEquals(90, RoadmapService.reviewIntervalDays("ADVANCED"));
        assertEquals(120, RoadmapService.reviewIntervalDays("EXPERT"));
        assertEquals(40, RoadmapService.reviewPoints(0));
        assertEquals(30, RoadmapService.reviewPoints(1));
        assertEquals(10, RoadmapService.reviewPoints(3));
        assertEquals(5, RoadmapService.reviewPoints(4));
        assertEquals(5, RoadmapService.reviewPoints(50), "최저 점수 아래로는 내려가지 않는다");
    }

    @Test
    void 입문을_끝낸_지_30일이_지나야_복습이_생기고_여정_맨_뒤에_이어_붙는다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto entry = firstEntrySkillStep(roadmapId);
        roadmapService.completeStep(userId, entry.getId(), true);

        setCompletedAt(entry.getId(), LocalDateTime.now().minusDays(29));
        assertEquals(0, roadmapService.appendDueReviews(userId, LocalDateTime.now()), "29일째는 아직");

        setCompletedAt(entry.getId(), LocalDateTime.now().minusDays(31));
        assertEquals(1, roadmapService.appendDueReviews(userId, LocalDateTime.now()));

        List<RoadmapStepDto> steps = roadmapService.getSteps(roadmapId);
        RoadmapStepDto review = steps.get(steps.size() - 1);
        assertEquals("REVIEW", review.getStepType());
        assertEquals(entry.getRelatedSkillId(), review.getRelatedSkillId());
        assertFalse(review.isCompleted());
        assertEquals(steps.stream().mapToInt(RoadmapStepDto::getStepOrder).max().getAsInt(), review.getStepOrder());

        assertEquals(0, roadmapService.appendDueReviews(userId, LocalDateTime.now()), "열린 복습이 있으면 또 만들지 않는다");
        // 복습 단계는 계단식 잠금 계산(티어별 진행도)에 끼어들지 않는다
        assertEquals(4, roadmapService.computeProgress(steps).getTiers().size());
    }

    @Test
    void 핵심까지_끝낸_기술은_60일이_지나야_복습이_생긴다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto entry = firstEntrySkillStep(roadmapId);
        RoadmapStepDto core = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && "CORE".equals(s.getTier())
                        && entry.getRelatedSkillId().equals(s.getRelatedSkillId()))
                .findFirst().orElseThrow();
        roadmapService.completeStep(userId, entry.getId(), true);
        roadmapService.completeStep(userId, core.getId(), true);

        setCompletedAt(entry.getId(), LocalDateTime.now().minusDays(100));
        setCompletedAt(core.getId(), LocalDateTime.now().minusDays(45));
        assertEquals(0, roadmapService.appendDueReviews(userId, LocalDateTime.now()), "입문 기준(30일)이 아니라 핵심 기준(60일)");

        setCompletedAt(core.getId(), LocalDateTime.now().minusDays(61));
        assertEquals(1, roadmapService.appendDueReviews(userId, LocalDateTime.now()));
    }

    @Test
    void 복습은_기록을_내야_끝나고_점수가_복습할수록_줄어든다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto entry = firstEntrySkillStep(roadmapId);
        roadmapService.completeStep(userId, entry.getId(), true);
        int afterEntry = scoreService.getSummary(userId).getTotalScore();
        setCompletedAt(entry.getId(), LocalDateTime.now().minusDays(31));
        roadmapService.appendDueReviews(userId, LocalDateTime.now());
        RoadmapStepDto review = lastStep(roadmapId);

        assertThrows(IllegalArgumentException.class, () -> roadmapService.completeStep(userId, review.getId(), true),
                "체크만으로는 복습을 끝낼 수 없다");
        assertThrows(IllegalArgumentException.class, () -> roadmapService.completeReview(userId, review.getId(), "짧음"));
        assertEquals(0, roadmapService.completeReview(userId + 999_999L, review.getId(), "가".repeat(30)),
                "남의 복습 단계는 끝낼 수 없다");

        assertEquals(40, roadmapService.completeReview(userId, review.getId(), "복습 기록을 충분히 길게 적었습니다. 핵심 개념 정리"));
        assertEquals(afterEntry + 40, scoreService.getSummary(userId).getTotalScore());
        assertEquals(0, roadmapService.completeReview(userId, review.getId(), "복습 기록을 충분히 길게 적었습니다. 핵심 개념 정리"),
                "이미 끝낸 복습에 또 점수를 주지 않는다");

        // 한 주기 뒤 두 번째 복습 — 점수가 30으로 줄어든다
        setCompletedAt(review.getId(), LocalDateTime.now().minusDays(31));
        assertEquals(1, roadmapService.appendDueReviews(userId, LocalDateTime.now()));
        RoadmapStepDto second = lastStep(roadmapId);
        assertEquals(30, roadmapService.completeReview(userId, second.getId(), "두 번째 복습 기록도 충분히 길게 적습니다 하하"));
    }

    private RoadmapStepDto firstEntrySkillStep(Long roadmapId) throws Exception {
        return roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier()))
                .findFirst().orElseThrow();
    }

    private RoadmapStepDto lastStep(Long roadmapId) throws Exception {
        List<RoadmapStepDto> steps = roadmapService.getSteps(roadmapId);
        return steps.get(steps.size() - 1);
    }

    private void setCompletedAt(Long stepId, LocalDateTime at) throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("UPDATE ROADMAP_STEP SET completed_at = ? WHERE id = ?")) {
            pstmt.setTimestamp(1, java.sql.Timestamp.valueOf(at));
            pstmt.setLong(2, stepId);
            pstmt.executeUpdate();
        }
    }

    // ---- 프로젝트 기타 링크(블로그 글·발표 영상 등)

    private RoadmapStepDto firstProjectStep(Long roadmapId) throws Exception {
        return roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType())).findFirst().orElseThrow();
    }

    @Test
    void 프로젝트를_완료하며_낸_기타_링크가_저장되고_다시_완료할_때_입력칸이_없으면_그대로_둔다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto step = firstProjectStep(roadmapId);
        ProjectSubmission submission = sampleSubmission();
        submission.setLinks(List.of(new com.specodyssey.dto.ProjectLinkDto(" 블로그 ", " https://blog.example.com/p "),
                new com.specodyssey.dto.ProjectLinkDto("", "")));
        roadmapService.completeProjectStep(userId, step.getId(), submission);

        Long projectId = userProjectDao.findByUserId(userId).get(0).getId();
        List<com.specodyssey.dto.ProjectLinkDto> saved = new com.specodyssey.dao.ProjectLinkDao().findByProjectId(projectId);
        assertEquals(1, saved.size(), "빈 줄은 저장하지 않는다");
        assertEquals("블로그", saved.get(0).getLabel());
        assertEquals("https://blog.example.com/p", saved.get(0).getUrl());

        // 완료 취소 후 다시 완료 — 링크 입력칸 없이(links == null) 내면 기존 링크를 건드리지 않는다
        roadmapService.completeStep(userId, step.getId(), false);
        roadmapService.completeProjectStep(userId, step.getId(), new ProjectSubmission(sampleProject()));
        assertEquals(1, new com.specodyssey.dao.ProjectLinkDao().findByProjectId(projectId).size());

        // 이번엔 입력칸이 있었고 전부 비웠다 = 링크를 모두 지운다
        roadmapService.completeStep(userId, step.getId(), false);
        ProjectSubmission cleared = new ProjectSubmission(sampleProject());
        cleared.setLinks(List.of());
        roadmapService.completeProjectStep(userId, step.getId(), cleared);
        assertTrue(new com.specodyssey.dao.ProjectLinkDao().findByProjectId(projectId).isEmpty());
    }

    @Test
    void 주소가_잘못된_링크가_있으면_프로젝트도_완료도_만들어지지_않는다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto step = firstProjectStep(roadmapId);
        ProjectSubmission bad = sampleSubmission();
        bad.setLinks(List.of(new com.specodyssey.dto.ProjectLinkDto("나쁜 링크", "javascript:alert(1)")));

        assertThrows(IllegalArgumentException.class, () -> roadmapService.completeProjectStep(userId, step.getId(), bad));

        assertTrue(userProjectDao.findByUserId(userId).isEmpty());
        assertFalse(roadmapService.getSteps(roadmapId).stream()
                .filter(st -> st.getId().equals(step.getId())).findFirst().orElseThrow().isCompleted());
    }

    @Test
    void 임시저장된_링크가_다시_열_때의_초안에_실린다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto step = firstProjectStep(roadmapId);
        ProjectSubmission submission = sampleSubmission();
        submission.setLinks(List.of(new com.specodyssey.dto.ProjectLinkDto("발표 영상", "https://youtu.be/abc")));
        roadmapService.completeProjectStep(userId, step.getId(), submission);
        roadmapService.completeStep(userId, step.getId(), false);

        RoadmapStepDto reread = roadmapService.getSteps(roadmapId).stream()
                .filter(st -> st.getId().equals(step.getId())).findFirst().orElseThrow();
        ProjectSubmissionService.ProjectDraft draft =
                new ProjectSubmissionService().loadDraft(userId, reread.getEvidenceProjectId());
        assertEquals(1, draft.getLinks().size());
        assertEquals("발표 영상", draft.getLinks().get(0).getLabel());
    }

    // ---- 끝없는 로드맵의 유지·성장 단계: 프로젝트 업데이트 · 기술 글 업데이트 · 트렌딩 학습

    private void sql(String statement, Object... params) throws Exception {
        try (Connection conn = DBUtil.getConnection(); PreparedStatement pstmt = conn.prepareStatement(statement)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            pstmt.executeUpdate();
        }
    }

    private java.sql.Timestamp daysAgo(int days) {
        return java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(days));
    }

    private List<RoadmapStepDto> stepsOfType(Long roadmapId, String type) throws Exception {
        return roadmapService.getSteps(roadmapId).stream().filter(st -> type.equals(st.getStepType())).collect(Collectors.toList());
    }

    @Test
    void 프로젝트를_90일_손대지_않으면_업데이트_단계가_이어_붙고_끝내면_점수가_줄며_손본_날이_갱신된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        UserProjectDto fresh = sampleProject();
        fresh.setUserId(userId);
        fresh.setTitle("최근 프로젝트");
        userProjectDao.insert(fresh);
        UserProjectDto old = sampleProject();
        old.setUserId(userId);
        old.setTitle("오래된 프로젝트");
        Long oldId = userProjectDao.insert(old);
        sql("UPDATE USER_PROJECTS SET updated_at = ? WHERE id = ?", daysAgo(100), oldId);

        assertEquals(1, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()), "손 안 댄 프로젝트만");
        RoadmapStepDto step = stepsOfType(roadmapId, "PROJECT_UPDATE").get(0);
        assertEquals(oldId, step.getEvidenceProjectId());
        assertEquals("REVIEW", step.getTier());
        assertTrue(step.getReason().contains("오래된 프로젝트"));
        assertEquals(0, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()), "열린 업데이트가 있으면 또 만들지 않는다");

        assertThrows(IllegalArgumentException.class, () -> roadmapService.completeStep(userId, step.getId(), true),
                "체크만으로는 끝낼 수 없다");
        assertThrows(IllegalArgumentException.class, () -> roadmapService.completeUpkeep(userId, step.getId(), "짧음"));
        assertEquals(0, roadmapService.completeUpkeep(userId + 999_999L, step.getId(), "가".repeat(30)), "남의 단계는 끝낼 수 없다");

        assertEquals(60, roadmapService.completeUpkeep(userId, step.getId(), "README에 실행 방법을 쓰고 로그인 버그를 고쳤습니다."));
        assertTrue(userProjectDao.findById(oldId, userId).getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(5)),
                "마지막으로 손본 날이 지금으로 바뀐다");
        assertEquals(0, roadmapService.completeUpkeep(userId, step.getId(), "가".repeat(30)), "이미 끝낸 단계에 또 점수를 주지 않는다");

        // 한 주기가 다시 지나면 두 번째 업데이트 — 같은 프로젝트라 50점
        sql("UPDATE USER_PROJECTS SET updated_at = ? WHERE id = ?", daysAgo(100), oldId);
        assertEquals(0, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()),
                "방금 업데이트 기록을 냈으면 프로젝트 수정 시각이 오래됐어도 기록 시각부터 센다");
        sql("UPDATE ROADMAP_STEP SET completed_at = ? WHERE id = ?", daysAgo(100), step.getId());
        assertEquals(1, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()));
        RoadmapStepDto second = stepsOfType(roadmapId, "PROJECT_UPDATE").stream().filter(st -> !st.isCompleted()).findFirst().orElseThrow();
        assertEquals(50, roadmapService.completeUpkeep(userId, second.getId(), "배포 주소를 바꾸고 테스트를 추가했습니다 하하"));
    }

    @Test
    void 삭제한_프로젝트의_업데이트_단계는_끝낼_수_없다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        UserProjectDto project = sampleProject();
        project.setUserId(userId);
        Long id = userProjectDao.insert(project);
        sql("UPDATE USER_PROJECTS SET updated_at = ? WHERE id = ?", daysAgo(100), id);
        roadmapService.appendDueUpkeep(userId, LocalDateTime.now());
        RoadmapStepDto step = stepsOfType(roadmapId, "PROJECT_UPDATE").get(0);
        sql("UPDATE USER_PROJECTS SET is_deleted = TRUE WHERE id = ?", id);

        assertThrows(IllegalArgumentException.class, () -> roadmapService.completeUpkeep(userId, step.getId(), "가".repeat(30)));
        assertFalse(stepsOfType(roadmapId, "PROJECT_UPDATE").get(0).isCompleted());
    }

    @Test
    void 전문가_글을_150일_전에_냈으면_글_업데이트가_생기고_규칙을_통과해야_끝난다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto expert = roadmapService.getSteps(roadmapId).stream()
                .filter(st -> "SKILL".equals(st.getStepType()) && "EXPERT".equals(st.getTier())).findFirst().orElseThrow();
        // [TEST] 통과처럼 증빙 없이 끝낸 전문가 단계는 글이 없으니 대상이 아니다
        sql("UPDATE ROADMAP_STEP SET is_completed = TRUE, completed_at = ? WHERE id = ?", daysAgo(200), expert.getId());
        assertEquals(0, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()), "글을 낸 적 없으면 업데이트할 글도 없다");

        sql("UPDATE ROADMAP_STEP SET proof_type = 'TEACHING_POST' WHERE id = ?", expert.getId());
        assertEquals(1, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()));
        RoadmapStepDto step = stepsOfType(roadmapId, "ARTICLE_UPDATE").get(0);
        assertEquals(expert.getRelatedSkillId(), step.getRelatedSkillId());

        String skillName = skillDao.findById(expert.getRelatedSkillId()).getSkillName();
        // 규칙 미달(너무 짧음) → 완료되지 않고 이유가 남는다
        var fail = roadmapService.submitArticleUpdate(userId, step.getId(), "너무 짧은 글", sampleDocument());
        assertEquals(SkillProofGrader.NEEDS_REVISION, fail.status());
        RoadmapStepDto after = stepsOfType(roadmapId, "ARTICLE_UPDATE").get(0);
        assertFalse(after.isCompleted());
        assertEquals("NEEDS_REVISION", after.getReviewStatus());
        int before = scoreService.getSummary(userId) == null ? 0 : scoreService.getSummary(userId).getTotalScore();

        String good = (skillName + " ").repeat(150) + "https://example.com/docs";
        var ok = roadmapService.submitArticleUpdate(userId, step.getId(), good, sampleDocument());
        assertEquals(SkillProofGrader.PASSED, ok.status());
        assertTrue(stepsOfType(roadmapId, "ARTICLE_UPDATE").get(0).isCompleted());
        assertEquals(before + 60, scoreService.getSummary(userId).getTotalScore());
        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.submitArticleUpdate(userId, step.getId(), good, sampleDocument()), "이미 끝난 단계");
        assertEquals(0, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()), "방금 업데이트했으니 다음 주기까지 없다");
    }

    @Test
    void 글_업데이트_제출은_다른_종류의_단계에는_쓸_수_없다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto entry = firstEntrySkillStep(roadmapId);
        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.submitArticleUpdate(userId, entry.getId(), "글", sampleDocument()));
        assertThrows(IllegalArgumentException.class, () -> roadmapService.completeUpkeep(userId, entry.getId(), "가".repeat(30)));
    }

    private Long newTrend(String name, double relevance) throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            Long id;
            try (PreparedStatement p = conn.prepareStatement(
                    "INSERT INTO TREND_TECH (tech_name, summary, source_url, published_at) VALUES (?, ?, ?, NOW())",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, name);
                p.setString(2, name + "은(는) 요즘 뜨는 기술입니다.");
                p.setString(3, "https://example.com/" + name);
                p.executeUpdate();
                try (java.sql.ResultSet keys = p.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            }
            try (PreparedStatement p = conn.prepareStatement(
                    "INSERT INTO TREND_TECH_JOB (trend_tech_id, job_id, relevance_score) VALUES (?, ?, ?)")) {
                p.setLong(1, id);
                p.setLong(2, jobId);
                p.setDouble(3, relevance);
                p.executeUpdate();
            }
            trendTechIds.add(id);
            return id;
        }
    }

    @Test
    void 로드맵을_만든_지_30일이_지나면_목표_직무의_트렌딩_기술_학습이_하나씩_이어_붙는다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        sql("UPDATE USERS SET desired_job_id = ? WHERE id = ?", jobId, userId);
        String first = "트렌드가나다" + System.nanoTime();
        String known = "트렌드이미아는것" + System.nanoTime();
        newTrend(first, 9.9999);
        newTrend(known, 9.9998);
        UserSkillDto mine = new UserSkillDto();
        mine.setUserId(userId);
        mine.setRawInput(known);
        userSkillDao.insert(mine);

        assertEquals(0, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()), "만든 지 30일이 안 됐다");
        sql("UPDATE ROADMAP SET created_at = ? WHERE id = ?", daysAgo(40), roadmapId);
        assertEquals(1, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()));

        RoadmapStepDto step = stepsOfType(roadmapId, "TREND_STUDY").get(0);
        assertTrue(step.getReason().startsWith(RoadmapUpkeepService.TREND_REASON_PREFIX + first + ":"), step.getReason());
        assertEquals(0, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()), "열린 트렌딩 학습이 있으면 더 만들지 않는다");

        assertEquals(40, roadmapService.completeUpkeep(userId, step.getId(), "공식 튜토리얼을 따라 해 보고 장단점을 정리했습니다."));
        assertEquals(0, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()), "방금 만들었으니 30일 뒤에");

        // 30일 뒤: 이미 한 주제(first)와 이미 아는 기술(known)은 건너뛰고 새 주제만
        String third = "트렌드다라마" + System.nanoTime();
        newTrend(third, 9.9997);
        sql("UPDATE ROADMAP_STEP SET created_at = ? WHERE id = ?", daysAgo(40), step.getId());
        assertEquals(1, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()));
        RoadmapStepDto next = stepsOfType(roadmapId, "TREND_STUDY").stream().filter(st -> !st.isCompleted()).findFirst().orElseThrow();
        assertTrue(next.getReason().startsWith(RoadmapUpkeepService.TREND_REASON_PREFIX + third + ":"), next.getReason());
        assertEquals(40, roadmapService.completeUpkeep(userId, next.getId(), "다음 주제도 새로 배웠고 점수는 감쇠 없이 40점이다."),
                "트렌딩 학습은 매번 새 주제라 감쇠하지 않는다");
    }

    @Test
    void 목표_직무가_없으면_트렌딩_학습은_만들지_않는다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        newTrend("트렌드없음" + System.nanoTime(), 9.9999);
        sql("UPDATE ROADMAP SET created_at = ? WHERE id = ?", daysAgo(40), roadmapId);
        assertEquals(0, roadmapService.appendDueUpkeep(userId, LocalDateTime.now()));
    }

    @Test
    void 유지_성장_점수는_대상이_같을수록_줄고_최저_5점이다() {
        assertEquals(60, RoadmapUpkeepService.pointsFor("PROJECT_UPDATE", 0));
        assertEquals(50, RoadmapUpkeepService.pointsFor("PROJECT_UPDATE", 1));
        assertEquals(5, RoadmapUpkeepService.pointsFor("ARTICLE_UPDATE", 20));
        assertEquals(40, RoadmapUpkeepService.pointsFor("TREND_STUDY", 7));
        assertThrows(IllegalArgumentException.class, () -> RoadmapUpkeepService.pointsFor("SKILL", 0));
    }

    // SKILL 단계 학습 검증(2026-09-30 팀 결정, 규칙 기반) — ENTRY 공부노트 제출.
    @Test
    void ENTRY_SKILL_단계에_기준_미달_노트를_제출하면_NEEDS_REVISION이고_완료되지_않는다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto entrySkillStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier()))
                .findFirst().orElseThrow();

        SkillProofGrader.GradeResult result = roadmapService.submitSkillNote(userId, entrySkillStep.getId(),
                "너무 짧은 노트", sampleDocument());

        assertEquals(SkillProofGrader.NEEDS_REVISION, result.status());
        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(entrySkillStep.getId()))
                .findFirst().orElseThrow();
        assertFalse(updated.isCompleted());
        assertEquals(SkillProofGrader.NEEDS_REVISION, updated.getReviewStatus());
    }

    @Test
    void ENTRY_SKILL_단계에_기준을_채운_노트를_제출하면_PASSED이고_완료되며_점수가_적립된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto entrySkillStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier()))
                .findFirst().orElseThrow();
        SkillDto skill = skillDao.findById(entrySkillStep.getRelatedSkillId());
        String note = (skill.getSkillName() + " 학습 내용 정리. ").repeat(20) + "```\nSystem.out.println(1);\n```";

        SkillProofGrader.GradeResult result = roadmapService.submitSkillNote(userId, entrySkillStep.getId(),
                note, sampleDocument());

        assertEquals(SkillProofGrader.PASSED, result.status());
        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(entrySkillStep.getId()))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
        assertEquals("NOTE", updated.getProofType());
        assertEquals(skillPoints(entrySkillStep.getRelatedSkillId(), "ENTRY"), scoreService.getSummary(userId).getTotalScore());
    }

    @Test
    void 다른_사용자_id로_노트를_제출하면_예외가_발생한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto entrySkillStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier()))
                .findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.submitSkillNote(userId + 999_999L, entrySkillStep.getId(), "아무 내용",
                        sampleDocument()));
    }

    // SKILL 단계 학습 검증 — CORE/ADVANCED 프로젝트 등록/업그레이드. generate()는 이 픽스처(부족 기술
    // 2개)로는 CORE 티어에 SKILL 단계를 만들지 않으므로, CORE 단계를 직접 삽입해서 검증한다.
    @Test
    void CORE_SKILL_단계를_프로젝트_등록으로_제출하면_evidence가_연결되고_완료된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        Long coreStepId;
        try (Connection conn = DBUtil.getConnection()) {
            RoadmapStepDto coreStep = new RoadmapStepDto();
            coreStep.setRoadmapId(roadmapId);
            coreStep.setStepOrder(999);
            coreStep.setStepType("SKILL");
            coreStep.setTier("CORE");
            coreStep.setRelatedSkillId(requiredSkillId);
            coreStep.setReason("테스트용 CORE 단계");
            coreStep.setCompleted(false);
            coreStepId = roadmapStepDao.insert(conn, coreStep);
        }

        boolean applied = roadmapService.submitSkillProjectStep(userId, coreStepId, sampleSubmission(), null);

        assertTrue(applied);
        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(coreStepId))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
        assertEquals("PROJECT_LINK", updated.getProofType());
        assertNotNull(updated.getEvidenceProjectId());
        assertEquals(skillPoints(requiredSkillId, "CORE"), scoreService.getSummary(userId).getTotalScore());
    }

    @Test
    void ENTRY_티어_단계를_프로젝트_등록으로_제출하면_예외가_발생한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto entrySkillStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier()))
                .findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.submitSkillProjectStep(userId, entrySkillStep.getId(), sampleSubmission(), null));
    }

    // "로드맵이 한 번 만들면 고정되는 문제" 해결(2026-09-30 팀 결정) — JOB.requirement_version 비교.
    @Test
    void isJobRequirementOutdated_직무_요구기술_버전이_바뀌면_true를_반환한다() throws Exception {
        roadmapService.generate(userId); // setUp에서 만든 GAP_ANALYSIS로 대표 로드맵 생성
        assertFalse(roadmapService.isJobRequirementOutdated(userId), "방금 만든 로드맵은 아직 최신이어야 한다");

        try (Connection conn = DBUtil.getConnection()) {
            jobDao.bumpRequirementVersion(conn, jobId);
        }
        try {
            assertTrue(roadmapService.isJobRequirementOutdated(userId));
        } finally {
            // BACKEND 직무는 팀 공용 시드라 반드시 원상복구한다.
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "UPDATE JOB SET requirement_version = requirement_version - 1 WHERE id = ?")) {
                pstmt.setLong(1, jobId);
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    void ADVANCED_단계는_업그레이드_없이_제출하면_예외가_발생한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        Long advancedStepId;
        try (Connection conn = DBUtil.getConnection()) {
            RoadmapStepDto advancedStep = new RoadmapStepDto();
            advancedStep.setRoadmapId(roadmapId);
            advancedStep.setStepOrder(998);
            advancedStep.setStepType("SKILL");
            advancedStep.setTier("ADVANCED");
            advancedStep.setRelatedSkillId(requiredSkillId);
            advancedStep.setReason("테스트용 ADVANCED 단계");
            advancedStep.setCompleted(false);
            advancedStepId = roadmapStepDao.insert(conn, advancedStep);
        }

        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.submitSkillProjectStep(userId, advancedStepId, sampleSubmission(), null));
    }

    @Test
    void ADVANCED_단계를_업그레이드로_제출하면_완료된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        UserProjectDto coreProject = sampleProject();
        coreProject.setUserId(userId);
        Long coreProjectId = userProjectDao.insert(coreProject);
        Long advancedStepId;
        try (Connection conn = DBUtil.getConnection()) {
            RoadmapStepDto advancedStep = new RoadmapStepDto();
            advancedStep.setRoadmapId(roadmapId);
            advancedStep.setStepOrder(997);
            advancedStep.setStepType("SKILL");
            advancedStep.setTier("ADVANCED");
            advancedStep.setRelatedSkillId(requiredSkillId);
            advancedStep.setReason("테스트용 ADVANCED 단계");
            advancedStep.setCompleted(false);
            advancedStepId = roadmapStepDao.insert(conn, advancedStep);
        }

        boolean applied = roadmapService.submitSkillProjectStep(userId, advancedStepId, sampleSubmission(), coreProjectId);

        assertTrue(applied);
        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(advancedStepId))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
    }

    // CERT 단계 학습 검증(2026-09-30 팀 결정, "완료 체크만 있던 걸 뒤늦게 발견해서 고침") — 증빙 서류
    // 첨부로만 완료할 수 있다.
    @Test
    void CERT_단계는_증빙_서류_없이_제출하면_예외가_발생한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto certStep = roadmapService.getSteps(roadmapId).get(0);
        assertEquals("CERT", certStep.getStepType());

        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.submitCertProof(userId, certStep.getId(), null));
    }

    @Test
    void CERT_단계에_증빙_서류를_첨부하면_완료되고_점수가_적립되며_USER_SPECS에_반영된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto certStep = roadmapService.getSteps(roadmapId).get(0);
        CertificationDto cert;
        try (Connection conn = DBUtil.getConnection()) {
            cert = certificationDao.findById(conn, certStep.getCertificationId());
        }

        boolean applied = roadmapService.submitCertProof(userId, certStep.getId(), sampleDocument());

        assertTrue(applied);
        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(certStep.getId()))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
        assertEquals("CERT_DOCUMENT", updated.getProofType());
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());

        boolean specAdded = userSpecDao.findByUserId(userId).stream()
                .anyMatch(s -> "CERT".equals(s.getSpecType()) && cert.getCertName().equals(s.getTitle()));
        assertTrue(specAdded, "USER_SPECS에 자격증이 자동 반영돼야 한다");
    }

    @Test
    void 다른_사용자_id로_CERT_증빙을_제출하면_예외가_발생한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto certStep = roadmapService.getSteps(roadmapId).get(0);

        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.submitCertProof(userId + 999_999L, certStep.getId(), sampleDocument()));
    }

    @Test
    void 이미_완료된_CERT_단계에_다시_제출하면_false를_반환한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto certStep = roadmapService.getSteps(roadmapId).get(0);

        assertTrue(roadmapService.submitCertProof(userId, certStep.getId(), sampleDocument()));
        assertFalse(roadmapService.submitCertProof(userId, certStep.getId(), sampleDocument()));
    }

    private UserProjectDto sampleProject() {
        UserProjectDto project = new UserProjectDto();
        project.setTitle("테스트 프로젝트");
        project.setDescription("로드맵 PROJECT 단계 완료 테스트용");
        project.setTechStack("Java, MySQL");
        return project;
    }

    private ProjectSubmission sampleSubmission() {
        ProjectSubmission submission = new ProjectSubmission(sampleProject());
        submission.getDocs().put("README", ProjectSubmission.DocSlot.submitted(sampleDocument()));
        submission.getDocs().put("SCREENSHOT", ProjectSubmission.DocSlot.submitted(sampleDocument()));
        return submission;
    }

    private DocumentDto sampleDocument() {
        DocumentDto document = new DocumentDto();
        document.setOriginalName("결과물.zip");
        document.setStoredName("test-" + System.nanoTime() + ".zip");
        document.setFilePath("/tmp/test-" + System.nanoTime() + ".zip");
        document.setFileSize(1024L);
        document.setMimeType("application/zip");
        document.setChecksum("dummy-checksum");
        return document;
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

    // 재분석 격차분석이 (버그·지연 등으로) 이미 배운 스킬을 여전히 MISSING으로 줘도, 로드맵 쪽에서
    // 한 번 더 방어한다 — 이전에 완료했던 스킬은 새 버전에서도 처음부터 완료 상태로 승계하고,
    // 점수는 다시 주지 않는다(팀 합의, 2026-09-23).
    @Test
    void 완료했던_스킬은_재생성해도_미완료로_돌아가지_않고_점수도_중복적립되지_않는다() throws Exception {
        Long firstRoadmapId = roadmapService.generate(userId);
        RoadmapStepDto preferredSkillStep = roadmapService.getSteps(firstRoadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && preferredSkillId.equals(s.getRelatedSkillId()))
                .findFirst().orElseThrow();
        roadmapService.completeStep(userId, preferredSkillStep.getId(), true);
        assertEquals(skillPoints(preferredSkillStep.getRelatedSkillId(), preferredSkillStep.getTier()), scoreService.getSummary(userId).getTotalScore());

        Long secondGapAnalysisId;
        try (Connection conn = DBUtil.getConnection()) {
            GapAnalysisDto secondAnalysis = new GapAnalysisDto();
            secondAnalysis.setUserId(userId);
            secondAnalysis.setJobId(jobId);
            secondAnalysis.setMatchRate(new BigDecimal("55.00"));
            secondAnalysis.setAnalyzedAt(LocalDateTime.now().plusMinutes(1));
            secondGapAnalysisId = gapAnalysisDao.insert(conn, secondAnalysis);

            // 이미 배운 preferredSkillId가 새 분석에도 여전히 MISSING으로 잡히는 최악의 경우를 흉내낸다.
            GapAnalysisItemDto item = new GapAnalysisItemDto();
            item.setGapAnalysisId(secondGapAnalysisId);
            item.setSkillId(preferredSkillId);
            item.setStatus("MISSING");
            gapAnalysisItemDao.insert(conn, item);
        }

        try {
            Long secondRoadmapId = roadmapService.generate(userId);
            RoadmapStepDto carriedStep = roadmapService.getSteps(secondRoadmapId).stream()
                    .filter(s -> "SKILL".equals(s.getStepType()) && preferredSkillId.equals(s.getRelatedSkillId()))
                    .findFirst().orElseThrow();

            assertTrue(carriedStep.isCompleted());
            assertNotNull(carriedStep.getCompletedAt());
            // 승계는 새로 완료한 게 아니므로 점수가 200으로 늘어나면 안 된다.
            assertEquals(skillPoints(preferredSkillStep.getRelatedSkillId(), preferredSkillStep.getTier()), scoreService.getSummary(userId).getTotalScore());
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

    @Test
    void 진행도는_ENTRY_티어_기준으로_계산되고_다_끝내야_ENTRY가_complete가_된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        List<RoadmapStepDto> steps = roadmapService.getSteps(roadmapId);
        List<RoadmapStepDto> entrySteps = steps.stream().filter(s -> "ENTRY".equals(s.getTier())).toList();
        assertFalse(entrySteps.isEmpty());

        RoadmapProgress before = roadmapService.computeProgress(steps);
        TierProgress entryBefore = before.getTier("ENTRY");
        assertEquals(entrySteps.size(), entryBefore.getTotal());
        assertEquals(0, entryBefore.getDone());
        assertFalse(entryBefore.isComplete());
        assertTrue(entryBefore.isUnlocked(), "ENTRY는 항상 열려 있어야 한다");
        assertEquals(entryBefore, before.getCurrentTier());

        for (RoadmapStepDto step : entrySteps) {
            roadmapService.completeStep(userId, step.getId(), true);
        }

        RoadmapProgress after = roadmapService.computeProgress(roadmapService.getSteps(roadmapId));
        TierProgress entryAfter = after.getTier("ENTRY");
        assertEquals(entrySteps.size(), entryAfter.getDone());
        assertEquals(100, entryAfter.getPercent());
        assertTrue(entryAfter.isComplete());
    }

    // ADVANCED/EXPERT 확장(2026-09-29): 부족 기술이 12개를 넘으면 ENTRY(5)/CORE(5)/ADVANCED(2)로
    // 나뉘고 EXPERT는 비어 있어야 하며, 앞 티어를 끝내야 그 다음 티어가 진행도상 "지금 할 일"로 풀려야 한다.
    @Test
    void 부족한_기술이_많아도_라운드_5개가_4개_티어를_모두_거치고_앞_티어를_끝내야_다음_티어가_풀린다() throws Exception {
        List<Long> extraSkillIds = new ArrayList<>();
        Long manyGapAnalysisId;
        try (Connection conn = DBUtil.getConnection()) {
            GapAnalysisDto analysis = new GapAnalysisDto();
            analysis.setUserId(userId);
            analysis.setJobId(jobId);
            analysis.setMatchRate(new BigDecimal("10.00"));
            analysis.setAnalyzedAt(LocalDateTime.now().plusMinutes(3));
            manyGapAnalysisId = gapAnalysisDao.insert(conn, analysis);

            for (int i = 0; i < 12; i++) {
                Long skillId = TestFixtures.insertSkill(conn, "초대량스킬" + i + "_" + System.nanoTime());
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

        try {
            Long roadmapId = roadmapService.generate(userId);
            List<RoadmapStepDto> steps = roadmapService.getSteps(roadmapId);

            long entryCount = steps.stream().filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier())).count();
            long coreCount = steps.stream().filter(s -> "SKILL".equals(s.getStepType()) && "CORE".equals(s.getTier())).count();
            long advancedCount = steps.stream().filter(s -> "SKILL".equals(s.getStepType()) && "ADVANCED".equals(s.getTier())).count();
            long expertCount = steps.stream().filter(s -> "SKILL".equals(s.getStepType()) && "EXPERT".equals(s.getTier())).count();
            // 기술별 사다리 — 부족 기술이 12개여도 한 라운드엔 상위 5개만 담기고, 그 5개가 4개 티어를 모두 거친다.
            assertEquals(5, entryCount);
            assertEquals(5, coreCount);
            assertEquals(5, advancedCount);
            assertEquals(5, expertCount);

            RoadmapProgress progress = roadmapService.computeProgress(steps);
            assertTrue(progress.getTier("ENTRY").isUnlocked());
            assertFalse(progress.getTier("CORE").isUnlocked(), "ENTRY를 안 끝냈으면 CORE는 아직 잠겨 있어야 한다");
            assertFalse(progress.getTier("ADVANCED").isUnlocked());
            assertFalse(progress.getTier("EXPERT").isEmptyTier(), "라운드의 기술은 전문가 티어까지 이어져야 한다");
            assertEquals("ENTRY", progress.getCurrentTier().getTier());
            assertEquals("CORE", progress.getNextLockedTier().getTier());

            for (RoadmapStepDto step : steps) {
                if ("ENTRY".equals(step.getTier())) {
                    roadmapService.completeStep(userId, step.getId(), true);
                }
            }
            RoadmapProgress afterEntry = roadmapService.computeProgress(roadmapService.getSteps(roadmapId));
            assertTrue(afterEntry.getTier("CORE").isUnlocked(), "ENTRY를 다 끝냈으면 CORE가 풀려야 한다");
            assertFalse(afterEntry.getTier("ADVANCED").isUnlocked());
            assertEquals("CORE", afterEntry.getCurrentTier().getTier());
            assertEquals("ADVANCED", afterEntry.getNextLockedTier().getTier());
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
                    // ENTRY 단계를 완료 체크했으므로 USER_SKILLS에 자동 등록됐다 — SKILL을 지우기 전에
                    // 그 참조부터 먼저 지워야 FK 제약(fk_user_skills_skill)에 걸리지 않는다.
                    TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "skill_id", skillId);
                    TestFixtures.hardDeleteByColumn(conn, "JOB_REQUIRED_SKILL", "skill_id", skillId);
                    TestFixtures.hardDelete(conn, "SKILL", skillId);
                }
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

    // 2026-10-06에 실제로 500이 났다: Duplicate entry for key 'roadmap.uk_roadmap_gap_analysis_id'.
    // 중복 확인은 LLM 호출 "전"에 하고 INSERT는 "후"에 하기 때문에, 그 수 초 사이에 들어온 두 번째
    // 요청이 같은 확인을 통과해 둘 다 INSERT하면 뒤쪽이 깨졌다. LLM이 불리는 동안 경쟁 로드맵을 넣어
    // 그 순간을 그대로 재현한다.
    @Test
    void LLM을_기다리는_동안_다른_요청이_로드맵을_만들어도_500이_아니라_그것을_쓴다() throws Exception {
        List<Long> plantedIds = new ArrayList<>();
        RoadmapService racing = new RoadmapService(new ProjectIdeaService(new LlmClient() {
            @Override
            public <T> T completeJson(String prompt, Class<T> type) {
                if (plantedIds.isEmpty()) {
                    try {
                        plantedIds.add(insertCompetingRoadmap()); // 먼저 들어온 요청이 만든 로드맵
                    } catch (java.sql.SQLException e) {
                        throw new IllegalStateException(e);
                    }
                }
                return type.cast(new ProjectIdeaService.ProjectIdea("테스트 프로젝트", "테스트용 고정 설명"));
            }
        }));

        Long result = racing.generate(userId);

        assertEquals(1, plantedIds.size(), "LLM이 불리지 않으면 이 테스트는 아무것도 검증하지 못한다");
        assertEquals(plantedIds.get(0), result, "먼저 만들어진 로드맵을 그대로 써야 한다");
        assertEquals(1, roadmapDao.findByUserId(userId).size(), "로드맵이 둘로 갈라지면 안 된다");
        assertTrue(roadmapDao.findByGapAnalysisId(gapAnalysisId).isPrimary(), "그 로드맵이 대표가 돼야 한다");
    }

    // 복구는 "그 UNIQUE 위반"일 때만 해야 한다 — 제약 이름을 문자열로 보고 가리므로, MySQL이 실제로
    // 그 이름을 담아 주는지 진짜 중복 INSERT를 해서 확인한다(버전이 올라가 메시지가 바뀌면 여기서 깨진다).
    @Test
    void 중복키_판별이_MySQL_실제_메시지와_맞는다() throws Exception {
        roadmapService.generate(userId);

        SQLIntegrityConstraintViolationException duplicate = assertThrows(
                SQLIntegrityConstraintViolationException.class, this::insertCompetingRoadmap);
        assertTrue(RoadmapGenerator.isDuplicateGapAnalysisKey(duplicate),
                "실제 메시지: " + duplicate.getMessage());

        assertFalse(RoadmapGenerator.isDuplicateGapAnalysisKey(
                        new SQLIntegrityConstraintViolationException("Cannot add or update a child row: fk_something")),
                "다른 제약 위반은 삼키면 안 된다");
    }

    /** 같은 격차 분석을 가리키는 로드맵을 직접 하나 넣는다 — 경쟁 요청 흉내 */
    private Long insertCompetingRoadmap() throws java.sql.SQLException {
        RoadmapDto competing = new RoadmapDto();
        competing.setUserId(userId);
        competing.setGapAnalysisId(gapAnalysisId);
        competing.setVersion(1);
        competing.setActive(true);
        competing.setPrimary(false); // 복구가 대표로 올려 주는지도 함께 확인한다
        competing.setTargetLevel("EXPERT");
        return roadmapDao.insert(competing);
    }

    // 부족한 기술이 5개(MAX_SKILL_STEPS_PER_TIER)를 넘으면, 넘는 만큼은 버리지 않고 tier=CORE로 남는다.
    @Test
    void 부족한_기술이_5개_넘으면_상위_5개만_라운드에_담기고_각_티어에_같은_5개가_이어진다() throws Exception {
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

            assertEquals(5, entrySkillCount, "ENTRY SKILL 단계는 라운드 기술 5개여야 한다");
            assertEquals(5, coreSkillCount, "CORE에도 같은 5개 기술이 이어져야 한다");
            // 같은 기술이 티어마다 한 번씩 — 입문 기술 집합과 핵심 기술 집합이 같다.
            assertEquals(
                    steps.stream().filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier()))
                            .map(RoadmapStepDto::getRelatedSkillId).collect(java.util.stream.Collectors.toSet()),
                    steps.stream().filter(s -> "SKILL".equals(s.getStepType()) && "CORE".equals(s.getTier()))
                            .map(RoadmapStepDto::getRelatedSkillId).collect(java.util.stream.Collectors.toSet()));

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

    // 기술 보충(2026-10-01) — 부족 기술이 5개 미만이면 직무 요구 기술로 5개를 채운다. LLM(Groq)이 고른 기술이
    // 우선이고, 후보에 없는 이름은 무시하며, LLM이 실패하면 중요도 순으로 채운다.
    @Test
    void 부족한_기술이_5개_미만이면_LLM이_고른_직무_요구_기술로_보충한다() throws Exception {
        Long extraSkillId;
        String extraSkillName = "보충후보스킬_" + System.nanoTime();
        try (Connection conn = DBUtil.getConnection()) {
            extraSkillId = TestFixtures.insertSkill(conn, extraSkillName);
            JobRequiredSkillDto req = new JobRequiredSkillDto();
            req.setJobId(jobId);
            req.setSkillId(extraSkillId);
            req.setImportance("PREFERRED");
            req.setSource("MANUAL");
            req.setEstimated(false);
            jobRequiredSkillDao.insert(conn, req);
        }
        try {
            RoadmapService withLlm = new RoadmapService(
                    new ProjectIdeaService(new StubLlmClient().register(ProjectIdeaService.ProjectIdea.class,
                            "{\"title\":\"t\",\"description\":\"d\"}")),
                    new SkillDeepenService(new StubLlmClient().register(SkillDeepenService.Picks.class,
                            "{\"skills\":[\"" + extraSkillName + "\",\"후보에 없는 기술\"]}")));
            Long roadmapId = withLlm.generate(userId);
            List<RoadmapStepDto> entrySkills = roadmapService.getSteps(roadmapId).stream()
                    .filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier())).toList();
            assertEquals(5, entrySkills.size(), "부족 기술 2개 + 보충 3개");
            assertTrue(entrySkills.stream().anyMatch(s -> extraSkillId.equals(s.getRelatedSkillId())),
                    "LLM이 고른 기술이 포함돼야 한다");
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                for (RoadmapDto roadmap : roadmapDao.findByUserId(userId)) {
                    for (RoadmapStepDto step : roadmapStepDao.findByRoadmapId(roadmap.getId())) {
                        TestFixtures.hardDelete(conn, "ROADMAP_STEP", step.getId());
                    }
                    TestFixtures.hardDelete(conn, "ROADMAP", roadmap.getId());
                }
                TestFixtures.hardDeleteByColumn(conn, "JOB_REQUIRED_SKILL", "skill_id", extraSkillId);
                TestFixtures.hardDelete(conn, "SKILL", extraSkillId);
            }
        }
    }

    // FR-111 재시도 버튼 (2026-10-06, E 추가) — 기본 문구로 대체된 프로젝트 단계만 다시 받고, 나머지 단계는 그대로 둔다
    @Test
    void 프로젝트_추천을_다시_시도하면_기본_문구였던_단계만_AI_추천으로_바뀐다() throws Exception {
        RoadmapService down = new RoadmapService(new ProjectIdeaService(StubLlmClient.failing(503)));
        Long roadmapId = down.generate(userId);
        List<RoadmapStepDto> before = roadmapService.getSteps(roadmapId);
        RoadmapStepDto project = before.stream().filter(s -> "PROJECT".equals(s.getStepType())).findFirst().orElseThrow();
        assertTrue(project.getReason().startsWith(RoadmapGenerator.PROJECT_FALLBACK_PREFIX), project.getReason());

        assertEquals(0, down.retryProjectIdea(userId), "또 실패하면 아무것도 바꾸지 않는다");
        assertEquals(1, roadmapService.retryProjectIdea(userId));

        List<RoadmapStepDto> after = roadmapService.getSteps(roadmapId);
        RoadmapStepDto retried = after.stream().filter(s -> s.getId().equals(project.getId())).findFirst().orElseThrow();
        assertFalse(retried.getReason().startsWith(RoadmapGenerator.PROJECT_FALLBACK_PREFIX), retried.getReason());
        assertEquals(before.size(), after.size(), "단계를 더하거나 빼지 않는다");
        for (RoadmapStepDto step : after) {
            if (!step.getId().equals(project.getId())) {
                RoadmapStepDto old = before.stream().filter(s -> s.getId().equals(step.getId())).findFirst().orElseThrow();
                assertEquals(old.getReason(), step.getReason(), "다른 단계의 문구는 그대로");
            }
        }
        assertEquals(0, roadmapService.retryProjectIdea(userId), "이미 AI 추천이 들어간 단계는 다시 부르지 않는다");
    }

    @Test
    void LLM이_실패해도_중요도_순으로_보충해_로드맵이_만들어진다() throws Exception {
        RoadmapService failingLlm = new RoadmapService(
                new ProjectIdeaService(StubLlmClient.failing(429)),
                new SkillDeepenService(StubLlmClient.failing(429)));
        Long roadmapId = failingLlm.generate(userId);
        long entrySkills = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && "ENTRY".equals(s.getTier())).count();
        assertEquals(5, entrySkills);
    }

    @Test
    void 핵심_단계를_완료하면_숙련도가_중급으로_오르고_전문가_단계는_고급이다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        List<RoadmapStepDto> steps = roadmapService.getSteps(roadmapId);
        Long skillId = requiredSkillId;
        RoadmapStepDto core = steps.stream().filter(s -> "CORE".equals(s.getTier())
                && "SKILL".equals(s.getStepType()) && skillId.equals(s.getRelatedSkillId())).findFirst().orElseThrow();
        RoadmapStepDto expert = steps.stream().filter(s -> "EXPERT".equals(s.getTier())
                && skillId.equals(s.getRelatedSkillId())).findFirst().orElseThrow();

        roadmapService.completeStep(userId, core.getId(), true);
        assertEquals("INTERMEDIATE", currentProficiency(skillId));
        roadmapService.completeStep(userId, expert.getId(), true);
        assertEquals("ADVANCED", currentProficiency(skillId));
    }

    @Test
    void 재생성하면_같은_기술의_같은_단계만_완료로_승계된다() throws Exception {
        Long firstRoadmapId = roadmapService.generate(userId);
        RoadmapStepDto entry = roadmapService.getSteps(firstRoadmapId).stream()
                .filter(s -> "ENTRY".equals(s.getTier()) && "SKILL".equals(s.getStepType())
                        && requiredSkillId.equals(s.getRelatedSkillId())).findFirst().orElseThrow();
        roadmapService.completeStep(userId, entry.getId(), true);

        Long secondGapAnalysisId;
        try (Connection conn = DBUtil.getConnection()) {
            GapAnalysisDto second = new GapAnalysisDto();
            second.setUserId(userId);
            second.setJobId(jobId);
            second.setMatchRate(new BigDecimal("50.00"));
            second.setAnalyzedAt(LocalDateTime.now().plusMinutes(1));
            secondGapAnalysisId = gapAnalysisDao.insert(conn, second);
            GapAnalysisItemDto item = new GapAnalysisItemDto();
            item.setGapAnalysisId(secondGapAnalysisId);
            item.setSkillId(requiredSkillId);
            item.setStatus("MISSING");
            gapAnalysisItemDao.insert(conn, item);
        }
        try {
            Long secondRoadmapId = roadmapService.generate(userId);
            List<RoadmapStepDto> steps = roadmapService.getSteps(secondRoadmapId).stream()
                    .filter(s -> "SKILL".equals(s.getStepType()) && requiredSkillId.equals(s.getRelatedSkillId()))
                    .toList();
            assertTrue(steps.stream().filter(s -> "ENTRY".equals(s.getTier())).findFirst().orElseThrow().isCompleted());
            assertFalse(steps.stream().filter(s -> "CORE".equals(s.getTier())).findFirst().orElseThrow().isCompleted(),
                    "입문만 끝낸 기술의 핵심 단계까지 완료로 승계하면 안 된다");
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

    @Test
    void 길_더_만들기는_아직_담지_않은_기술을_각_티어_맨_뒤에_이어_붙이고_끝낸_단계는_그대로_둔다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        // 입문 단계 하나를 끝내 둔다 — 이어 붙인 뒤에도 자리가 그대로여야 한다
        List<RoadmapStepDto> before = roadmapService.getSteps(roadmapId);
        RoadmapStepDto doneStep = before.stream()
                .filter(s -> RoadmapConstants.TIER_ENTRY.equals(s.getTier()) && "CERT".equals(s.getStepType()))
                .findFirst().orElseThrow();
        roadmapService.completeStep(userId, doneStep.getId(), true);
        int doneOrderBefore = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(doneStep.getId())).findFirst().orElseThrow().getStepOrder();

        // 로드맵에 아직 없는 부족 기술을 같은 분석에 하나 더 넣는다
        Long extraSkillId;
        Long extraRequirementId;
        Long extraItemId;
        try (Connection conn = DBUtil.getConnection()) {
            extraSkillId = TestFixtures.insertSkill(conn, "테스트추가스킬_" + System.nanoTime());
            JobRequiredSkillDto req = new JobRequiredSkillDto();
            req.setJobId(jobId);
            req.setSkillId(extraSkillId);
            req.setImportance("REQUIRED");
            req.setSource("MANUAL");
            req.setEstimated(false);
            extraRequirementId = jobRequiredSkillDao.insert(conn, req);

            GapAnalysisItemDto item = new GapAnalysisItemDto();
            item.setGapAnalysisId(gapAnalysisId);
            item.setSkillId(extraSkillId);
            item.setStatus("MISSING");
            extraItemId = gapAnalysisItemDao.insert(conn, item);
        }

        try {
            int added = roadmapService.appendNextRound(userId);
            assertTrue(added > 0, "아직 담지 않은 기술이 있으면 이어 붙인다");

            List<RoadmapStepDto> after = roadmapService.getSteps(roadmapId);
            assertEquals(doneOrderBefore, after.stream().filter(s -> s.getId().equals(doneStep.getId()))
                    .findFirst().orElseThrow().getStepOrder(), "끝낸 단계의 자리는 바뀌지 않는다");

            // 새 기술 단계는 자기 티어의 맨 뒤에 있다
            for (String tier : RoadmapConstants.SKILL_TIER_ORDER) {
                List<RoadmapStepDto> inTier = after.stream().filter(s -> tier.equals(s.getTier())).toList();
                if (inTier.isEmpty()) {
                    continue;
                }
                RoadmapStepDto last = inTier.get(inTier.size() - 1);
                assertEquals(extraSkillId, last.getRelatedSkillId(), tier + " 티어의 마지막이 새 기술이어야 한다");
            }

            // 같은 기술을 또 넣지는 않는다
            assertEquals(0, roadmapService.appendNextRound(userId), "더 넣을 기술이 없으면 0");
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "ROADMAP_STEP", "related_skill_id", extraSkillId);
                TestFixtures.hardDelete(conn, "GAP_ANALYSIS_ITEM", extraItemId);
                TestFixtures.hardDelete(conn, "JOB_REQUIRED_SKILL", extraRequirementId);
                TestFixtures.hardDelete(conn, "SKILL", extraSkillId);
            }
        }
    }
}
