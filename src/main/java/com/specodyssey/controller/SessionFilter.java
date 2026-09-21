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

import java.io.IOException;
import java.util.Set;

/**
 * 로그인 세션 확인 필터. 세션에 loginUser가 없으면 로그인 화면으로 보낸다.
 * 다른 사용자 id를 파라미터로 받아 조회하지 않고, 항상 세션의 본인 정보만 쓰는 전제가 여기서 시작된다.
 *
 * 모든 경로({@code /*})에 걸어두고 PUBLIC_PATHS/PUBLIC_PREFIXES에 나열한 것만 예외로 공개한다
 * (화이트리스트 방식). "보호할 경로를 나열"하는 방식은 새 URL(/roadmap, /analysis, /mission 등)을
 * 추가할 때 필터 등록을 깜빡하면 그대로 로그인 없이 열리므로, 기본값이 "보호됨"인 이 방식이 더 안전하다.
 */
@WebFilter(urlPatterns = {"/*"})
public class SessionFilter implements Filter {

    // 로그인 없이 접근 가능한 정확한 경로
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/", "/index.jsp", "/login", "/register"
    );

    // 로그인 없이 접근 가능한 경로 접두사 (정적 리소스 등)
    // "/share/"는 FR-85 면접관 공유 링크용으로 미리 공개해둔다 — 면접관은 계정이 없어 로그인할 수 없고
    // (FR-14), 접근 제어는 로그인이 아니라 ShareLinkDao.findByToken의 토큰·활성·만료 확인이 대신한다.
    private static final String[] PUBLIC_PREFIXES = {
            "/css/", "/js/", "/img/", "/share/"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (isPublic(req.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("loginUser") == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
