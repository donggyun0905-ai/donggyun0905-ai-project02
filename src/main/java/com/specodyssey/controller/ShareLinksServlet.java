package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 공유 링크 관리 화면(지원자가 로그인 상태에서 발급·관리). 관련 요구사항: FR-85 · 86
 * 화면설계 PDF "12. 공유 링크 관리" 기준 — 화면만(팀 지시). 고정 예시 데이터로 렌더링한다.
 * 면접관이 로그인 없이 보는 화면은 ShareViewServlet("/share/*")이 따로 처리한다.
 */
@WebServlet("/share-links")
public class ShareLinksServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/share-links.jsp").forward(req, resp);
    }
}
