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
                {"/share", "/some-token"}, {"/password-reset", null}, {"/recovery-code", null}};
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

    private FakeWeb.Response runRole(UserDto who, String path, FakeWeb.Chain chain) throws Exception {
        FakeWeb.Request req = FakeWeb.request().loggedIn(who);
        req.servletPath = path;
        FakeWeb.Response resp = FakeWeb.response();
        roleFilter.doFilter(req.http(), resp.http(), chain.chain());
        return resp;
    }

    @Test
    void 관리자는_관리자_홈에서_다시_돌려보내지_않는다() throws Exception {
        // "/admin"은 "/admin/"으로 시작하지 않는다 — 이 경로를 빼먹으면 자기 자신으로 리다이렉트해
        // 브라우저가 "너무 여러 번 리디렉션되었습니다"를 띄운다(2026-10-07 사용자 제보).
        FakeWeb.Chain chain = new FakeWeb.Chain();
        FakeWeb.Response resp = runRole(user("ADMIN"), RoleFilter.ADMIN_HOME, chain);

        assertNull(resp.redirect, "관리자 홈에서는 리다이렉트가 없어야 한다");
        assertTrue(chain.called(), "화면이 그려져야 한다");
    }

    @Test
    void 관리자는_관리자_하위_경로를_모두_쓸_수_있다() throws Exception {
        for (String path : new String[] {"/admin", "/admin/users", "/admin/articles", "/admin/roadmap",
                "/admin/reference", "/admin/audit", "/admin/design", "/admin/tests", "/admin/job-skill-trend"}) {
            FakeWeb.Chain chain = new FakeWeb.Chain();
            FakeWeb.Response resp = runRole(user("ADMIN"), path, chain);
            assertNull(resp.redirect, path);
            assertTrue(chain.called(), path);
        }
    }

    @Test
    void 관리자가_지원자_화면에_가면_관리자_홈으로_한_번만_보낸다() throws Exception {
        FakeWeb.Chain chain = new FakeWeb.Chain();
        FakeWeb.Response resp = runRole(user("ADMIN"), "/roadmap", chain);

        assertEquals(RoleFilter.ADMIN_HOME, resp.redirect);
        assertFalse(chain.called());
        // 돌려보낸 곳에서는 더 이상 리다이렉트가 없어야 루프가 끝난다
        FakeWeb.Chain next = new FakeWeb.Chain();
        assertNull(runRole(user("ADMIN"), resp.redirect, next).redirect, "돌려보낸 경로에서 또 보내면 루프다");
    }

    @Test
    void 관리자가_아니면_관리자_홈과_하위_경로_모두_막는다() throws Exception {
        for (String path : new String[] {"/admin", "/admin/users", "/admin/audit"}) {
            FakeWeb.Chain applicant = new FakeWeb.Chain();
            assertEquals(RoleFilter.APPLICANT_HOME, runRole(user("APPLICANT"), path, applicant).redirect, path);
            assertFalse(applicant.called(), path);

            FakeWeb.Chain interviewer = new FakeWeb.Chain();
            assertEquals(RoleFilter.INTERVIEWER_HOME, runRole(user("INTERVIEWER"), path, interviewer).redirect, path);
            assertFalse(interviewer.called(), path);
        }
    }

    @Test
    void 관리자도_로그아웃과_정적_파일은_쓸_수_있다() throws Exception {
        for (String path : new String[] {"/logout", "/login", "/css/style.css", "/image/logo.png"}) {
            FakeWeb.Chain chain = new FakeWeb.Chain();
            assertNull(runRole(user("ADMIN"), path, chain).redirect, path);
            assertTrue(chain.called(), path);
        }
    }
}
