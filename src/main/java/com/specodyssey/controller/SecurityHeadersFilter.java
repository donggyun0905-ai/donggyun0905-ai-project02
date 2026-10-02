package com.specodyssey.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.specodyssey.util.CsrfToken;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * 공통 보안 헤더 + CSRF 방어(출처 확인 + 세션 토큰).
 *
 * - 응답마다 nosniff / 프레임 삽입 금지 / Referrer 최소화를 붙인다. 업로드 파일이 브라우저에서 다른 형식으로
 *   해석(sniffing)되거나, 다른 사이트가 이 앱을 iframe으로 덮어 클릭을 가로채는 것을 막는다.
 * - 상태를 바꾸는 요청(POST 등)은 ① Origin(없으면 Referer)의 호스트가 요청 Host와 같아야 하고
 *   ② 세션의 CSRF 토큰({@link CsrfToken})을 폼 필드 _csrf 또는 헤더 X-CSRF-Token으로 돌려보내야 한다. 아니면 403.
 *   토큰은 요청 속성 csrfToken으로 JSP에 내려주며, JSP의 모든 POST 폼이 hidden 입력으로 담는다
 *   (CsrfJspCoverageTest가 새 폼에서 빠뜨리지 않았는지 검사한다).
 * - 이 필터가 모든 요청의 첫 관문이라 문자 인코딩도 여기서 UTF-8로 맞춘다 — 폼 값을 읽는 건 이 필터가 처음이다.
 *
 * 세션이 아예 없는 POST는 로그인·가입만 막는다(그 외 보호된 경로는 SessionFilter가 로그인 화면으로 보낸다).
 */
@WebFilter(urlPatterns = {"/*"})
public class SecurityHeadersFilter implements Filter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String[] STATIC_PREFIXES = {"/css/", "/js/", "/img/", "/image/"};
    private static final Set<String> SESSIONLESS_REJECT_PATHS = Set.of("/login", "/register");
    private static final String EXPIRED_MESSAGE = "페이지가 만료됐거나 요청을 확인할 수 없습니다. 새로고침한 뒤 다시 시도해주세요.";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "SAMEORIGIN");
        resp.setHeader("Referrer-Policy", "same-origin");

        if (req.getCharacterEncoding() == null) {
            req.setCharacterEncoding("UTF-8");
        }

        if (SAFE_METHODS.contains(req.getMethod())) {
            if (!isStatic(req.getServletPath())) {
                req.setAttribute(CsrfToken.REQUEST_ATTRIBUTE, CsrfToken.forSession(req.getSession(true)));
            }
            chain.doFilter(request, response);
            return;
        }

        if (!isSameOrigin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "허용되지 않은 출처의 요청입니다.");
            return;
        }

        HttpSession session = req.getSession(false);
        if (session == null && !SESSIONLESS_REJECT_PATHS.contains(req.getServletPath())) {
            chain.doFilter(request, response); // 로그인이 필요한 경로면 SessionFilter가 로그인 화면으로 보낸다
            return;
        }
        String submitted;
        try {
            submitted = req.getHeader(CsrfToken.HEADER_NAME);
            if (submitted == null || submitted.isEmpty()) {
                submitted = req.getParameter(CsrfToken.PARAMETER_NAME);
            }
        } catch (IllegalStateException e) {
            // 첨부 파일이 설정한 용량을 넘으면 컨테이너가 파라미터를 읽는 순간 이렇게 알린다
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "첨부 파일 용량이 너무 큽니다 (파일당 20MB, 전체 100MB 이하).");
            return;
        }
        String expected = session == null ? null : (String) session.getAttribute(CsrfToken.SESSION_ATTRIBUTE);
        if (!CsrfToken.matches(expected, submitted) && isMultipart(req) && exceedsUploadLimit(req)) {
            // getParameter는 용량 초과를 삼키고 null을 돌려준다 — 토큰이 없는 이유가 용량 초과면 그 사실을 알려준다
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "첨부 파일 용량이 너무 큽니다 (파일당 20MB, 전체 100MB 이하).");
            return;
        }
        if (!CsrfToken.matches(expected, submitted)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, EXPIRED_MESSAGE);
            return;
        }
        // 검증에 실패한 입력을 다시 보여주려고 POST 처리 뒤 JSP로 forward하는 경우에도 폼이 같은 토큰을 갖도록
        req.setAttribute(CsrfToken.REQUEST_ATTRIBUTE, expected);
        chain.doFilter(request, response);
    }

    private static boolean isMultipart(HttpServletRequest req) {
        String type = req.getContentType();
        return type != null && type.toLowerCase().startsWith("multipart/");
    }

    private static boolean exceedsUploadLimit(HttpServletRequest req) {
        try {
            req.getParts();
            return false;
        } catch (IllegalStateException e) {
            return true;
        } catch (IOException | ServletException e) {
            return false;
        }
    }

    private static boolean isStatic(String servletPath) {
        if (servletPath == null) {
            return false;
        }
        for (String prefix : STATIC_PREFIXES) {
            if (servletPath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
