package com.specodyssey.controller;

import com.specodyssey.dao.ScoringRuleDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ScoringRuleAdminService;
import com.specodyssey.util.AdminAccess;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 관리자 화면 — 접근 제한, 규칙 저장/검증. */
class AdminServletTest {

    private final AdminServlet servlet = new AdminServlet();
    private final ScoringRuleDao dao = new ScoringRuleDao();

    @AfterEach
    void restore() throws Exception {
        dao.upsert("REVIEW_DAYS_ENTRY", 30);
        dao.upsert("LADDER_BUDGET", 2500);
        new ScoringRuleAdminService().save(Map.of("LADDER_BUDGET", "2500"));
    }

    private static UserDto user(String loginId) {
        UserDto u = new UserDto();
        u.setId(1L);
        u.setLoginId(loginId);
        u.setUserType("admin".equals(loginId) ? "ADMIN" : "APPLICANT");
        return u;
    }

    @Test
    void user_type이_ADMIN인_계정만_관리자다() {
        assertTrue(AdminAccess.isAdmin(user("admin")));
        assertFalse(AdminAccess.isAdmin(user("someone")));
        assertFalse(AdminAccess.isAdmin(null));
    }

    @Test
    void 관리자가_아니면_화면도_저장도_403이다() throws Exception {
        FakeWeb.Request get = FakeWeb.request().loggedIn(user("someone"));
        FakeWeb.Response getResp = FakeWeb.response();
        servlet.doGet(get.http(), getResp.http());
        assertEquals(403, getResp.errorStatus);
        assertTrue(get.forwards.isEmpty());

        FakeWeb.Request post = FakeWeb.request().post("/admin").loggedIn(user("someone"));
        post.param("rule_LADDER_BUDGET", "1");
        FakeWeb.Response postResp = FakeWeb.response();
        servlet.doPost(post.http(), postResp.http());
        assertEquals(403, postResp.errorStatus);
    }

    @Test
    void 관리자는_규칙_화면을_보고_값을_저장할_수_있다() throws Exception {
        FakeWeb.Request get = FakeWeb.request().loggedIn(user("admin"));
        servlet.doGet(get.http(), FakeWeb.response().http());
        assertEquals(List.of("/WEB-INF/views/admin.jsp"), get.forwards);
        assertNotNull(get.attributes.get("ruleGroups"));

        FakeWeb.Request post = FakeWeb.request().post("/admin").loggedIn(user("admin"));
        post.param("rule_REVIEW_DAYS_ENTRY", "45");
        FakeWeb.Response resp = FakeWeb.response();
        servlet.doPost(post.http(), resp.http());

        assertEquals("/admin", resp.redirect);
        assertEquals("1개 규칙을 저장했습니다.", post.session.attributes.get(AdminServlet.MESSAGE_KEY));
        assertEquals(45, dao.findAll().get("REVIEW_DAYS_ENTRY"));
    }

    @Test
    void 숫자가_아니거나_범위를_벗어나거나_모르는_규칙이면_하나도_저장하지_않는다() throws Exception {
        for (String[] bad : new String[][] {
                {"LADDER_BUDGET", "abc"}, {"LADDER_BUDGET", "0"}, {"LADDER_BUDGET", "-3"},
                {"LADDER_BUDGET", "100001"}, {"NOT_A_RULE", "5"}}) {
            FakeWeb.Request post = FakeWeb.request().post("/admin").loggedIn(user("admin"));
            post.param("rule_REVIEW_DAYS_ENTRY", "77").param("rule_" + bad[0], bad[1]);
            FakeWeb.Response resp = FakeWeb.response();
            servlet.doPost(post.http(), resp.http());
            assertEquals("/admin", resp.redirect);
            assertNotNull(post.session.attributes.get(AdminServlet.ERROR_KEY), bad[0] + "=" + bad[1]);
            assertEquals(30, dao.findAll().get("REVIEW_DAYS_ENTRY"), "잘못된 값이 섞이면 나머지도 저장하지 않는다");
        }
    }

    @Test
    void 감쇠와_보너스_규칙은_0을_허용한다() throws Exception {
        assertEquals(1, new ScoringRuleAdminService().save(Map.of("STREAK_BONUS_PER_DAY", "0")));
        new ScoringRuleAdminService().save(Map.of("STREAK_BONUS_PER_DAY", "2"));
    }
}
