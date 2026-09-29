package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 격차 분석 화면. 관련 요구사항: FR-31 · 42 · 111 · 112
 * 화면설계 PDF "8. 격차 분석" + "6-1~6-3"(AI 응답 대기·실패·데이터 없음) 기준 — 화면만(팀 지시).
 * ?state=loading|failed|unavailable 로 각 상태를 그대로 미리볼 수 있게 해뒀다. 실제 격차 분석
 * 판정은 다른 담당자 몫이라 여기서는 고정 예시 데이터로 렌더링한다.
 */
@WebServlet("/gap-analysis")
public class GapAnalysisServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("state", req.getParameter("state"));
        req.getRequestDispatcher("/WEB-INF/views/gap-analysis.jsp").forward(req, resp);
    }
}
