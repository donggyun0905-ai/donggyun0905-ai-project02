package com.specodyssey.controller;

import com.specodyssey.service.InterviewerViewService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 면접관 뷰(로그인 없이 링크로만 접근). 관련 요구사항: FR-81~86
 * "/share/*"는 이미 SessionFilter의 PUBLIC_PREFIXES에 있어 로그인 없이 열린다(FR-14 면접관은
 * 계정이 없음). 토큰 검증·열람 로그·공개 범위에 따른 데이터 조합은 InterviewerViewService가
 * 전부 처리한다(2026-10-01, "demo" 하드코딩 흉내를 실제 토큰 검증으로 교체).
 */
@WebServlet("/share/*")
public class ShareViewServlet extends HttpServlet {

    private final InterviewerViewService interviewerViewService = new InterviewerViewService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        if ("/compare".equals(pathInfo)) {
            req.getRequestDispatcher("/WEB-INF/views/interviewer-compare.jsp").forward(req, resp);
            return;
        }

        String token = (pathInfo == null || pathInfo.length() < 2) ? "" : pathInfo.substring(1);
        try {
            InterviewerViewService.ViewResult result = token.isBlank()
                    ? null
                    : interviewerViewService.loadView(token, req.getRemoteAddr());
            req.setAttribute("valid", result != null);
            req.setAttribute("view", result);
        } catch (SQLException e) {
            throw new ServletException("공유 이력을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/interviewer-view.jsp").forward(req, resp);
    }
}
