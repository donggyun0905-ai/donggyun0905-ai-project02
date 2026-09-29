package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 데이터 인사이트 화면. 관련 요구사항: FR-45~48
 * 화면설계 PDF "10. 데이터 인사이트" 기준 — 화면만(팀 지시). 고정 예시 데이터로 렌더링한다.
 */
@WebServlet("/insights")
public class InsightsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/insights.jsp").forward(req, resp);
    }
}
