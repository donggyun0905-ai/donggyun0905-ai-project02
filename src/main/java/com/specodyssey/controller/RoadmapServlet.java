package com.specodyssey.controller;

import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.RoadmapService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * 로드맵 조회 · 생성 · 완료 체크.
 * 관련 요구사항: FR-32 · 33 · 36
 */
@WebServlet("/roadmap")
public class RoadmapServlet extends HttpServlet {

    private final RoadmapService roadmapService = new RoadmapService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        try {
            RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(userId);
            req.setAttribute("roadmap", roadmap);
            List<RoadmapStepDto> steps = roadmap == null
                    ? Collections.emptyList()
                    : roadmapService.getSteps(roadmap.getId());
            req.setAttribute("steps", steps);
        } catch (SQLException e) {
            throw new ServletException("로드맵을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/roadmap.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        String action = req.getParameter("action");

        try {
            if ("generate".equals(action)) {
                roadmapService.generate(userId);
            } else if ("complete".equals(action)) {
                Long stepId = Long.valueOf(req.getParameter("stepId"));
                boolean completed = "true".equals(req.getParameter("completed"));
                roadmapService.completeStep(userId, stepId, completed);
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (RoadmapService.NoGapAnalysisException e) {
            req.setAttribute("errorMessage", e.getMessage());
            doGet(req, resp);
            return;
        } catch (SQLException e) {
            throw new ServletException("로드맵 처리 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/roadmap");
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
