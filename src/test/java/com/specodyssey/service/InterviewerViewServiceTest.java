package com.specodyssey.service;

import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InterviewerViewService 통합테스트. 관련 요구사항: FR-81 · 85, NFR-9
 */
class InterviewerViewServiceTest {

    private final UserDao userDao = new UserDao();
    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final ShareLinkViewLogDao shareLinkViewLogDao = new ShareLinkViewLogDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final InterviewerViewService interviewerViewService = new InterviewerViewService();

    private Long userId;
    private Long linkId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("interviewer_view_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setMajor("컴퓨터공학과");
        user.setGrade("4학년");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        token = "iv_test_token_" + System.nanoTime();
        ShareLinkDto link = new ShareLinkDto();
        link.setUserId(userId);
        link.setToken(token);
        link.setActive(true);
        link.setScopeBasic(true);
        link.setScopeSkills(true);
        link.setScopeGrowth(false);
        try (Connection conn = DBUtil.getConnection()) {
            linkId = shareLinkDao.insert(conn, link);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement deleteLogs = conn.prepareStatement(
                     "DELETE FROM SHARE_LINK_VIEW_LOG WHERE share_link_id IN " +
                             "(SELECT id FROM SHARE_LINK WHERE user_id = ?)")) {
            deleteLogs.setLong(1, userId);
            deleteLogs.executeUpdate();
            TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SPECS", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 존재하지_않는_토큰은_null을_돌려준다() throws Exception {
        InterviewerViewService.ViewResult result = interviewerViewService.loadView("없는토큰", "127.0.0.1");

        assertNull(result);
    }

    @Test
    void 유효한_토큰이면_열람_로그가_남는다() throws Exception {
        interviewerViewService.loadView(token, "127.0.0.1");

        assertEquals(1, shareLinkViewLogDao.findByShareLinkId(linkId).size());
        assertEquals("127.0.0.1", shareLinkViewLogDao.findByShareLinkId(linkId).get(0).getViewerIp());
    }

    @Test
    void scopeBasic이면_전공_자격증_프로젝트가_날짜순으로_합쳐진다() throws Exception {
        UserSpecDto cert = new UserSpecDto();
        cert.setUserId(userId);
        cert.setSpecType("CERT");
        cert.setTitle("정보처리기사");
        cert.setAcquiredDate(LocalDate.of(2026, 6, 1));
        userSpecDao.insert(cert);

        UserProjectDto project = new UserProjectDto();
        project.setUserId(userId);
        project.setTitle("백엔드 API 서버");
        project.setDescription("Spring Boot REST API");
        project.setTechStack("Java, Spring Boot");
        project.setStartDate(LocalDate.of(2026, 9, 9));
        project.setEndDate(LocalDate.of(2026, 9, 23));
        userProjectDao.insert(project);

        InterviewerViewService.ViewResult result = interviewerViewService.loadView(token, "127.0.0.1");

        List<InterviewerViewService.TimelineItem> timeline = result.getTimeline();
        assertEquals(3, timeline.size());
        assertEquals("전공", timeline.get(0).type());
        assertEquals("자격증", timeline.get(1).type());
        assertEquals("프로젝트", timeline.get(2).type());
        assertTrue(timeline.get(2).detail().contains("Java, Spring Boot"));
    }

    @Test
    void scopeSkills가_false면_기술_목록은_빈다() throws Exception {
        UserSkillDto skill = new UserSkillDto();
        skill.setUserId(userId);
        skill.setRawInput("Docker");
        userSkillDao.insert(skill);

        try (Connection conn = DBUtil.getConnection()) {
            ShareLinkDto link = shareLinkDao.findByToken(token);
            link.setScopeSkills(false);
            shareLinkDao.update(conn, link, userId);
        }

        InterviewerViewService.ViewResult result = interviewerViewService.loadView(token, "127.0.0.1");

        assertTrue(result.getSkills().isEmpty());
    }

    @Test
    void scopeSkills가_true면_수동입력_기술명도_그대로_보여준다() throws Exception {
        UserSkillDto skill = new UserSkillDto();
        skill.setUserId(userId);
        skill.setRawInput("Docker");
        skill.setProficiency("INTERMEDIATE");
        userSkillDao.insert(skill);

        InterviewerViewService.ViewResult result = interviewerViewService.loadView(token, "127.0.0.1");

        assertEquals(1, result.getSkills().size());
        assertEquals("Docker", result.getSkills().get(0).name());
        assertEquals("INTERMEDIATE", result.getSkills().get(0).proficiency());
    }
}
