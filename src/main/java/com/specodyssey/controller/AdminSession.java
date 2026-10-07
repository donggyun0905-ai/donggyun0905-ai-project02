package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.util.AdminAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 관리자 화면이 쓰는 세션 읽기 (2026-10-07).
 * 어드민 서블릿 네 곳이 같은 isAdmin을 각자 들고 있었다 — 감사 로그가 "누가 했는지"를 알아야 해서
 * 로그인 사용자도 함께 필요해지면서 한곳으로 모았다.
 */
final class AdminSession {

    private AdminSession() {
    }

    /** 로그인한 사용자 (없으면 null) */
    static UserDto loginUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (UserDto) session.getAttribute("loginUser");
    }

    static boolean isAdmin(HttpServletRequest req) {
        return AdminAccess.isAdmin(loginUser(req));
    }
}
