package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ShareLinkService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 공유 링크 관리 화면(지원자가 로그인 상태에서 발급·관리). 관련 요구사항: FR-85 · 86
 * 면접관이 로그인 없이 보는 화면은 ShareViewServlet("/share/*")이 따로 처리한다.
 */
@WebServlet("/share-links")
public class ShareLinksServlet extends HttpServlet {

    private final ShareLinkService shareLinkService = new ShareLinkService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        try {
            req.setAttribute("myLinks", shareLinkService.listMine(userId));
        } catch (SQLException e) {
            throw new ServletException("공유 링크 목록을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/share-links.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        String action = req.getParameter("action");

        try {
            if ("create".equals(action)) {
                String label = trimToNull(req.getParameter("label"));
                String expiresParam = trimToNull(req.getParameter("expiresInDays"));
                Integer expiresInDays = expiresParam == null ? null : Integer.valueOf(expiresParam);
                boolean scopeBasic = "true".equals(req.getParameter("scopeBasic"));
                boolean scopeSkills = "true".equals(req.getParameter("scopeSkills"));
                boolean scopeGrowth = "true".equals(req.getParameter("scopeGrowth"));
                shareLinkService.issue(userId, label, expiresInDays, scopeBasic, scopeSkills, scopeGrowth);
            } else if ("toggle".equals(action)) {
                Long linkId = Long.valueOf(req.getParameter("linkId"));
                boolean active = "true".equals(req.getParameter("active"));
                shareLinkService.setActive(userId, linkId, active);
            } else if ("delete".equals(action)) {
                Long linkId = Long.valueOf(req.getParameter("linkId"));
                shareLinkService.delete(userId, linkId);
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (SQLException e) {
            throw new ServletException("공유 링크 처리 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/share-links");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
