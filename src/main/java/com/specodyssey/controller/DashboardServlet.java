package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 대시보드 화면. 관련 요구사항: FR-41~44
 * 화면설계 PDF(개발자료/화면 설계/spec-odyssey-screens.pdf) "9. 대시보드" 기준 — 지금은 화면만
 * 만들고(팀 지시) 실제 완성도·격차·여정지도 집계는 담당자가 채운다. JSP는 고정 예시 데이터로 렌더링한다.
 */
@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req, resp);
    }
}
