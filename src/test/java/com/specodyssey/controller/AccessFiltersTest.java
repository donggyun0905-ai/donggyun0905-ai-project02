package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 로그인 필터(SessionFilter)와 계정 유형 필터(RoleFilter) — 어느 경로가 누구에게 열리는지. */
class AccessFiltersTest {

    private final SessionFilter sessionFilter = new SessionFilter();
    private final RoleFilter roleFilter = new RoleFilter();

    private static UserDto user(String type) {
        UserDto user = new UserDto();
        user.setId(-1L); // 없는 사용자 — 등급 조회는 0점 기준으로 처리된다
        user.setUserType(type);
        return user;
    }

    private FakeWeb.Response runSession(FakeWeb.Request req, FakeWeb.Chain chain) throws Exception {
        FakeWeb.Response resp = FakeWeb.response();
        sessionFilter.doFilter(req.http(), resp.http(), chain.chain());
        return resp;
    }

    @Test
    void 로그인_없이_보호된_경로에_가면_로그인_화면으로_보낸다() throws Exception {
        for (String path : new String[] {"/roadmap", "/dashboard", "/documents", "/profile", "/share-links"}) {
            FakeWeb.Request req = FakeWeb.request();
            req.servletPath = path;
            FakeWeb.Chain chain = new FakeWeb.Chain();
            FakeWeb.Response resp = runSession(req, chain);
            assertEquals("/login", resp.redirect, path);
            assertFalse(chain.called(), path);
        }
    }

    @Test
    void 공개_경로는_로그인_없이_열린다() throws Exception {
        String[][] cases = {{"/login", null}, {"/register", null}, {"/css/style.css", null}, {"/image/logo.png", null},
                {"/share", "/some-token"}};
        for (String[] c : cases) {
            FakeWeb.Request req = FakeWeb.request();
            req.servletPath = c[0];
            req.pathInfo = c[1];
            FakeWeb.Chain chain = new FakeWeb.Chain();
            FakeWeb.Response resp = runSession(req, chain);
            assertTrue(chain.called(), c[0]);
            assertNull(resp.redirect, c[0]);
        }
    }

    @Test
    void 로그인한_사용자는_통과하고_등급_정보가_요청에_실린다() throws Exception {
        FakeWeb.Request req = FakeWeb.request().loggedIn(user("APPLICANT"));
        req.servletPath = "/roadmap";
        FakeWeb.Chain chain = new FakeWeb.Chain();
        FakeWeb.Response resp = runSession(req, chain);
        assertTrue(chain.called());
        assertNull(resp.redirect);
        assertEquals(0, req.attributes.get("totalScore"));
    }

    private FakeWeb.Response runRole(String type, String path, String pathInfo) throws Exception {
        FakeWeb.Request req = FakeWeb.request().loggedIn(user(type));
        req.servletPath = path;
        req.pathInfo = pathInfo;
        FakeWeb.Response resp = FakeWeb.response();
        roleFilter.doFilter(req.http(), resp.http(), new FakeWeb.Chain().chain());
        return resp;
    }

    @Test
    void 면접관은_지원자_화면에_못_가고_면접관_화면으로_돌아간다() throws Exception {
        for (String path : new String[] {"/roadmap", "/dashboard", "/documents", "/profile", "/mission", "/share-links"}) {
            assertEquals(RoleFilter.INTERVIEWER_HOME, runRole("INTERVIEWER", path, null).redirect, path);
        }
    }

    @Test
    void 면접관은_자기_화면과_공유_링크_로그아웃_정적_파일은_쓴다() throws Exception {
        for (String[] c : new String[][] {{"/interviewer", "/shared"}, {"/interviewer", "/compare"}, {"/share", "/tok"},
                {"/logout", null}, {"/css/style.css", null}}) {
            assertNull(runRole("INTERVIEWER", c[0], c[1]).redirect, c[0]);
        }
    }

    @Test
    void 지원자는_면접관_화면에_못_간다() throws Exception {
        assertEquals(RoleFilter.APPLICANT_HOME, runRole("APPLICANT", "/interviewer", "/shared").redirect);
        assertNull(runRole("APPLICANT", "/roadmap", null).redirect);
    }
}
