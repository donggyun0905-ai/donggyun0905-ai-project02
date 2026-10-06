package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GapAnalysisService 통합테스트. 실제 DB로 검증한다(.env가 가리키는 서버 그대로 — 팀 공용
 * 마스터 데이터인 SKILL/JOB_REQUIRED_SKILL은 건드리지 않고, 이 테스트가 만든 행만 정리한다).
 */
class GapAnalysisServiceTest {

    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisService gapAnalysisService = new GapAnalysisService();
    // 실제 Groq를 부르지 않는다 — RoadmapServiceTest와 같은 이유(2026-10-01, youngjun 제안).
    private final RoadmapService roadmapService = new RoadmapService(new ProjectIdeaService(
            new StubLlmClient().register(ProjectIdeaService.ProjectIdea.class,
                    "{\"title\":\"테스트 프로젝트\",\"description\":\"테스트용 고정 설명\"}")));

    private Long userId;
    private Long jobId;
    private Long metBySkillIdSkillId;
    private Long metByRawInputSkillId;
    private String metByRawInputSkillName;
    private Long missingSkillId;
    private String missingSkillName;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("gap_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        JobDto backendJob = jobDao.findAll().stream()
                .filter(j -> "BACKEND".equals(j.getJobCategory()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("시드 데이터에 BACKEND 직무가 없습니다"));
        jobId = backendJob.getId();

        metByRawInputSkillName = "GapTestRawMatch_" + System.nanoTime();
        missingSkillName = "GapTestMissing_" + System.nanoTime();

        try (Connection conn = DBUtil.getConnection()) {
            metBySkillIdSkillId = TestFixtures.insertSkill(conn, "GapTestSkillIdMatch_" + System.nanoTime());
            metByRawInputSkillId = TestFixtures.insertSkill(conn, metByRawInputSkillName);
            missingSkillId = TestFixtures.insertSkill(conn, missingSkillName);

            insertRequired(conn, metBySkillIdSkillId);
            insertRequired(conn, metByRawInputSkillId);
            insertRequired(conn, missingSkillId);

            // 1) skill_id로 이미 연결된 보유 스킬
            UserSkillDto owned1 = new UserSkillDto();
            owned1.setUserId(userId);
            owned1.setSkillId(metBySkillIdSkillId);
            owned1.setRawInput("아무 이름");
            userSkillDao.insert(conn, owned1);

            // 2) skill_id는 없지만(수동 입력) 이름이 대소문자만 다르게 일치하는 보유 스킬
            UserSkillDto owned2 = new UserSkillDto();
            owned2.setUserId(userId);
            owned2.setRawInput(metByRawInputSkillName.toUpperCase());
            userSkillDao.insert(conn, owned2);
        }
    }

    private Long insertRequired(Connection conn, Long skillId) throws Exception {
        JobRequiredSkillDto req = new JobRequiredSkillDto();
        req.setJobId(jobId);
        req.setSkillId(skillId);
        req.setImportance("REQUIRED");
        req.setSource("MANUAL");
        req.setEstimated(false);
        return jobRequiredSkillDao.insert(conn, req);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            // DAO 조회(findByUserId 등)는 is_deleted = FALSE만 돌려준다 — 재분석이 논리 삭제한 행을
            // 놓치면 그 행이 FK로 SKILL·USERS 하드 삭제를 막으므로, 전부 FK 컬럼으로 직접 지운다.
            for (Long roadmapId : TestFixtures.findIdsByColumn(conn, "ROADMAP", "user_id", userId)) {
                TestFixtures.hardDeleteByColumn(conn, "ROADMAP_STEP", "roadmap_id", roadmapId);
            }
            TestFixtures.hardDeleteByColumn(conn, "ROADMAP", "user_id", userId);
            for (Long analysisId : TestFixtures.findIdsByColumn(conn, "GAP_ANALYSIS", "user_id", userId)) {
                TestFixtures.hardDeleteByColumn(conn, "GAP_ANALYSIS_ITEM", "gap_analysis_id", analysisId);
            }
            TestFixtures.hardDeleteByColumn(conn, "GAP_ANALYSIS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "SCORE_LOG", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SCORE_SUMMARY", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
            // 이 테스트 기술을 팀 공용 BACKEND 직무의 요구 기술로 끼워 넣기 때문에, 그 사이에 분석을
            // 돌린 다른 테스트 사용자의 GAP_ANALYSIS_ITEM·ROADMAP_STEP도 이 기술을 물고 있을 수 있다.
            // 내가 만든 기술은 반드시 지워지도록 소유자와 상관없이 이 기술을 가리키는 행을 모두 끊는다.
            for (Long skillId : List.of(metBySkillIdSkillId, metByRawInputSkillId, missingSkillId)) {
                TestFixtures.hardDeleteByColumn(conn, "GAP_ANALYSIS_ITEM", "skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "ROADMAP_STEP", "related_skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "SKILL_ALIAS", "skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "JOB_REQUIRED_SKILL", "skill_id", skillId);
                TestFixtures.hardDelete(conn, "SKILL", skillId);
            }
            // SpecScoreScheduler는 웹앱이 뜨는 순간 전체 사용자에게 스냅샷을 남긴다 — USERS 바로 앞에서 지운다.
            TestFixtures.hardDeleteByColumn(conn, "SPEC_SCORE_HISTORY", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void skill_id_매칭과_이름_매칭_둘_다_MET로_판정되고_나머지는_MISSING이다() throws Exception {
        Long analysisId = gapAnalysisService.analyze(userId, jobId);
        assertNotNull(analysisId);

        // BACKEND 직무에는 팀 공용 시드가 이미 요구 기술을 여러 개 넣어놨다 — 내가 추가한 3개는
        // 그 위에 얹히는 것이니 전체 개수(=팀 시드 + 3)를 고정값으로 단정하지 않고, 내가 만든
        // 3개 skill_id의 판정만 확인한다.
        List<GapAnalysisItemDto> items = gapAnalysisService.getItems(analysisId);
        assertTrue(items.size() >= 3);

        Map<Long, String> bySkillId = items.stream()
                .collect(Collectors.toMap(GapAnalysisItemDto::getSkillId, GapAnalysisItemDto::getStatus));
        assertEquals("MET", bySkillId.get(metBySkillIdSkillId));
        assertEquals("MET", bySkillId.get(metByRawInputSkillId));
        assertEquals("MISSING", bySkillId.get(missingSkillId));

        GapAnalysisDto analysis = gapAnalysisDao.findById(analysisId);
        assertNotNull(analysis.getMatchRate());
        assertTrue(analysis.getMatchRate().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void getLatest는_가장_최근_분석을_돌려준다() throws Exception {
        gapAnalysisService.analyze(userId, jobId);
        // analyzed_at은 초 단위라 같은 초에 두 번 분석해도 GapAnalysisDao의 id DESC 2차 정렬로
        // 항상 더 나중 것이 먼저 온다 — sleep 없이도 안정적으로 검증된다.
        Long secondId = gapAnalysisService.analyze(userId, jobId);

        GapAnalysisDto latest = gapAnalysisService.getLatest(userId);
        assertEquals(secondId, latest.getId());
    }

    // "로드맵이 한 번 만들면 고정되는 문제" 해결(2026-09-30 팀 결정) — 분석 시점 JOB.requirement_version
    // 스냅샷이 잘 찍히는지 확인. 공용 시드(BACKEND)를 직접 건드리지 않고 현재 값을 읽어서만 비교한다.
    @Test
    void analyze는_JOB의_requirement_version을_스냅샷으로_저장한다() throws Exception {
        JobDto beforeAnalyze = jobDao.findById(jobId);

        Long analysisId = gapAnalysisService.analyze(userId, jobId);

        GapAnalysisDto analysis = gapAnalysisDao.findById(analysisId);
        assertEquals(beforeAnalyze.getRequirementVersion(), analysis.getJobRequirementVersion());
    }

    // FuzzyNameMatcher 도입(2026-09-30, "이름 일치라도") — raw_input에 사소한 오타가 있어도
    // MET로 잡히고, similarity_score(TD-1이 원래 비워뒀던 자리)가 채워지는지 확인.
    @Test
    void raw_input에_오타가_있어도_퍼지_매칭으로_MET_판정되고_similarity_score가_채워진다() throws Exception {
        // missingSkillId는 setUp에서 아무도 소유하지 않은 스킬이라, owned2(정확 일치)의 영향을
        // 받지 않고 순수하게 이 테스트의 오타 매칭만 검증할 수 있다.
        String typoName = missingSkillName.substring(0, missingSkillName.length() - 1) + "Z";
        try (Connection conn = DBUtil.getConnection()) {
            UserSkillDto typoOwned = new UserSkillDto();
            typoOwned.setUserId(userId);
            typoOwned.setRawInput(typoName);
            userSkillDao.insert(conn, typoOwned);
        }

        Long analysisId = gapAnalysisService.analyze(userId, jobId);
        List<GapAnalysisItemDto> items = gapAnalysisService.getItems(analysisId);
        GapAnalysisItemDto matchedItem = items.stream()
                .filter(i -> missingSkillId.equals(i.getSkillId()))
                .findFirst().orElseThrow();

        assertEquals("MET", matchedItem.getStatus());
        assertNotNull(matchedItem.getSimilarityScore());
        assertTrue(matchedItem.getSimilarityScore().doubleValue() < 1.0);
    }

    @Test
    void 분석_결과로_로드맵_생성까지_바로_이어진다() throws Exception {
        gapAnalysisService.analyze(userId, jobId);

        Long roadmapId = roadmapService.generate(userId);
        assertNotNull(roadmapId);
        assertTrue(roadmapService.getSteps(roadmapId).size() > 0);
    }
}
