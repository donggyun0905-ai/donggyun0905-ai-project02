package com.specodyssey.controller;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.PersonalInfo;
import com.specodyssey.service.UserService;
import com.specodyssey.util.DBUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 로그인·로그아웃 서블릿 — 실제 DB의 계정으로 요청 처리 결과(이동 경로, 세션, 오류 문구)를 확인한다. */
class LoginServletTest {

    private final LoginServlet servlet = new LoginServlet();
    private final List<Long> createdUsers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : createdUsers) {
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    private String newApplicant() throws Exception {
        String loginId = "test_login_" + System.nanoTime();
        Long id = new UserService().register(loginId, "Passw0rd!x", null,
                PersonalInfo.of("테스터", "25", "JOB_SEEKER", null), null, null);
        createdUsers.add(id);
        return loginId;
    }

    @Test
    void 올바른_로그인은_로드맵으로_보내고_세션에_해시_없는_사용자를_담는다() throws Exception {
        String loginId = newApplicant();
        FakeWeb.Request req = FakeWeb.request().post("/login").param("loginId", loginId).param("password", "Passw0rd!x");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals("/roadmap", resp.redirect);
        UserDto saved = (UserDto) req.session.attributes.get("loginUser");
        assertNotNull(saved);
        assertEquals(loginId, saved.getLoginId());
        assertNull(saved.getPasswordHash(), "세션에는 비밀번호 해시를 남기지 않는다");
        assertEquals(1, req.changeSessionIdCalls, "세션 고정 공격 방지: 로그인하면 세션 ID를 바꾼다");
    }

    @Test
    void 비밀번호가_틀리면_로그인_화면으로_돌아가고_세션에_사용자가_없다() throws Exception {
        String loginId = newApplicant();
        FakeWeb.Request req = FakeWeb.request().post("/login").param("loginId", loginId).param("password", "wrong-password");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals(List.of("/WEB-INF/views/login.jsp"), req.forwards);
        assertNotNull(req.attributes.get("errorMessage"));
        assertNull(resp.redirect);
        assertTrue(req.session == null || req.session.attributes.get("loginUser") == null);
    }

    @Test
    void 연속으로_틀리면_맞는_비밀번호도_잠시_막힌다() throws Exception {
        String loginId = newApplicant();
        for (int i = 0; i < 5; i++) {
            servlet.doPost(FakeWeb.request().post("/login").param("loginId", loginId).param("password", "nope" + i).http(),
                    FakeWeb.response().http());
        }
        FakeWeb.Request req = FakeWeb.request().post("/login").param("loginId", loginId).param("password", "Passw0rd!x");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertNull(resp.redirect, "잠긴 동안은 맞는 비밀번호여도 로그인되지 않는다");
        assertTrue(((String) req.attributes.get("errorMessage")).contains("잠시 막혔습니다"));
    }

    @Test
    void 이미_로그인한_채로_로그인_화면에_오면_홈으로_보낸다() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        FakeWeb.Request req = FakeWeb.request().loggedIn(user);
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doGet(req.http(), resp.http());
        assertEquals("/roadmap", resp.redirect);

        user.setUserType("INTERVIEWER");
        FakeWeb.Response resp2 = FakeWeb.response();
        servlet.doGet(req.http(), resp2.http());
        assertEquals(RoleFilter.INTERVIEWER_HOME, resp2.redirect);
    }

    @Test
    void 로그아웃하면_세션이_무효가_된다() throws Exception {
        UserDao unused = new UserDao();
        FakeWeb.Request req = FakeWeb.request().loggedIn(new UserDto());
        FakeWeb.Response resp = FakeWeb.response();

        new LogoutServlet().doGet(req.http(), resp.http());

        assertTrue(req.session.invalidated);
        assertNotNull(resp.redirect);
    }
}
