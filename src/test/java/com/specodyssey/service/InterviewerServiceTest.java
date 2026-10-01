package com.specodyssey.service;

import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.dto.InterviewerCompareDto;
import com.specodyssey.dto.InterviewerCompareDto.Applicant;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InterviewerService 통합테스트. 실제 DB에 지원자·면접관·기술을 만들고 끝나면 지운다.
 */
class InterviewerServiceTest {

    private static final UserDao userDao = new UserDao();
    private static final ShareLinkService shareLinkService = new ShareLinkService();
    private final InterviewerService service = new InterviewerService();

    private static Long applicantId;
    private static Long interviewerId;
    private static Long sessionId;
    private static long ownedSkillId;
    private static long missingSkillId;
    private static String ownedSkillName;
    private static String missingSkillName;

    @BeforeAll
    static void setUp() throws Exception {
        long stamp = System.nanoTime();

        UserDto applicant = new UserDto();
        applicant.setUserType("APPLICANT");
        applicant.setLoginId("test_iv_applicant_" + stamp);
        applicant.setPasswordHash("dummy_hash");
        applicant.setName("홍길동");
        applicant.setMajor("컴퓨터공학과");
        applicant.setGrade("4학년");
        applicant.setDesiredJobStatus("UNSET");
        applicant.setPrivacyConsentAt(LocalDateTime.now());
        applicantId = userDao.insert(applicant);

        ownedSkillName = "test_iv_owned_" + stamp;
        missingSkillName = "test_iv_missing_" + stamp;
        try (Connection conn = DBUtil.getConnection()) {
            ownedSkillId = TestFixtures.insertSkill(conn, ownedSkillName);
            missingSkillId = TestFixtures.insertSkill(conn, missingSkillName);
        }
        UserSkillDto skill = new UserSkillDto();
        skill.setUserId(applicantId);
        skill.setSkillId(ownedSkillId);
        skill.setRawInput(ownedSkillName);
        new UserSkillDao().insert(skill);

        interviewerId = new UserService().registerInterviewer(
                "test_iv_interviewer_" + stamp, "password1234", null,
                PersonalInfo.nameOnly("김면접"), "  테스트 회사  ");
        sessionId = new InterviewerService().getOrCreateSession(interviewerId).getId();
    }

