package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 좌우 고정 위젯 — 자소서 첨삭·프로필을 뺀 지원자 화면에만, 면접관·관리자에게는 붙지 않는다. */
class SideWidgetFilterTest {

    private final SideWidgetFilter filter = new SideWidgetFilter();

    private static UserDto user(String type, String loginId) {
        UserDto user = new UserDto();
        user.setId(-1L); // 없는 사용자 — 조회는 빈 결과로 끝난다
        user.setUserType(type);
        user.setLoginId(loginId);
        return user;
    }

    private FakeWeb.Request run(UserDto user, String method, String path) throws Exception {
        FakeWeb.Request req = user == null ? FakeWeb.request() : FakeWeb.request().loggedIn(user);
        req.method = method;
        req.servletPath = path;
        FakeWeb.Chain chain = new FakeWeb.Chain();
        filter.doFilter(req.http(), FakeWeb.response().http(), chain.chain());
        assertTrue(chain.called(), "필터는 항상 요청을 이어서 보낸다");
        return req;
    }

    @Test
    void 지원자의_일반_화면_요청에는_위젯이_켜진다() throws Exception {
        FakeWeb.Request req = run(user("APPLICANT", "someone"), "GET", "/dashboard");
        assertEquals(Boolean.TRUE, req.attributes.get("sideWidgets"));
        assertNotNull(req.attributes.get("recentDocuments"));
        assertNotNull(req.attributes.get("noteText"));
    }

    @Test
    void 오늘의_미션_화면은_미션_필터가_따로_채우므로_여기서는_미션을_다시_읽지_않는다() throws Exception {
        FakeWeb.Request req = run(user("APPLICANT", "someone"), "GET", "/mission");
        assertEquals(Boolean.TRUE, req.attributes.get("sideWidgets"));
        assertNull(req.attributes.get("dailyMissions"));
    }

    @Test
    void 면접관_관리자_비로그인_POST에는_위젯이_붙지_않는다() throws Exception {
        assertNull(run(user("INTERVIEWER", "iv"), "GET", "/dashboard").attributes.get("sideWidgets"));
        assertNull(run(user("ADMIN", "admin"), "GET", "/dashboard").attributes.get("sideWidgets"));
        assertNull(run(null, "GET", "/dashboard").attributes.get("sideWidgets"));
        assertNull(run(user("APPLICANT", "someone"), "POST", "/dashboard").attributes.get("sideWidgets"));
    }

    @Test
    void 자소서_첨삭과_프로필은_위젯_대상_경로가_아니다() {
        jakarta.servlet.annotation.WebFilter annotation = SideWidgetFilter.class.getAnnotation(jakarta.servlet.annotation.WebFilter.class);
        java.util.Set<String> patterns = java.util.Set.of(annotation.urlPatterns());
        assertFalse(patterns.contains("/resume-feedback"));
        assertFalse(patterns.stream().anyMatch(p -> p.startsWith("/profile")));
        assertTrue(patterns.containsAll(java.util.List.of("/dashboard", "/roadmap", "/mission", "/insights", "/spec-archive")));
    }
}
