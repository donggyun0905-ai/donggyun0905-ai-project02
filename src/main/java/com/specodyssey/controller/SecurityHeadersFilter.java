package com.specodyssey.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * 공통 보안 헤더 + 교차 출처 POST 차단(CSRF 1차 방어).
 *
 * - 응답마다 nosniff / 프레임 삽입 금지 / Referrer 최소화를 붙인다. 업로드 파일이 브라우저에서 다른 형식으로
 *   해석(sniffing)되거나, 다른 사이트가 이 앱을 iframe으로 덮어 클릭을 가로채는 것을 막는다.
 * - 상태를 바꾸는 요청(POST 등)의 Origin(없으면 Referer) 호스트가 요청 Host와 다르면 403.
 *   폼마다 토큰을 넣는 방식은 JSP 80여 개를 고쳐야 해서, 같은 출처 검사로 먼저 막는다
 *   (세션 쿠키 SameSite=Lax와 함께 쓴다). Origin·Referer가 둘 다 없는 요청(curl 등)은 브라우저가 보낸
 *   교차 출처 폼이 아니므로 통과시킨다.
 */
@WebFilter(urlPatterns = {"/*"})
public class SecurityHeadersFilter implements Filter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "SAMEORIGIN");
        resp.setHeader("Referrer-Policy", "same-origin");

        if (!SAFE_METHODS.contains(req.getMethod()) && !isSameOrigin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "허용되지 않은 출처의 요청입니다.");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isSameOrigin(HttpServletRequest req) {
        String source = req.getHeader("Origin");
        if (source == null || source.isBlank() || "null".equals(source)) {
            source = req.getHeader("Referer");
        }
        if (source == null || source.isBlank()) {
            return true;
        }
        return hostMatches(source, req.getHeader("Host"));
    }

    // Host 헤더("host:port")와 Origin/Referer URL의 호스트:포트가 같은지 본다.
    static boolean hostMatches(String sourceUrl, String hostHeader) {
        if (hostHeader == null) {
            return false;
        }
        try {
            URI uri = URI.create(sourceUrl);
            if (uri.getHost() == null) {
                return false;
            }
            String authority = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
            return authority.equalsIgnoreCase(hostHeader.trim())
                    || uri.getHost().equalsIgnoreCase(hostHeader.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
