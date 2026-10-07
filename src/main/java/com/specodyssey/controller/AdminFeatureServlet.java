package com.specodyssey.controller;

import com.specodyssey.service.FeatureCoverageService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 관리자 기능 설계서·테스트 목록 화면(2026-10-07 사용자 요청).
 * 두 화면이 같은 연결표(FeatureCoverageService)를 쓰고 보여 주는 칼럼만 다르다 — 그래서 한 서블릿이 둘을 맡는다.
 * 내용은 빌드가 소스에서 만든 것이라 코드가 바뀌면 함께 바뀐다(손으로 쓰지 않는다).
 */
@WebServlet({"/admin/design", "/admin/tests"})
public class AdminFeatureServlet extends HttpServlet {

    private final FeatureCoverageService coverageService = new FeatureCoverageService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        boolean testsView = req.getServletPath().endsWith("/tests");
        req.setAttribute("byArea", coverageService.byArea());
        req.setAttribute("summary", coverageService.summary());
        req.setAttribute("adminTab", testsView ? "tests" : "design");
        req.getRequestDispatcher(testsView
                ? "/WEB-INF/views/admin-tests.jsp"
                : "/WEB-INF/views/admin-design.jsp").forward(req, resp);
    }
}
