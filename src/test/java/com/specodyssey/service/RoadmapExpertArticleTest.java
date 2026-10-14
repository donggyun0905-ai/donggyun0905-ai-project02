package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.TechArticleDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.TechArticleDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXPERT 단계 — 스펙 아카이브에 글을 쓰는 것이 증빙이다 (2026-10-14).
 *
 * 예전에는 PDF 를 받아 글자 수·기술명 횟수·링크로 판정했다. 본인 말고 아무도 못 봤고,
 * ERD·다이어그램을 아무리 넣어도 판정은 추출된 텍스트만 봤다. 지금은 아카이브에 바로 쓰고
 * 그 글이 공개된다 — "남에게 설명할 수 있다"가 실제로 확인된다.
 */
class RoadmapExpertArticleTest {

    private final UserDao userDao = new UserDao();
    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao stepDao = new RoadmapStepDao();
    private final TechArticleDao articleDao = new TechArticleDao();
    private final RoadmapSubmissionService service = new RoadmapSubmissionService();

    private Long userId;
    private Long gapAnalysisId;
    private Long roadmapId;
    private Long skillId;
    private Long expertStepId;
    private Long entryStepId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_expert_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        GapAnalysisDto analysis = new GapAnalysisDto();
        analysis.setUserId(userId);
        analysis.setJobId(new JobDao().findAll().get(0).getId());
        analysis.setMatchRate(new BigDecimal("30.00"));
        analysis.setAnalyzedAt(LocalDateTime.now());

        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, "테스트기술_전문가글_" + System.nanoTime());
            gapAnalysisId = gapAnalysisDao.insert(conn, analysis);

            RoadmapDto roadmap = new RoadmapDto();
            roadmap.setUserId(userId);
            roadmap.setGapAnalysisId(gapAnalysisId);
            roadmap.setVersion(1);
            roadmap.setActive(true);
            roadmap.setPrimary(true);
            roadmapId = roadmapDao.insert(conn, roadmap);

            expertStepId = stepDao.insert(conn, step(1, "EXPERT"));
            entryStepId = stepDao.insert(conn, step(2, "ENTRY"));
        }
    }

    private RoadmapStepDto step(int order, String tier) {
        RoadmapStepDto s = new RoadmapStepDto();
        s.setRoadmapId(roadmapId);
        s.setStepOrder(order);
        s.setStepType("SKILL");
        s.setTier(tier);
        s.setRelatedSkillId(skillId);
        s.setReason("테스트");
        s.setCompleted(false);
        return s;
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "TECH_ARTICLE", "user_id", userId);
            for (Long id : List.of(expertStepId, entryStepId)) {
                TestFixtures.hardDelete(conn, "ROADMAP_STEP", id);
            }
            TestFixtures.hardDeleteByColumn(conn, "SCORE_LOG", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
            TestFixtures.hardDelete(conn, "ROADMAP", roadmapId);
            TestFixtures.hardDelete(conn, "GAP_ANALYSIS", gapAnalysisId);
            TestFixtures.hardDelete(conn, "USERS", userId);
            TestFixtures.hardDelete(conn, "SKILL", skillId);
        }
    }

    @Test
    @DisplayName("글을 올리면 그 글이 증빙이 되고 단계가 완료된다")
    void 글을_올리면_단계가_완료된다() throws Exception {
        Long articleId = service.submitExpertArticle(userId, expertStepId,
                "내가 배운 것", "짧아도 된다. 글자 수 기준은 두지 않는다.", Map.of());

        assertNotNull(articleId);
        try (Connection conn = DBUtil.getConnection()) {
            RoadmapStepDto step = stepDao.findById(conn, expertStepId);
            assertTrue(step.isCompleted(), "글을 쓰면 바로 완료 — 판정 기준이 없다");
            assertNotNull(step.getCompletedAt());
            assertEquals(SkillProofGrader.PASSED, step.getReviewStatus());
        }
        TechArticleDto article = articleDao.findArchiveById(articleId);
        assertNotNull(article, "아카이브 목록·상세에 보인다 — 전에는 PDF 라 아무도 못 봤다");
        assertEquals(TechArticleDao.SOURCE_ROADMAP_EXPERT, article.getSourceType());
        assertEquals(skillId, article.getSkillId(), "어떤 기술에 대한 글인지 남는다");
        assertEquals(expertStepId, article.getRoadmapStepId());
    }

    @Test
    @DisplayName("완료를 취소하고 다시 내면 글을 두 벌로 만들지 않는다")
    void 다시_내도_글은_하나다() throws Exception {
        Long first = service.submitExpertArticle(userId, expertStepId, "첫 글", "본문", Map.of());

        // 완료 취소 — roadmap_step_id 에 UNIQUE 가 걸려 있어 그냥 또 넣으면 INSERT 가 깨진다
        try (Connection conn = DBUtil.getConnection()) {
            stepDao.updateCompleted(conn, expertStepId, userId, false, null);
        }

        Long second = service.submitExpertArticle(userId, expertStepId, "다시 쓴 제목", "다른 본문", Map.of());
        assertEquals(first, second, "이미 쓴 글로 단계만 다시 완료한다");

        TechArticleDto article = articleDao.findArchiveById(first);
        assertEquals("첫 글", article.getTitle(), "쓴 글을 덮어쓰지 않는다 — 고치려면 아카이브에서 고친다");
    }

    @Test
    @DisplayName("남의 단계·전문가가 아닌 단계·이미 끝난 단계는 받지 않는다")
    void 아무_단계나_받지_않는다() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitExpertArticle(userId, entryStepId, "제목", "본문", Map.of()),
                "입문 단계는 공부노트 PDF 로 낸다");
        assertThrows(IllegalArgumentException.class,
                () -> service.submitExpertArticle(userId, -1L, "제목", "본문", Map.of()),
                "없는 단계");

        service.submitExpertArticle(userId, expertStepId, "제목", "본문", Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.submitExpertArticle(userId, expertStepId, "또", "또", Map.of()),
                "이미 완료된 단계");
    }

    @Test
    @DisplayName("전문가 단계는 PDF 로 낼 수 없다 — 예전 화면이나 직접 POST 를 막는다")
    void PDF_경로는_닫혔다() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitSkillNote(userId, expertStepId, "긴 글 ".repeat(400), null),
                "열어 두면 글 없이 전문가 단계가 완료된다");
    }

    @Test
    @DisplayName("글쓰기 화면이 이 단계로 들어온 게 맞는지 확인할 수 있다")
    void 글쓰기_화면이_단계를_확인한다() throws Exception {
        RoadmapSubmissionService.ExpertStep found = service.findExpertStepToWrite(userId, expertStepId);
        assertNotNull(found);
        assertEquals(skillId, found.skillId());
        assertTrue(found.skillName().startsWith("테스트기술_전문가글_"));

        assertNull(service.findExpertStepToWrite(userId, entryStepId), "입문 단계는 아니다");
        assertNull(service.findExpertStepToWrite(-1L, expertStepId), "남의 단계는 안 보인다");

        service.submitExpertArticle(userId, expertStepId, "제목", "본문", Map.of());
        assertNull(service.findExpertStepToWrite(userId, expertStepId), "이미 끝낸 단계는 안 보인다");
    }
}
