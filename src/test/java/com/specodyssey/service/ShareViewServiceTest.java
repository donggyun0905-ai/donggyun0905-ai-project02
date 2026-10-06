package com.specodyssey.service;

import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ShareViewService 통합테스트. 실제 DB에 지원자 한 명의 이력을 만들고 끝나면 지운다.
 */
class ShareViewServiceTest {

    private static final UserDao userDao = new UserDao();
    private static final ShareLinkService shareLinkService = new ShareLinkService();
    private final ShareViewService service = new ShareViewService();
    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_shareview_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setMajor("컴퓨터공학과");
        user.setGrade("4학년");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        UserProjectDto project = new UserProjectDto();
        project.setUserId(userId);
        project.setTitle("API 서버 프로젝트");
        project.setTechStack("Java, MySQL");
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setEndDate(LocalDate.of(2026, 9, 20));
        new UserProjectDao().insert(project);

        UserSpecDto cert = new UserSpecDto();
        cert.setUserId(userId);
        cert.setSpecType("CERT");
        cert.setTitle("정보처리기능사");
        cert.setAcquiredDate(LocalDate.of(2026, 6, 1));
        new UserSpecDao().insert(cert);

        UserSkillDto skill = new UserSkillDto();
        skill.setUserId(userId);
        skill.setRawInput("Spring Boot");
        skill.setProficiency("INTERMEDIATE");
        new UserSkillDao().insert(skill);

        // 숙련도를 고르지 않은 기술
        UserSkillDto noProficiency = new UserSkillDto();
        noProficiency.setUserId(userId);
        noProficiency.setRawInput("Docker");
        new UserSkillDao().insert(noProficiency);
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (ShareLinkDto link : new ShareLinkDao().findByUserId(userId)) {
                TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK_VIEW_LOG", "share_link_id", link.getId());
            }
            TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SPECS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
            // 공유 링크 열람은 알림을 남긴다 — NOTIFICATION이 USERS를 RESTRICT로 잡는다
            TestFixtures.hardDeleteByColumn(conn, "NOTIFICATION", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 전체_공개_링크는_이력을_시간순으로_보여주고_열람을_기록한다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(userId, "전체 공개", 30, true, true, true);

        ShareViewDto view = service.loadView(link.getToken(), "127.0.0.1", null);

        assertNotNull(view);
        assertEquals("컴퓨터공학과", view.getMajor());
        assertEquals("4학년", view.getGrade());
        assertEquals(2, view.getTimeline().size());
        assertEquals("자격증", view.getTimeline().get(0).getTypeLabel()); // 6월 자격증이 9월 프로젝트보다 먼저
        assertEquals("정보처리기능사", view.getTimeline().get(0).getTitle());
        assertEquals("프로젝트", view.getTimeline().get(1).getTypeLabel());
        assertEquals("2026-09-01 ~ 2026-09-20", view.getTimeline().get(1).getDateText());
        assertEquals("사용 기술: Java, MySQL", view.getTimeline().get(1).getDetail());
        assertEquals("Spring Boot · 중급", view.getSkills().get(0));
        assertEquals("Docker", view.getSkills().get(1)); // 숙련도가 없으면 기술명만
        assertEquals(1, new ShareLinkViewLogDao().findByShareLinkId(link.getId()).size());
    }

    @Test
    void 지원자_본인이_미리보기로_열면_열람_기록을_남기지_않는다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(userId, "미리보기", 30, true, false, false);

        assertNotNull(service.loadView(link.getToken(), "127.0.0.1", userId));

        assertTrue(new ShareLinkViewLogDao().findByShareLinkId(link.getId()).isEmpty());
    }

    @Test
    void 공개하지_않은_범위의_데이터는_채우지_않는다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(userId, "기술만", 30, false, true, false);

        ShareViewDto view = service.loadView(link.getToken(), "127.0.0.1", null);

        assertNull(view.getMajor());
        assertNull(view.getGrade());
        assertTrue(view.getTimeline().isEmpty());
        assertEquals(2, view.getSkills().size());
    }

    @Test
    void 없는_토큰과_공유를_중단한_링크는_열리지_않는다() throws Exception {
        assertNull(service.loadView("no_such_token_" + System.nanoTime(), "127.0.0.1", null));
        assertNull(service.loadView("", "127.0.0.1", null));
        assertNull(service.loadView(null, "127.0.0.1", null));

        ShareLinkDto link = shareLinkService.createLink(userId, "중단할 링크", 30, true, false, false);
        link.setActive(false);
        try (Connection conn = DBUtil.getConnection()) {
            shareLinkDao.update(conn, link, userId);
        }
        assertNull(service.loadView(link.getToken(), "127.0.0.1", null));
    }

    @Test
    void 만료된_링크는_열리지_않는다() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(userId, "만료될 링크", 7, true, false, false);
        link.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        try (Connection conn = DBUtil.getConnection()) {
            shareLinkDao.update(conn, link, userId);
        }
        assertNull(service.loadView(link.getToken(), "127.0.0.1", null));
    }
}
