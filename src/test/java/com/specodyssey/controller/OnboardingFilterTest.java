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
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 처음 설문을 하기 전에는 설문과 프로필만 열린다. */
class OnboardingFilterTest {

    private final OnboardingFilter filter = new OnboardingFilter();
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
        Long id = userService.register("test_ob_" + System.nanoTime(), "Passw0rd!x", null,
                PersonalInfo.of("온보딩", "25", "JOB_SEEKER", null), null, null);
        created.add(id);
        return userDao.findById(id);
    }

    private FakeWeb.Request req(UserDto user, String path) {
        FakeWeb.Request r = FakeWeb.request().loggedIn(user);
        r.servletPath = path;
        return r;
    }

    private boolean passes(FakeWeb.Request r, FakeWeb.Response resp) throws Exception {
        FakeWeb.Chain chain = new FakeWeb.Chain();
        filter.doFilter(r.http(), resp.http(), chain.chain());
        return chain.called();
    }

    @Test
    void 설문을_안_했으면_설문_프로필_계정_화면만_열리고_나머지는_설문으로_보낸다() throws Exception {
        UserDto user = newApplicant();
        for (String path : new String[] {"/job-discovery", "/profile", "/profile/skills", "/profile/password",
                "/profile/withdraw", "/logout", "/css/style.css"}) {
            assertTrue(passes(req(user, path), FakeWeb.response()), path);
        }
        for (String path : new String[] {"/roadmap", "/gap-analysis", "/dashboard", "/mission", "/documents",
                "/insights", "/dday", "/share-links", "/resume-feedback", "/roadmap-note"}) {
            FakeWeb.Request r = req(user, path);
            FakeWeb.Response resp = FakeWeb.response();
            assertFalse(passes(r, resp), path);
            assertEquals("/job-discovery", resp.redirect, path);
            assertNotNull(r.session.attributes.get(OnboardingFilter.NOTICE_KEY));
        }
    }

    @Test
    void 설문에_답했거나_목표_직무가_있으면_모두_열리고_세션에_기억한다() throws Exception {
        UserDto withJob = newApplicant();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement(
                     "UPDATE USERS SET desired_job_id = (SELECT id FROM JOB LIMIT 1) WHERE id = ?")) {
            p.setLong(1, withJob.getId());
            p.executeUpdate();
        }
        FakeWeb.Request r = req(withJob, "/roadmap");
        assertTrue(passes(r, FakeWeb.response()));
        assertEquals(Boolean.TRUE, r.session.attributes.get(OnboardingFilter.SESSION_KEY));

        UserDto answered = newApplicant();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement(
                     "INSERT INTO USER_SURVEY_ANSWER (user_id, question_id, answer_value, answered_at) " +
                     "SELECT ?, id, 3, NOW() FROM SURVEY_QUESTION WHERE survey_type = 'JOB_DISCOVERY' LIMIT 1")) {
            p.setLong(1, answered.getId());
            p.executeUpdate();
        }
        try {
            assertTrue(passes(req(answered, "/dashboard"), FakeWeb.response()));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "USER_SURVEY_ANSWER", "user_id", answered.getId());
            }
        }
    }

    @Test
    void 면접관과_관리자_계정은_설문_제한을_받지_않는다() throws Exception {
        UserDto interviewer = newApplicant();
        interviewer.setUserType("INTERVIEWER");
        assertTrue(passes(req(interviewer, "/interviewer/shared"), FakeWeb.response()));

        UserDto admin = newApplicant();
        admin.setLoginId("admin");
        assertTrue(passes(req(admin, "/admin"), FakeWeb.response()));
    }

    @Test
    void 로그인하지_않은_요청은_이_필터가_건드리지_않는다() throws Exception {
        FakeWeb.Request r = FakeWeb.request();
        r.servletPath = "/roadmap";
        assertTrue(passes(r, FakeWeb.response()));
    }
}
