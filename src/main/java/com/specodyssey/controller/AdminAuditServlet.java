package com.specodyssey.controller;

import com.specodyssey.service.AdminAuditService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 관리자 감사 로그 화면(2026-10-07) — 관리자가 남의 데이터를 바꾼 기록을 최신 순으로 본다.
 * 기록은 고치거나 지우지 않는다(append-only) — 그래서 이 화면에 doPost가 없다.
 */
@WebServlet("/admin/audit")
public class AdminAuditServlet extends HttpServlet {

    private final AdminAuditService auditService = new AdminAuditService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        String action = req.getParameter("action");
        int page = parsePage(req.getParameter("page"));
        try {
            req.setAttribute("logs", auditService.list(action, page));
            req.setAttribute("totalCount", auditService.count(action));
            req.setAttribute("totalPages", auditService.countPages(action));
            // EL은 static 메서드를 부를 수 없어서 행동 → 한글 이름 표를 미리 만들어 넘긴다
            java.util.List<String> used = auditService.usedActions();
            java.util.Map<String, String> labels = new java.util.LinkedHashMap<>();
            for (String one : used) {
                labels.put(one, AdminAuditService.actionLabel(one));
            }
            req.setAttribute("usedActions", used);
            req.setAttribute("actionLabels", labels);
        } catch (SQLException e) {
            throw new ServletException("감사 로그를 불러오는 중 오류가 발생했습니다.", e);
        }
        req.setAttribute("page", page);
        req.setAttribute("actionFilter", action);
        req.getRequestDispatcher("/WEB-INF/views/admin-audit.jsp").forward(req, resp);
    }

    private static int parsePage(String value) {
        try {
            int page = Integer.parseInt(value);
            return page < 1 ? 1 : page;
        } catch (NumberFormatException | NullPointerException e) {
            return 1;
        }
    }
}
