package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 자소서·이력서 첨삭 화면. 관련 요구사항: FR-91 · 92
 * 화면설계 PDF "16. 자소서·이력서 첨삭" 기준 — 화면만(팀 지시). 고정 예시 데이터로 렌더링한다.
 */
@WebServlet("/resume-feedback")
public class ResumeFeedbackServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/resume-feedback.jsp").forward(req, resp);
    }
}
