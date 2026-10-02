package com.specodyssey.controller;

import com.specodyssey.util.CsrfToken;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    // ---- 출처 확인

    @Test
    void 같은_호스트에서_온_요청은_통과한다() {
        assertTrue(SecurityHeadersFilter.hostMatches("http://localhost:8080/roadmap", "localhost:8080"));
        assertTrue(SecurityHeadersFilter.hostMatches("https://spec.example.com/roadmap", "spec.example.com"));
        assertTrue(SecurityHeadersFilter.hostMatches("HTTP://Spec.Example.com/x", "spec.example.com"));
    }

    @Test
    void 다른_호스트나_포트에서_온_요청은_막는다() {
        assertFalse(SecurityHeadersFilter.hostMatches("http://evil.example.com/form", "localhost:8080"));
        assertFalse(SecurityHeadersFilter.hostMatches("http://localhost:9999/form", "localhost:8080"));
        assertFalse(SecurityHeadersFilter.hostMatches("http://localhost.evil.com/", "localhost"));
    }

    @Test
    void 이상한_출처나_Host_헤더가_없으면_막는다() {
        assertFalse(SecurityHeadersFilter.hostMatches("not a url", "localhost"));
        assertFalse(SecurityHeadersFilter.hostMatches("file:///etc/passwd", "localhost"));
        assertFalse(SecurityHeadersFilter.hostMatches("http://localhost/", null));
    }

    // ---- 필터 동작 (GET: 헤더·토큰 발급 / POST: 토큰 검증)

    @Test
    void GET은_보안_헤더를_붙이고_세션에_토큰을_만들어_JSP에_내려준다() throws Exception {
        FakeWeb.Request req = FakeWeb.request();
        req.servletPath = "/roadmap";
        FakeWeb.Response resp = FakeWeb.response();
        FakeWeb.Chain chain = new FakeWeb.Chain();

        filter.doFilter(req.http(), resp.http(), chain.chain());

        assertTrue(chain.called());
        assertEquals("nosniff", resp.headers.get("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", resp.headers.get("X-Frame-Options"));
        assertEquals("same-origin", resp.headers.get("Referrer-Policy"));
        assertEquals("UTF-8", req.characterEncoding, "폼 값을 읽기 전에 인코딩을 UTF-8로 맞춘다");
        String token = (String) req.attributes.get(CsrfToken.REQUEST_ATTRIBUTE);
        assertNotNull(token);
        assertEquals(token, req.session.attributes.get(CsrfToken.SESSION_ATTRIBUTE));
    }

    @Test
    void 정적_파일_GET은_세션을_만들지_않는다() throws Exception {
        for (String path : new String[] {"/css/style.css", "/image/logo.png", "/js/a.js", "/img/a.png"}) {
            FakeWeb.Request req = FakeWeb.request();
            req.servletPath = path;
            filter.doFilter(req.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
            assertNull(req.session, path);
            assertNull(req.attributes.get(CsrfToken.REQUEST_ATTRIBUTE), path);
        }
    }

    @Test
    void 같은_세션_안에서는_토큰이_바뀌지_않는다() throws Exception {
        FakeWeb.Request first = FakeWeb.request();
        first.servletPath = "/roadmap";
        filter.doFilter(first.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
        FakeWeb.Request second = FakeWeb.request();
        second.servletPath = "/dashboard";
        second.session = first.session;
        filter.doFilter(second.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
        assertEquals(first.attributes.get(CsrfToken.REQUEST_ATTRIBUTE), second.attributes.get(CsrfToken.REQUEST_ATTRIBUTE));
    }

    private FakeWeb.Request sessionWithToken(String path) {
        FakeWeb.Request req = FakeWeb.request().post(path);
        req.session = new FakeWeb.Session();
        req.session.attributes.put(CsrfToken.SESSION_ATTRIBUTE, "good-token");
        req.session.attributes.put("loginUser", "someone");
        return req;
    }

    @Test
    void POST는_폼의_토큰이_맞으면_통과하고_JSP에도_같은_토큰을_내려준다() throws Exception {
        FakeWeb.Request req = sessionWithToken("/documents").param("_csrf", "good-token");
        FakeWeb.Response resp = FakeWeb.response();
        FakeWeb.Chain chain = new FakeWeb.Chain();
        filter.doFilter(req.http(), resp.http(), chain.chain());
        assertTrue(chain.called());
        assertEquals(0, resp.errorStatus);
        assertEquals("good-token", req.attributes.get(CsrfToken.REQUEST_ATTRIBUTE), "실패 화면을 다시 그려도 폼이 토큰을 갖는다");
    }

    @Test
    void POST는_헤더의_토큰으로도_통과한다() throws Exception {
        FakeWeb.Request req = sessionWithToken("/roadmap-note").header("X-CSRF-Token", "good-token");
        FakeWeb.Chain chain = new FakeWeb.Chain();
        filter.doFilter(req.http(), FakeWeb.response().http(), chain.chain());
        assertTrue(chain.called());
    }

    @Test
    void 토큰이_없거나_틀리면_403이고_서블릿까지_가지_않는다() throws Exception {
        String[][] cases = {{}, {"wrong-token"}, {""}};
        for (String[] c : cases) {
            FakeWeb.Request req = sessionWithToken("/documents");
            if (c.length > 0) {
                req.param("_csrf", c[0]);
            }
            FakeWeb.Response resp = FakeWeb.response();
            FakeWeb.Chain chain = new FakeWeb.Chain();
            filter.doFilter(req.http(), resp.http(), chain.chain());
            assertFalse(chain.called());
            assertEquals(403, resp.errorStatus);
        }
    }

    @Test
    void 다른_사이트_출처의_POST는_토큰이_맞아도_403이다() throws Exception {
        FakeWeb.Request req = sessionWithToken("/documents").param("_csrf", "good-token")
                .header("Origin", "http://evil.example.com").header("Host", "localhost:8080");
        FakeWeb.Response resp = FakeWeb.response();
        FakeWeb.Chain chain = new FakeWeb.Chain();
        filter.doFilter(req.http(), resp.http(), chain.chain());
        assertFalse(chain.called());
        assertEquals(403, resp.errorStatus);
    }

    @Test
    void 세션이_없는_POST는_로그인과_가입만_막고_나머지는_SessionFilter에_맡긴다() throws Exception {
        for (String path : new String[] {"/login", "/register"}) {
            FakeWeb.Response resp = FakeWeb.response();
            FakeWeb.Chain chain = new FakeWeb.Chain();
            filter.doFilter(FakeWeb.request().post(path).param("_csrf", "x").http(), resp.http(), chain.chain());
            assertFalse(chain.called(), path);
            assertEquals(403, resp.errorStatus, path);
        }
        FakeWeb.Chain chain = new FakeWeb.Chain();
        FakeWeb.Response resp = FakeWeb.response();
        filter.doFilter(FakeWeb.request().post("/documents").http(), resp.http(), chain.chain());
        assertTrue(chain.called(), "로그인 필요한 경로는 SessionFilter가 로그인 화면으로 보낸다");
        assertEquals(0, resp.errorStatus);
    }

    @Test
    void 토큰_비교는_빈_값과_다른_값을_모두_거절한다() {
        assertTrue(CsrfToken.matches("abc", "abc"));
        assertFalse(CsrfToken.matches("abc", "abd"));
        assertFalse(CsrfToken.matches("abc", null));
        assertFalse(CsrfToken.matches(null, "abc"));
        assertFalse(CsrfToken.matches("", ""));
    }
}
