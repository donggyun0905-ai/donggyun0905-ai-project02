package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 가입·비밀번호 찾기 직후 복구 코드를 한 번만 보여 주는 화면("/recovery-code", 로그인 전이라 공개 경로).
 * 세션에 실려 온 코드가 없으면(새로고침·직접 접근) 로그인 화면으로 보낸다 — 코드 원문은 다시 볼 수 없다.
 */
@WebServlet("/recovery-code")
public class RecoveryCodeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] notice = RecoveryCodeNotice.take(req.getSession(false));
        if (notice == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        resp.setHeader("Cache-Control", "no-store"); // 뒤로 가기·캐시로 코드가 다시 보이지 않게
        req.setAttribute("recoveryCode", notice[0]);
        req.setAttribute("recoveryContext", notice[1]);
        req.getRequestDispatcher("/WEB-INF/views/recovery-code.jsp").forward(req, resp);
    }
}
