package com.specodyssey.controller;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
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

/** 회원가입 — 개인정보 동의·면접관 공개 안내를 서버에서도 강제하는지, 가입 결과가 DB에 맞게 남는지 확인한다. */
class RegisterServletTest {

    private final RegisterServlet servlet = new RegisterServlet();
    private final UserDao userDao = new UserDao();
    private final List<String> loginIds = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (String loginId : loginIds) {
                UserDto user = userDao.findByLoginId(loginId);
                if (user != null) {
                    TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION", "user_id", user.getId()); // 면접관 가입 때 만들어진다
                    TestFixtures.hardDelete(conn, "USERS", user.getId());
                }
            }
        }
    }

    private FakeWeb.Request applicant(String loginId) {
        loginIds.add(loginId);
        return FakeWeb.request().post("/register").param("userType", "APPLICANT").param("loginId", loginId)
                .param("password", "Passw0rd!x").param("name", "가입자").param("age", "24")
                .param("careerStatus", "JOB_SEEKER");
    }

    @Test
    void 개인정보_동의_없이는_가입되지_않는다() throws Exception {
        String loginId = "test_reg_" + System.nanoTime();
        FakeWeb.Request req = applicant(loginId).param("visibilityNotice", "Y");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals(List.of("/WEB-INF/views/signup.jsp"), req.forwards);
        assertTrue(((String) req.attributes.get("errorMessage")).contains("개인정보"));
        assertNull(userDao.findByLoginId(loginId));
    }

    @Test
    void 지원자는_면접관_공개_안내도_확인해야_가입된다() throws Exception {
        String loginId = "test_reg_" + System.nanoTime();
        FakeWeb.Request req = applicant(loginId).param("privacyConsent", "Y");

        servlet.doPost(req.http(), FakeWeb.response().http());

        assertTrue(((String) req.attributes.get("errorMessage")).contains("면접관"));
        assertNull(userDao.findByLoginId(loginId));
    }

    @Test
    void 둘_다_동의하면_가입되고_동의_시각이_기록되며_로그인_화면으로_보낸다() throws Exception {
        String loginId = "test_reg_" + System.nanoTime();
        FakeWeb.Request req = applicant(loginId).param("privacyConsent", "Y").param("visibilityNotice", "Y");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals("/login", resp.redirect);
        UserDto saved = userDao.findByLoginId(loginId);
        assertNotNull(saved);
        assertNotNull(saved.getPrivacyConsentAt());
        assertEquals("APPLICANT", saved.getUserType());
    }

    @Test
    void 면접관은_개인정보_동의만으로_가입된다() throws Exception {
        String loginId = "test_reg_iv_" + System.nanoTime();
        loginIds.add(loginId);
        FakeWeb.Request req = FakeWeb.request().post("/register").param("userType", "INTERVIEWER")
                .param("loginId", loginId).param("password", "Passw0rd!x").param("name", "면접관")
                .param("privacyConsent", "Y");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals("/login", resp.redirect);
        assertEquals("INTERVIEWER", userDao.findByLoginId(loginId).getUserType());
    }

    @Test
    void 이미_있는_아이디는_가입되지_않고_오류_문구를_보여준다() throws Exception {
        String loginId = "test_reg_" + System.nanoTime();
        servlet.doPost(applicant(loginId).param("privacyConsent", "Y").param("visibilityNotice", "Y").http(),
                FakeWeb.response().http());

        FakeWeb.Request again = applicant(loginId).param("privacyConsent", "Y").param("visibilityNotice", "Y");
        FakeWeb.Response resp = FakeWeb.response();
        servlet.doPost(again.http(), resp.http());

        assertNull(resp.redirect);
        assertEquals("이미 사용 중인 아이디입니다.", again.attributes.get("errorMessage"));
    }
}
