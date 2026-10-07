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
                profileService.updateProject(userId, project, ProjectLinkForm.parse(req));
            } else {
                UserProjectDto project = parseProject(req, resp);
                if (project == null) {
                    return;
                }
                profileService.addProject(userId, project, ProjectLinkForm.parse(req));
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            // 입력 실수에 sendError를 쓰면 컨테이너 에러 페이지가 떠서 적던 내용이 통째로 날아간다
            // (web.xml에 400 항목이 없다) — 프로필 화면에 문구로 돌려준다. 기술·스펙 칸과 같은 방식.
            ProfileNotice.putError(req, "날짜와 팀 인원은 숫자·날짜 형식으로 입력해주세요.");
            resp.sendRedirect(req.getContextPath() + "/profile");
            return;
        } catch (IllegalArgumentException e) {
            // 저장소·배포·기타 링크 형식 오류 — 어떤 값이 왜 안 되는지 그대로 알려준다
            ProfileNotice.putError(req, e.getMessage());
            resp.sendRedirect(req.getContextPath() + "/profile");
            return;
        } catch (SQLException e) {
            throw new ServletException("프로젝트 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    // 유효성 검사 실패 시 프로필 화면으로 문구와 함께 돌려보내고 null을 반환한다.
    private UserProjectDto parseProject(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String title = req.getParameter("title");
        if (title == null || title.isBlank()) {
            ProfileNotice.putError(req, "프로젝트명을 입력해주세요.");
            resp.sendRedirect(req.getContextPath() + "/profile");
            return null;
        }
        UserProjectDto project = new UserProjectDto();
        project.setTitle(title);
        project.setDescription(req.getParameter("description"));
        project.setTechStack(req.getParameter("techStack"));
        project.setRepoUrl(trimToNull(req.getParameter("repoUrl")));
        project.setDeployUrl(trimToNull(req.getParameter("deployUrl")));
        project.setStartDate(parseDate(req.getParameter("startDate")));
        project.setEndDate(parseDate(req.getParameter("endDate")));
        String teamSize = trimToNull(req.getParameter("teamSize"));
        project.setTeamSize(teamSize == null ? null : Integer.valueOf(teamSize)); // 숫자가 아니면 NumberFormatException → 프로필 화면에 안내
        project.setMyRole(trimToNull(req.getParameter("myRole")));
        return project;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
    }
}