    @AfterEach
    void clearList() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "EVALUATION_CRITERIA", "session_id", sessionId);
            TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION_ITEM", "session_id", sessionId);
            for (ShareLinkDto link : new com.specodyssey.dao.ShareLinkDao().findByUserId(applicantId)) {
                TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK_VIEW_LOG", "share_link_id", link.getId());
            }
            TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", applicantId);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "EVALUATION_SESSION", sessionId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", applicantId);
            TestFixtures.hardDelete(conn, "SKILL", ownedSkillId);
            TestFixtures.hardDelete(conn, "SKILL", missingSkillId);
            TestFixtures.hardDelete(conn, "USERS", applicantId);
            TestFixtures.hardDelete(conn, "USERS", interviewerId);
        }
    }

    @Test
    void 면접관으로_가입하면_면접관_유형이고_회사명이_담긴_비교_목록이_하나_생긴다() throws Exception {
        assertEquals("INTERVIEWER", userDao.findById(interviewerId).getUserType());

        EvaluationSessionDto session = service.getOrCreateSession(interviewerId);
        assertEquals(interviewerId, session.getUserId());
        assertEquals("테스트 회사", session.getCompanyName());
        // 다시 불러도 같은 목록이다
        assertEquals(session.getId(), service.getOrCreateSession(interviewerId).getId());
    }

    @Test
    void 받은_링크_주소를_붙여_넣으면_담기고_공개한_이력이_보인다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(applicantId, "A사", 30, true, true, false);

        service.addLink(interviewerId, "http://localhost/spec-odyssey/share/" + link.getToken());

        List<Applicant> applicants = service.loadCompare(interviewerId).getApplicants();
        assertEquals(1, applicants.size());
        Applicant applicant = applicants.get(0);
        assertEquals("홍길동", applicant.getLabel()); // 기본 이력을 공개했으므로 이름이 보인다
        assertTrue(applicant.isAvailable());
        assertEquals(link.getToken(), applicant.getToken());
        assertEquals("컴퓨터공학과", applicant.getView().getMajor());
        assertEquals("없음", applicant.getCertText());
        assertNull(applicant.getGrowthText()); // 성장 잠재력은 공개하지 않은 링크
        // 목록·비교 화면에서 읽는 것은 열람 횟수에 넣지 않는다
        assertTrue(new ShareLinkViewLogDao().findByShareLinkId(link.getId()).isEmpty());
    }

    @Test
    void 열_수_없는_링크와_이미_담은_링크는_담기지_않는다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(applicantId, "A사", 30, true, false, false);
        service.addLink(interviewerId, link.getToken());

        assertThrows(IllegalArgumentException.class, () -> service.addLink(interviewerId, link.getToken()));
        assertThrows(IllegalArgumentException.class, () -> service.addLink(interviewerId, "no_such_token"));
        assertThrows(IllegalArgumentException.class, () -> service.addLink(interviewerId, "  "));

        assertEquals(1, service.loadCompare(interviewerId).getApplicants().size());
    }

    @Test
    void 목록에서_뺀_지원자는_다시_담을_수_있고_남의_목록_항목은_빼지_못한다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(applicantId, "A사", 30, true, false, false);
        service.addLink(interviewerId, link.getToken());
        Long itemId = service.loadCompare(interviewerId).getApplicants().get(0).getItemId();

        // 지원자 계정에는 이 항목이 없다 — 자기 목록(새로 만들어진 빈 목록)에서만 찾는다
        Long otherSessionId;
        service.removeItem(applicantId, itemId);
        otherSessionId = service.getOrCreateSession(applicantId).getId();
        try {
            assertEquals(1, service.loadCompare(interviewerId).getApplicants().size());

            service.removeItem(interviewerId, itemId);
            assertTrue(service.loadCompare(interviewerId).getApplicants().isEmpty());

            service.addLink(interviewerId, link.getToken());
            assertEquals(1, service.loadCompare(interviewerId).getApplicants().size());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", otherSessionId);
            }
        }
    }

    @Test
    void 요구_역량의_가중치로_적합도를_계산한다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(applicantId, "A사", 30, true, true, false);
        service.addLink(interviewerId, link.getToken());
        service.saveCriterion(interviewerId, ownedSkillName, 3);
        service.saveCriterion(interviewerId, missingSkillName, 1);

        InterviewerCompareDto compare = service.loadCompare(interviewerId);

        assertEquals(2, compare.getCriteria().size());
        assertEquals(4, compare.getTotalWeight());
        Applicant applicant = compare.getApplicants().get(0);
        int ownedIndex = ownedSkillName.equals(compare.getCriteria().get(0).getSkillName()) ? 0 : 1;
        assertTrue(applicant.getMatches().get(ownedIndex));
        assertFalse(applicant.getMatches().get(1 - ownedIndex));
        assertEquals(75, applicant.getFitScore()); // 3 ÷ 4

        // 같은 기술을 다시 넣으면 가중치만 바뀐다
        service.saveCriterion(interviewerId, missingSkillName, 3);
        compare = service.loadCompare(interviewerId);
        assertEquals(2, compare.getCriteria().size());
        assertEquals(50, compare.getApplicants().get(0).getFitScore()); // 3 ÷ 6

        // 지웠다가 다시 추가해도 된다
        Long missingCriterionId = compare.getCriteria().get(1 - ownedIndex).getId();
        service.removeCriterion(interviewerId, missingCriterionId);
        assertEquals(100, service.loadCompare(interviewerId).getApplicants().get(0).getFitScore());
        service.saveCriterion(interviewerId, missingSkillName, 1);
        assertEquals(75, service.loadCompare(interviewerId).getApplicants().get(0).getFitScore());
    }

    @Test
    void 마스터에_매칭되지_않은_기술도_입력한_이름이_같으면_갖춘_것으로_본다() throws Exception {
        // 프로필에서 기술을 추가하면 격차 분석 전까지 skill_id가 비어 있다 — 대소문자·공백이 달라도 이름으로 맞춘다
        UserSkillDto unmatched = new UserSkillDto();
        unmatched.setUserId(applicantId);
        unmatched.setRawInput("  " + missingSkillName.toUpperCase() + " ");
        Long unmatchedId = new UserSkillDao().insert(unmatched);
        try {
            ShareLinkDto link = shareLinkService.createLink(applicantId, "A사", 30, true, true, false);
            service.addLink(interviewerId, link.getToken());
            service.saveCriterion(interviewerId, missingSkillName, 2);

            Applicant applicant = service.loadCompare(interviewerId).getApplicants().get(0);

            assertTrue(applicant.getMatches().get(0));
            assertEquals(100, applicant.getFitScore());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "USER_SKILLS", unmatchedId);
            }
        }
    }

    @Test
    void 등록되지_않은_기술과_범위를_벗어난_가중치는_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> service.saveCriterion(interviewerId, "no_such_skill_" + System.nanoTime(), 3));
        assertThrows(IllegalArgumentException.class, () -> service.saveCriterion(interviewerId, ownedSkillName, 0));
        assertThrows(IllegalArgumentException.class, () -> service.saveCriterion(interviewerId, ownedSkillName, 6));
    }

    @Test
    void 기술_스택을_공개하지_않은_지원자는_적합도를_계산하지_않는다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(applicantId, "기본만", 30, true, false, false);
        service.addLink(interviewerId, link.getToken());
        service.saveCriterion(interviewerId, ownedSkillName, 3);

        Applicant applicant = service.loadCompare(interviewerId).getApplicants().get(0);

        assertNull(applicant.getFitScore());
        assertTrue(applicant.getMatches().isEmpty());

        // 기본 이력을 공개하지 않은 링크에서는 이름도 보이지 않는다
        ShareLinkDto skillsOnly = shareLinkService.createLink(applicantId, "기술만", 30, false, true, false);
        service.addLink(interviewerId, skillsOnly.getToken());
        assertEquals("지원자 2", service.loadCompare(interviewerId).getApplicants().get(1).getLabel());
    }

    @Test
    void 지원자가_공유를_멈추면_담아_둔_이력도_더_이상_보이지_않는다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(applicantId, "A사", 30, true, true, false);
        service.addLink(interviewerId, link.getToken());

        shareLinkService.setActive(applicantId, link.getId(), false);

        Applicant applicant = service.loadCompare(interviewerId).getApplicants().get(0);
        assertFalse(applicant.isAvailable());
        assertNull(applicant.getView());
        assertNull(applicant.getToken());
    }

    @Test
    void 프로필에서_이메일과_회사명을_바꿀_수_있다() throws Exception {
        service.updateProfile(interviewerId, " 박면접 ", " iv@example.com ", "새 회사");
        try {
            assertEquals("박면접", service.findUser(interviewerId).getName());
            assertEquals("iv@example.com", service.findUser(interviewerId).getEmail());
            assertEquals("새 회사", service.getOrCreateSession(interviewerId).getCompanyName());
            assertThrows(IllegalArgumentException.class,
                    () -> service.updateProfile(interviewerId, "박면접", null, "가".repeat(101)));
            assertThrows(IllegalArgumentException.class,
                    () -> service.updateProfile(interviewerId, " ", null, "새 회사"));
        } finally {
            service.updateProfile(interviewerId, "김면접", null, "테스트 회사");
        }
    }

    @Test
    void 주소_전체를_붙여_넣어도_토큰만_뽑아낸다() {
        assertEquals("abc_DEF-123", InterviewerService.extractToken("http://host/spec-odyssey/share/abc_DEF-123"));
        assertEquals("abc_DEF-123", InterviewerService.extractToken("  http://host/share/abc_DEF-123/?x=1#top "));
        assertEquals("abc_DEF-123", InterviewerService.extractToken("abc_DEF-123"));
        assertEquals("", InterviewerService.extractToken(null));
    }

    @Test
    void 요구_역량이_없으면_적합도를_계산하지_않는다() {
        assertNull(InterviewerService.fitScore(0, 0));
        assertEquals(67, InterviewerService.fitScore(2, 3));
        assertNotNull(InterviewerService.fitScore(0, 3));
    }
}
