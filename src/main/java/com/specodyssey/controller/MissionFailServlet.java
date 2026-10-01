package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.MissionSubmitService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 일일 미션 "실패" 버튼 — 미션을 실패(완료 + 오답)로 표시하고 미션 화면으로 돌아간다.
 * 관련 요구사항: FR-53
 * 상태를 바꾸는 요청이라 POST만 받는다. 항상 세션의 본인 미션만 바꾼다.
 */
@WebServlet("/mission/fail")
public class MissionFailServlet extends HttpServlet {

    private final MissionSubmitService submitService = new MissionSubmitService();

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
            if (!submitService.markFailed(userId, missionId)) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "미션을 찾을 수 없습니다.");
                return;
            }
        } catch (SQLException e) {
            throw new ServletException("미션 실패 처리 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/mission");
    }
}
