package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.MissionUndoService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 오늘의 미션 "완료 취소"·"실패 취소" 버튼 (2026-10-12) — 끝낸 미션을 안 푼 상태로 되돌리고 미션 화면으로 돌아간다.
 * 세션의 본인 미션에만 적용한다. POST라 SecurityHeadersFilter가 CSRF 토큰을 확인한다.
 */
@WebServlet("/mission/undo")
public class MissionUndoServlet extends HttpServlet {

    private final MissionUndoService undoService = new MissionUndoService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long missionId;
        try {
            missionId = Long.valueOf(req.getParameter("missionId"));
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        }
        // SessionFilter가 로그인을 보장하는 경로라 세션의 loginUser가 항상 있다
        Long userId = ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
        try {
            if (!undoService.undo(userId, missionId)) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "취소할 수 있는 오늘 미션을 찾을 수 없습니다.");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException("미션 취소 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/mission");
    }
}
