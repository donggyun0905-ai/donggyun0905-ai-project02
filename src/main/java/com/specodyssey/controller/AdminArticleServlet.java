package com.specodyssey.controller;

import com.specodyssey.service.AdminAuditService;
import com.specodyssey.service.AdminArticleService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 관리자 게시판 관리 화면(2026-10-06) — 대기 신고 처리 + 글 목록에서 직접 숨기기/되살리기.
 * 관련 요구사항: db-design.md 4-3 "자동 게시 + 사후 관리", "회의에서 정할 것" 중 관리자 항목.
 */
@WebServlet("/admin/articles")
public class AdminArticleServlet extends HttpServlet {

    static final String MESSAGE_KEY = "adminMessage";
    static final String ERROR_KEY = "adminError";

    private final AdminArticleService adminArticleService = new AdminArticleService();
    private final AdminAuditService auditService = new AdminAuditService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        HttpSession session = req.getSession(false);
        for (String key : new String[] {MESSAGE_KEY, ERROR_KEY}) {
            Object value = session == null ? null : session.getAttribute(key);
            if (value != null) {
                req.setAttribute(key, value);
                session.removeAttribute(key);
            }
        }
        String statusFilter = req.getParameter("status");
        if (statusFilter != null && statusFilter.isBlank()) {
            statusFilter = null;
        }
        int page = parsePage(req.getParameter("page"));
        try {
            req.setAttribute("openReports", adminArticleService.listOpenReports());
            req.setAttribute("articles", adminArticleService.listArticles(statusFilter, page));
            req.setAttribute("totalCount", adminArticleService.countArticles(statusFilter));
        } catch (SQLException e) {
            throw new ServletException("게시판 현황을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.setAttribute("statusFilter", statusFilter);
        req.setAttribute("page", page);
        req.getRequestDispatcher("/WEB-INF/views/admin-articles.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        HttpSession session = req.getSession();
        String action = req.getParameter("action");
        try {
            if ("hide".equals(action)) {
                Long articleId = Long.valueOf(req.getParameter("articleId"));
                adminArticleService.hideArticle(articleId, req.getParameter("reason"));
                auditService.record(AdminSession.loginUser(req), AdminAuditService.ARTICLE_HIDE, "TECH_ARTICLE", articleId,
                        "사유: " + req.getParameter("reason"));
                session.setAttribute(MESSAGE_KEY, "글을 내렸습니다.");
            } else if ("restore".equals(action)) {
                Long articleId = Long.valueOf(req.getParameter("articleId"));
                adminArticleService.restoreArticle(articleId);
                auditService.record(AdminSession.loginUser(req), AdminAuditService.ARTICLE_RESTORE, "TECH_ARTICLE", articleId, null);
                session.setAttribute(MESSAGE_KEY, "글을 다시 공개했습니다.");
            } else if ("dismissReport".equals(action)) {
                Long reportId = Long.valueOf(req.getParameter("reportId"));
                adminArticleService.dismissReport(reportId);
                auditService.record(AdminSession.loginUser(req), AdminAuditService.REPORT_DISMISS, "TECH_ARTICLE_REPORT", reportId, null);
                session.setAttribute(MESSAGE_KEY, "신고를 기각했습니다.");
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("게시판 처리 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/admin/articles");
    }

    private int parsePage(String value) {
        try {
            int page = Integer.parseInt(value);
            return page < 1 ? 1 : page;
        } catch (NumberFormatException | NullPointerException e) {
            return 1;
        }
    }

}
