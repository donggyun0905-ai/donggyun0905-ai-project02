package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.companion.CompanionAuthService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 데스크톱 캐릭터가 이 브라우저의 로그인을 따라간다 (2026-10-08).
 *
 * 캐릭터를 연결한 브라우저에는 "이 PC의 캐릭터" 쿠키가 있다 ({@link CompanionServlet#LINK_COOKIE}).
 *   POST /logout          → 캐릭터는 연결을 둔 채 쉰다 ("로그인하면 자동으로 연결돼요")
 *   POST /login 로그인 성공 → 캐릭터가 방금 로그인한 계정으로 옮겨 간다. "캐릭터 연결"을 다시 누르지 않아도 된다.
 *                           캐릭터를 쓰지 않는 계정(면접관·관리자)이면 쉬게 한다.
 * 로그인·로그아웃 자체는 건드리지 않는다 — 요청을 끝까지 보낸 뒤 결과(세션)만 보고 판단하고, 실패해도 화면은 그대로다.
 */
@WebFilter(urlPatterns = {"/login", "/logout"})
public class CompanionLinkFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(CompanionLinkFilter.class.getName());

    private final CompanionAuthService authService;

    public CompanionLinkFilter() {
        this(new CompanionAuthService());
    }

    CompanionLinkFilter(CompanionAuthService authService) {
        this.authService = authService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String link = "POST".equals(req.getMethod()) ? CompanionServlet.linkCookie(req) : null;
        if (link == null) {
            chain.doFilter(request, response);
            return;
        }
        Long before = loginUserId(req.getSession(false));
        chain.doFilter(request, response);
        try {
            if ("/logout".equals(req.getServletPath())) {
                authService.onLogout(link);
                return;
            }
            UserDto after = loginUser(req.getSession(false));
            if (after != null && !after.getId().equals(before)) {
                authService.onLogin(link, after.getId(), usesCharacter(after));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "캐릭터 계정 따라가기 실패 — 로그인·로그아웃은 그대로 진행됐습니다", e);
        }
    }

    /** 캐릭터는 구직자용 — 면접관·관리자 화면에는 캐릭터 메뉴가 없다 */
    static boolean usesCharacter(UserDto user) {
        return !RoleFilter.INTERVIEWER.equals(user.getUserType()) && !RoleFilter.ADMIN.equals(user.getUserType());
    }

    private static UserDto loginUser(HttpSession session) {
        try {
            return session == null ? null : (UserDto) session.getAttribute("loginUser");
        } catch (IllegalStateException e) {
            return null; // 이미 무효화된 세션
        }
    }

    private static Long loginUserId(HttpSession session) {
        UserDto user = loginUser(session);
        return user == null ? null : user.getId();
    }
}
