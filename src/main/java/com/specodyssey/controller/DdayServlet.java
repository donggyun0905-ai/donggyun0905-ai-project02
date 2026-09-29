package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * D-day 알림 화면. 관련 요구사항: FR-71~73
 * 화면설계 PDF "17. D-day 알림" 기준 — 화면만(팀 지시). 고정 예시 데이터로 렌더링한다.
 */
@WebServlet("/dday")
public class DdayServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/dday.jsp").forward(req, resp);
    }
}
