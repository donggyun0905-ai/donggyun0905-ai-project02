package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.AiUsageLogService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * AI 활용 기록 자기 제출 · 삭제 · 공유 여부 전환. 관련 요구사항: FR-101(선택) · 102(선택), NFR-4
 */
@WebServlet("/profile/ai-usage")
public class ProfileAiUsageServlet extends HttpServlet {

    private final AiUsageLogService aiUsageLogService = new AiUsageLogService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = currentUserId(req);
        String action = req.getParameter("action");

        try {
            if ("delete".equals(action)) {
                Long logId = Long.valueOf(req.getParameter("logId"));
                aiUsageLogService.delete(userId, logId);
            } else if ("toggleShare".equals(action)) {
                Long logId = Long.valueOf(req.getParameter("logId"));
                boolean shared = "true".equals(req.getParameter("shared"));
                aiUsageLogService.setShared(userId, logId, shared);
            } else {
                String title = req.getParameter("title");
                String description = req.getParameter("description");
                boolean shared = "true".equals(req.getParameter("shared"));
                try {
                    aiUsageLogService.submit(userId, title, description, shared);
                } catch (IllegalArgumentException e) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
                    return;
                }
            }
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (SQLException e) {
            throw new ServletException("AI 활용 기록 처리 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
