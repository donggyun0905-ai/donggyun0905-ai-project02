package com.specodyssey.controller;

import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 밖으로 내보내는 절대 주소가 "지금 접속한 주소" 그대로 만들어지는지 (2026-10-12).
 *
 * 공유 링크와 캐릭터에 넘기는 server= 주소는 사용자가 복사해서 다른 사람에게 보내거나
 * 다른 프로그램이 받아 쓰는 값이다. 여기서 localhost:8080이 새어 나가면 밖에서는 열리지 않는다.
 *
 * 클라우드플레어 터널 뒤에서는 cloudflared가 평문 http로 톰캣에 붙기 때문에 톰캣이 그냥 두면
 * http://…:8080 으로 본다. RemoteIpValve(META-INF/context.xml)가 X-Forwarded-Proto를 읽어
 * scheme·port를 바로잡아 주고, 그러면 아래 코드가 손대지 않아도 올바른 주소를 만든다.
 * 이 테스트는 "밸브가 제 일을 한 뒤"의 상태를 흉내 내서 그 계약을 고정한다.
 */
class PublicUrlBehindProxyTest {

    private static FakeWeb.Request req(String scheme, String host, int port, String ctx) {
        FakeWeb.Request r = FakeWeb.request();
        r.scheme = scheme;
        r.serverName = host;
        r.serverPort = port;
        r.contextPath = ctx;
        return r;
    }

    @Test
    @DisplayName("터널 뒤(https·443)에서는 포트가 붙지 않는다 — 공유 링크")
    void 공유_링크는_포트_없는_https_주소가_된다() {
        assertEquals("https://spec.example.com/share/",
                ShareLinksServlet.shareBaseUrl(req("https", "spec.example.com", 443, "").http()));
    }

    @Test
    @DisplayName("터널 뒤(https·443)에서는 포트가 붙지 않는다 — 캐릭터 server=")
    void 캐릭터_주소도_포트_없는_https가_된다() {
        assertEquals("https://spec.example.com",
                CompanionApiServlet.baseUrl(req("https", "spec.example.com", 443, "").http()));
    }

    @Test
    @DisplayName("컨텍스트 경로가 있으면 주소에 함께 들어간다 — ROOT.war가 아닐 때")
    void 컨텍스트_경로를_빠뜨리지_않는다() {
        assertEquals("https://spec.example.com/spec-odyssey/share/",
                ShareLinksServlet.shareBaseUrl(req("https", "spec.example.com", 443, "/spec-odyssey").http()));
        assertEquals("https://spec.example.com/spec-odyssey",
                CompanionApiServlet.baseUrl(req("https", "spec.example.com", 443, "/spec-odyssey").http()));
    }

    @Test
    @DisplayName("로컬에서 직접 띄우면 예전처럼 포트가 붙는다 — 개발이 망가지지 않아야 한다")
    void 로컬_개발_주소는_그대로다() {
        assertEquals("http://localhost:8080/share/",
                ShareLinksServlet.shareBaseUrl(req("http", "localhost", 8080, "").http()));
        assertEquals("http://localhost:8080",
                CompanionApiServlet.baseUrl(req("http", "localhost", 8080, "").http()));
    }

    @Test
    @DisplayName("http 80 · https 443만 포트를 뗀다 — 8443 같은 건 붙여야 열린다")
    void 기본_포트만_생략한다() {
        assertEquals("https://spec.example.com:8443/share/",
                ShareLinksServlet.shareBaseUrl(req("https", "spec.example.com", 8443, "").http()));
        assertEquals("http://spec.example.com/share/",
                ShareLinksServlet.shareBaseUrl(req("http", "spec.example.com", 80, "").http()));
    }
}
