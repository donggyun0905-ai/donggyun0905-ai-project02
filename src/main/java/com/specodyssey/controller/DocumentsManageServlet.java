package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 서류 보관함 관리 화면(목록 + 업로드 폼). 관련 요구사항: FR-61~63
 * 화면설계 PDF "15. 서류 보관함" 기준 — 화면만(팀 지시). 고정 예시 데이터로 렌더링한다.
 * 정확히 "/documents"만 여기서 받는다 — "/documents/{id}" 실제 다운로드는
 * DocumentDownloadServlet(경로 매칭 "/documents/*")이 이미 처리하고 있어 겹치지 않는다.
 */
@WebServlet("/documents")
public class DocumentsManageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/documents-manage.jsp").forward(req, resp);
    }
}
