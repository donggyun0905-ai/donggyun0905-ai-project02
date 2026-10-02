package com.specodyssey.controller;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 로드맵 서블릿의 요청 라우팅 — 체크만으로 완료하는 길이 막혀 있는지, 잘못된 요청은 어떻게 거절되는지. */
class RoadmapServletTest {

    private static UserDto user;
    private final RoadmapServlet servlet = new RoadmapServlet();

    @BeforeAll
    static void setUp() throws Exception {
        user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_rmsv_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        user.setId(new UserDao().insert(user));
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "USERS", user.getId());
        }
    }

    private FakeWeb.Request post(String action) {
        FakeWeb.Request req = FakeWeb.request().post("/roadmap").loggedIn(user).param("action", action);
        req.servletPath = "/roadmap";
        return req;
    }

    @Test
    void 체크만으로_단계를_완료하는_요청은_거절된다() throws Exception {
        FakeWeb.Request req = post("complete").param("stepId", "1").param("completed", "true");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals("이 단계는 증빙을 제출해야 완료할 수 있습니다.", req.attributes.get("errorMessage"));
        assertNull(resp.redirect, "완료 처리 뒤 이동이 아니라 오류가 보이는 화면으로 다시 그린다");
        assertEquals(List.of("/WEB-INF/views/roadmap.jsp"), req.forwards);
    }

    @Test
    void 완료_취소_요청은_받아들인다() throws Exception {
        FakeWeb.Request req = post("complete").param("stepId", "-1").param("completed", "false");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals("/roadmap", resp.redirect);
        assertNull(req.attributes.get("errorMessage"));
    }

    @Test
    void 알_수_없는_action과_숫자가_아닌_stepId는_400이다() throws Exception {
        FakeWeb.Response unknown = FakeWeb.response();
        servlet.doPost(post("deleteEverything").http(), unknown.http());
        assertEquals(400, unknown.errorStatus);

        for (String action : new String[] {"complete", "completeUpkeep", "completeReview"}) {
            FakeWeb.Response bad = FakeWeb.response();
            servlet.doPost(post(action).param("stepId", "abc").http(), bad.http());
            assertEquals(400, bad.errorStatus, action);
        }
    }

    @Test
    void 기록이_짧거나_남의_단계에_보낸_업데이트_요청은_오류_문구로_돌려보낸다() throws Exception {
        FakeWeb.Request shortNote = post("completeUpkeep").param("stepId", "1").param("note", "짧음");
        servlet.doPost(shortNote.http(), FakeWeb.response().http());
        assertNotNull(shortNote.attributes.get("errorMessage"));
        assertTrue(((String) shortNote.attributes.get("errorMessage")).contains("20자"));

        // 존재하지 않는(=남의) 단계에 충분히 긴 기록을 보내도 아무 일도 일어나지 않고 점수 안내도 없다
        FakeWeb.Request foreign = post("completeUpkeep").param("stepId", "-5").param("note", "가".repeat(30));
        FakeWeb.Response resp = FakeWeb.response();
        servlet.doPost(foreign.http(), resp.http());
        assertEquals("/roadmap", resp.redirect);
        assertNull(foreign.session.attributes.get("roadmapNotice"));
    }
}
