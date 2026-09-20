package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.service.ProfileService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * 프로젝트 · 경험 추가 · 삭제.
 * 관련 요구사항: FR-24
 */
@WebServlet("/profile/projects")
public class ProfileProjectServlet extends HttpServlet {

    private final ProfileService profileService = new ProfileService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        Long userId = ((UserDto) session.getAttribute("loginUser")).getId();
        String action = req.getParameter("action");

        try {
            if ("delete".equals(action)) {
                Long projectId = Long.valueOf(req.getParameter("projectId"));
                profileService.deleteProject(userId, projectId);
            } else {
                UserProjectDto project = new UserProjectDto();
                project.setTitle(req.getParameter("title"));
                project.setDescription(req.getParameter("description"));
                project.setTechStack(req.getParameter("techStack"));
                project.setStartDate(parseDate(req.getParameter("startDate")));
                project.setEndDate(parseDate(req.getParameter("endDate")));
                profileService.addProject(userId, project);
            }
        } catch (SQLException e) {
            throw new ServletException("프로젝트 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    private LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
    }
}
