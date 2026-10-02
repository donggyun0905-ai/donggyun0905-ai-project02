package com.specodyssey.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    // 로그아웃은 상태를 바꾸는 요청이라 POST(CSRF 토큰 포함)로만 한다 — 다른 사이트가 <img src="/logout"> 같은 링크로
    // 사용자를 몰래 로그아웃시키지 못하게. 예전 주소(GET)로 들어오면 아무것도 바꾸지 않고 홈으로 보낸다.
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendRedirect(req.getContextPath() + "/login");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        resp.sendRedirect(req.getContextPath() + "/login");
    }
}
