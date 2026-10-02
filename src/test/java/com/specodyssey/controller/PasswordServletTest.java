package com.specodyssey.controller;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.PersonalInfo;
import com.specodyssey.service.UserService;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.PasswordUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 비밀번호 변경 — 서비스 규칙과 서블릿(이동 경로, 세션 문구, 연속 실패 잠금). */
class PasswordServletTest {

    private static final String OLD = "OldPassw0rd!";
    private final PasswordServlet servlet = new PasswordServlet();
    private final UserService userService = new UserService();
    private final UserDao userDao = new UserDao();
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : created) {
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION", "user_id", id);
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    private UserDto newApplicant() throws Exception {
        String loginId = "test_pw_" + System.nanoTime();
        Long id = userService.register(loginId, OLD, null, PersonalInfo.of("비번테스터", "25", "JOB_SEEKER", null), null, null);
        created.add(id);
        UserDto user = userDao.findById(id);
        user.setPasswordHash(null);
        return user;
    }

    private FakeWeb.Request request(UserDto user, String path, String current, String next, String confirm) {
        FakeWeb.Request req = FakeWeb.request().post(path).loggedIn(user);
        req.param("currentPassword", current).param("newPassword", next).param("newPasswordConfirm", confirm);
        return req;
    }

    @Test
    void 현재_비밀번호가_맞고_새_비밀번호가_규칙에_맞으면_바뀌고_프로필로_돌아간다() throws Exception {
        UserDto user = newApplicant();
        FakeWeb.Request req = request(user, "/profile/password", OLD, "NewPassw0rd!", "NewPassw0rd!");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals("/profile", resp.redirect);
        assertNotNull(req.session.attributes.get(PasswordServlet.MESSAGE_KEY));
        assertNull(req.session.attributes.get(PasswordServlet.ERROR_KEY));
        assertEquals(1, req.changeSessionIdCalls);
        String stored = userDao.findById(user.getId()).getPasswordHash();
        assertTrue(PasswordUtil.verify("NewPassw0rd!", stored));
        assertFalse(PasswordUtil.verify(OLD, stored), "예전 비밀번호로는 더 이상 로그인되지 않는다");
        assertNotNull(userService.login(user.getLoginId(), "NewPassw0rd!"));
    }

    @Test
    void 면접관은_면접관_프로필로_돌아간다() throws Exception {
        UserDto user = newApplicant();
        FakeWeb.Request req = request(user, "/interviewer/password", OLD, "NewPassw0rd!", "NewPassw0rd!");
        req.servletPath = "/interviewer/password";
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals("/interviewer/profile", resp.redirect);
    }

    @Test
    void 현재_비밀번호가_틀리면_바뀌지_않고_이유를_알려준다() throws Exception {
        UserDto user = newApplicant();
        FakeWeb.Request req = request(user, "/profile/password", "틀린비밀번호!!", "NewPassw0rd!", "NewPassw0rd!");

        servlet.doPost(req.http(), FakeWeb.response().http());

        assertEquals("현재 비밀번호가 올바르지 않습니다.", req.session.attributes.get(PasswordServlet.ERROR_KEY));
        assertTrue(PasswordUtil.verify(OLD, userDao.findById(user.getId()).getPasswordHash()));
    }

    @Test
    void 새_비밀번호가_규칙에_안_맞거나_확인과_다르거나_현재와_같으면_바뀌지_않는다() throws Exception {
        UserDto user = newApplicant();
        String[][] cases = {
                {OLD, "short", "short", "8~100자"},
                {OLD, "NewPassw0rd!", "Different0ne!", "서로 다릅니다"},
                {OLD, OLD, OLD, "달라야"},
                {OLD, "x".repeat(101), "x".repeat(101), "8~100자"},
        };
        for (String[] c : cases) {
            FakeWeb.Request req = request(user, "/profile/password", c[0], c[1], c[2]);
            servlet.doPost(req.http(), FakeWeb.response().http());
            String error = (String) req.session.attributes.get(PasswordServlet.ERROR_KEY);
            assertNotNull(error, c[3]);
            assertTrue(error.contains(c[3]), error);
        }
        assertTrue(PasswordUtil.verify(OLD, userDao.findById(user.getId()).getPasswordHash()));
    }

    @Test
    void 현재_비밀번호를_연달아_틀리면_맞는_비밀번호도_잠시_막힌다() throws Exception {
        UserDto user = newApplicant();
        for (int i = 0; i < 5; i++) {
            servlet.doPost(request(user, "/profile/password", "틀림" + i + "!!!!!", "NewPassw0rd!", "NewPassw0rd!").http(),
                    FakeWeb.response().http());
        }
        FakeWeb.Request req = request(user, "/profile/password", OLD, "NewPassw0rd!", "NewPassw0rd!");
        servlet.doPost(req.http(), FakeWeb.response().http());

        assertTrue(((String) req.session.attributes.get(PasswordServlet.ERROR_KEY)).contains("잠시 막혔습니다"));
        assertTrue(PasswordUtil.verify(OLD, userDao.findById(user.getId()).getPasswordHash()), "잠긴 동안은 바뀌지 않는다");
    }

    @Test
    void 서비스는_없는_사용자와_빈_값을_거절한다() {
        assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.changePassword(-1L, OLD, "NewPassw0rd!"));
        assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.changePassword(-1L, null, "NewPassw0rd!"));
    }
}
