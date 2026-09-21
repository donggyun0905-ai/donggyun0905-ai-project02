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
import java.time.format.DateTimeParseException;

/**
 * 프로젝트 · 경험 추가 · 수정 · 삭제.
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
            } else if ("update".equals(action)) {
                UserProjectDto project = parseProject(req, resp);
                if (project == null) {
                    return;
                }
                project.setId(Long.valueOf(req.getParameter("projectId")));
                profileService.updateProject(userId, project);
            } else {
                UserProjectDto project = parseProject(req, resp);
                if (project == null) {
                    return;
                }
                profileService.addProject(userId, project);
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (SQLException e) {
            throw new ServletException("프로젝트 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    // 유효성 검사 실패 시 400 응답을 직접 보내고 null을 반환한다.
    private UserProjectDto parseProject(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String title = req.getParameter("title");
        if (title == null || title.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "프로젝트명을 입력해주세요.");
            return null;
        }
        UserProjectDto project = new UserProjectDto();
        project.setTitle(title);
        project.setDescription(req.getParameter("description"));
        project.setTechStack(req.getParameter("techStack"));
        project.setStartDate(parseDate(req.getParameter("startDate")));
        project.setEndDate(parseDate(req.getParameter("endDate")));
        return project;
    }

    private LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
    }
}
