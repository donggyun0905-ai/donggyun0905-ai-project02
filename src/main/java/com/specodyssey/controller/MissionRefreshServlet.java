package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.MissionRefreshService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 오늘의 미션 "새로 불러오기" 버튼 (2026-10-12) — 안 푼 미션을 지금 등급 난이도로 다시 뽑고 미션 화면으로 돌아간다.
 * 세션의 본인 미션에만 적용한다. POST라 SecurityHeadersFilter가 CSRF 토큰을 확인한다.
 */
@WebServlet("/mission/refresh")
public class MissionRefreshServlet extends HttpServlet {

    private final MissionRefreshService refreshService = new MissionRefreshService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // SessionFilter가 로그인을 보장하는 경로라 세션의 loginUser가 항상 있다
        Long userId = ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
        try {
            refreshService.refreshToday(userId);
        } catch (SQLException e) {
            throw new ServletException("미션을 새로 불러오는 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/mission");
    }
}
