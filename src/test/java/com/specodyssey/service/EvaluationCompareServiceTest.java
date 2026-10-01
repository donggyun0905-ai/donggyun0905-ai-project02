package com.specodyssey.service;

import com.specodyssey.dao.EvaluationCriteriaDao;
import com.specodyssey.dao.EvaluationSessionDao;
import com.specodyssey.dao.EvaluationSessionItemDao;
import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvaluationCompareService 통합테스트. 관련 요구사항: FR-82 · 83
 */
class EvaluationCompareServiceTest {

    private final UserDao userDao = new UserDao();
    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final SkillDao skillDao = new SkillDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final EvaluationSessionDao evaluationSessionDao = new EvaluationSessionDao();
    private final EvaluationSessionItemDao evaluationSessionItemDao = new EvaluationSessionItemDao();
    private final EvaluationCriteriaDao evaluationCriteriaDao = new EvaluationCriteriaDao();
    private final EvaluationCompareService service = new EvaluationCompareService();

    private Long userId;
    private Long linkId;
    private String token;
    private Long skillId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("eval_compare_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setMajor("컴퓨터공학과");
        user.setGrade("4학년");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        token = "eval_compare_link_" + System.nanoTime();
        ShareLinkDto link = new ShareLinkDto();
        link.setUserId(userId);
        link.setToken(token);
        link.setActive(true);
        link.setScopeSkills(true);
        try (Connection conn = DBUtil.getConnection()) {
            linkId = shareLinkDao.insert(conn, link);
            skillId = TestFixtures.insertSkill(conn, "EvalCompareTestSkill_" + System.nanoTime());
        }

        UserSkillDto ownedSkill = new UserSkillDto();
        ownedSkill.setUserId(userId);
        ownedSkill.setSkillId(skillId);
        ownedSkill.setRawInput(skillDao.findById(skillId).getSkillName());
        userSkillDao.insert(ownedSkill);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement deleteCriteria = conn.prepareStatement(
                     "DELETE FROM EVALUATION_CRITERIA WHERE skill_id = ?");
             PreparedStatement deleteItems = conn.prepareStatement(
                     "DELETE FROM EVALUATION_SESSION_ITEM WHERE share_link_id = ?")) {
            deleteCriteria.setLong(1, skillId);
            deleteCriteria.executeUpdate();
            deleteItems.setLong(1, linkId);
            deleteItems.executeUpdate();
            TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
            TestFixtures.hardDelete(conn, "SKILL", skillId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void getOrCreateSession_토큰이_없으면_새로_만든다() throws Exception {
        EvaluationSessionDto session = service.getOrCreateSession(null);

        assertNotNull(session.getId());
        assertNotNull(session.getSessionToken());
        try {
            assertNotNull(evaluationSessionDao.findByToken(session.getSessionToken()));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", session.getId());
            }
        }
    }

    @Test
    void getOrCreateSession_기존_토큰이_유효하면_그대로_재사용한다() throws Exception {
        EvaluationSessionDto first = service.getOrCreateSession(null);
        try {
            EvaluationSessionDto second = service.getOrCreateSession(first.getSessionToken());
            assertEquals(first.getId(), second.getId());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", first.getId());
            }
        }
    }

    @Test
    void addCandidate_전체_URL을_붙여넣어도_토큰을_추출해서_담는다() throws Exception {
        EvaluationSessionDto session = service.getOrCreateSession(null);
        try {
            service.addCandidate(session.getId(), "https://spec-odyssey.example/share/" + token);

            assertEquals(1, evaluationSessionItemDao.findBySessionId(session.getId()).size());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION_ITEM", "session_id", session.getId());
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", session.getId());
            }
        }
    }

    @Test
    void addCandidate_유효하지_않은_링크는_예외() throws Exception {
        EvaluationSessionDto session = service.getOrCreateSession(null);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> service.addCandidate(session.getId(), "존재하지않는토큰"));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", session.getId());
            }
        }
    }

    @Test
    void addCandidate_같은_링크를_두번_담아도_하나만_남는다() throws Exception {
        EvaluationSessionDto session = service.getOrCreateSession(null);
        try {
            service.addCandidate(session.getId(), token);
            service.addCandidate(session.getId(), token);

            assertEquals(1, evaluationSessionItemDao.findBySessionId(session.getId()).size());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION_ITEM", "session_id", session.getId());
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", session.getId());
            }
        }
    }

    @Test
    void addOrUpdateCriterion_등록되지_않은_기술명이면_예외() throws Exception {
        EvaluationSessionDto session = service.getOrCreateSession(null);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> service.addOrUpdateCriterion(session.getId(), session.getSessionToken(),
                            "존재하지않는기술명_xyz", 3));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", session.getId());
            }
        }
    }

    @Test
    void addOrUpdateCriterion_같은_기술을_다시_추가하면_가중치만_덮어쓴다() throws Exception {
        EvaluationSessionDto session = service.getOrCreateSession(null);
        try {
            SkillDto skill = skillDao.findById(skillId);
            service.addOrUpdateCriterion(session.getId(), session.getSessionToken(), skill.getSkillName(), 2);
            service.addOrUpdateCriterion(session.getId(), session.getSessionToken(), skill.getSkillName(), 5);

            assertEquals(1, evaluationCriteriaDao.findBySessionId(session.getId()).size());
            assertEquals(5, evaluationCriteriaDao.findBySessionId(session.getId()).get(0).getWeight());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_CRITERIA", "session_id", session.getId());
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", session.getId());
            }
        }
    }

    @Test
    void buildCompareView은_공개된_기술과_가중치로_적합도를_계산한다() throws Exception {
        EvaluationSessionDto session = service.getOrCreateSession(null);
        try {
            service.addCandidate(session.getId(), token);
            SkillDto skill = skillDao.findById(skillId);
            service.addOrUpdateCriterion(session.getId(), session.getSessionToken(), skill.getSkillName(), 4);

            EvaluationCompareService.CompareView view = service.buildCompareView(
                    session.getId(), session.getSessionToken());

            assertEquals(1, view.criteria().size());
            assertEquals(1, view.candidates().size());
            EvaluationCompareService.CandidateView candidate = view.candidates().get(0);
            assertEquals(List.of(true), candidate.hasSkillByCriterion());
            assertEquals(100, candidate.fitScorePercent());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_CRITERIA", "session_id", session.getId());
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION_ITEM", "session_id", session.getId());
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", session.getId());
            }
        }
    }

    @Test
    void 링크가_비활성화되면_비교표에서_빠진다() throws Exception {
        EvaluationSessionDto session = service.getOrCreateSession(null);
        try {
            service.addCandidate(session.getId(), token);
            shareLinkDao.updateActive(linkId, userId, false);

            EvaluationCompareService.CompareView view = service.buildCompareView(
                    session.getId(), session.getSessionToken());

            assertTrue(view.candidates().isEmpty());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION_ITEM", "session_id", session.getId());
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", session.getId());
            }
        }
    }
}
