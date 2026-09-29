package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.GapAnalysisItemDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
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
    private final RoadmapService roadmapService = new RoadmapService();
    private final ScoreService scoreService = new ScoreService();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final DocumentDao documentDao = new DocumentDao();

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
            TestFixtures.hardDeleteByColumn(conn, "SCORE_LOG", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SCORE_SUMMARY", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SPECS", "user_id", userId);
            // DOCUMENTS.project_id -> USER_PROJECTS FK가 RESTRICT라 문서를 먼저 지워야 한다.
            TestFixtures.hardDeleteByColumn(conn, "DOCUMENTS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
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
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());

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

        assertTrue(roadmapService.completeProjectStep(userId, projectStep.getId(), sampleProject(),
                List.of(sampleDocument())));
        // 재제출 — 호출부(서블릿)가 이 반환값으로 "방금 저장한 파일을 지워야 하는지" 판단한다.
        assertFalse(roadmapService.completeProjectStep(userId, projectStep.getId(), sampleProject(),
                List.of(sampleDocument())));
    }

    @Test
    void 다른_사용자_id로_PROJECT_단계를_제출하면_completeProjectStep이_false를_반환한다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        assertFalse(roadmapService.completeProjectStep(userId + 999_999L, projectStep.getId(), sampleProject(),
                List.of(sampleDocument())));
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
                step.setTier("CORE");
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
    void PROJECT_단계는_파일_없이는_완료할_수_없다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        UserProjectDto project = sampleProject();
        assertThrows(IllegalArgumentException.class,
                () -> roadmapService.completeProjectStep(userId, projectStep.getId(), project, List.of()));
    }

    @Test
    void PROJECT_단계를_파일과_함께_완료하면_프로젝트와_문서가_등록되고_점수도_적립된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        UserProjectDto project = sampleProject();
        roadmapService.completeProjectStep(userId, projectStep.getId(), project, List.of(sampleDocument()));

        RoadmapStepDto updated = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> s.getId().equals(projectStep.getId()))
                .findFirst().orElseThrow();
        assertTrue(updated.isCompleted());
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());

        List<UserProjectDto> projects = userProjectDao.findByUserId(userId);
        assertEquals(1, projects.size());
        assertEquals("테스트 프로젝트", projects.get(0).getTitle());

        List<DocumentDto> documents = documentDao.findByUserId(userId);
        assertEquals(1, documents.size());
        assertEquals(projects.get(0).getId(), documents.get(0).getProjectId());
    }

    @Test
    void 이미_완료된_PROJECT_단계를_다시_제출해도_프로젝트가_중복_생성되지_않는다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        RoadmapStepDto projectStep = roadmapService.getSteps(roadmapId).stream()
                .filter(s -> "PROJECT".equals(s.getStepType()))
                .findFirst().orElseThrow();

        roadmapService.completeProjectStep(userId, projectStep.getId(), sampleProject(), List.of(sampleDocument()));
        roadmapService.completeProjectStep(userId, projectStep.getId(), sampleProject(), List.of(sampleDocument()));

        assertEquals(1, userProjectDao.findByUserId(userId).size());
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());
    }

    private UserProjectDto sampleProject() {
        UserProjectDto project = new UserProjectDto();
        project.setTitle("테스트 프로젝트");
        project.setDescription("로드맵 PROJECT 단계 완료 테스트용");
        project.setTechStack("Java, MySQL");
        return project;
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
        assertEquals(100, scoreService.getSummary(userId).getTotalScore());

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
            assertEquals(100, scoreService.getSummary(userId).getTotalScore());
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
    void 진행도는_ENTRY_티어_기준으로_계산되고_다_끝내야_entryComplete가_된다() throws Exception {
        Long roadmapId = roadmapService.generate(userId);
        List<RoadmapStepDto> steps = roadmapService.getSteps(roadmapId);
        List<RoadmapStepDto> entrySteps = steps.stream().filter(s -> "ENTRY".equals(s.getTier())).toList();
        assertFalse(entrySteps.isEmpty());

        RoadmapService.RoadmapProgress before = roadmapService.computeProgress(steps);
        assertEquals(entrySteps.size(), before.getEntryTotal());
        assertEquals(0, before.getEntryDone());
        assertFalse(before.isEntryComplete());

        for (RoadmapStepDto step : entrySteps) {
            roadmapService.completeStep(userId, step.getId(), true);
        }

        RoadmapService.RoadmapProgress after = roadmapService.computeProgress(roadmapService.getSteps(roadmapId));
        assertEquals(entrySteps.size(), after.getEntryDone());
        assertEquals(100, after.getEntryPercent());
        assertTrue(after.isEntryComplete());
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
