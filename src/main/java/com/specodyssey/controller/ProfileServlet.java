package com.specodyssey.controller;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ProfileService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 프로필 조회 · 기본정보 수정 화면.
 * 관련 요구사항: FR-21 · 22 · 23 · 24 · 25
 */
@WebServlet("/profile")
public class ProfileServlet extends HttpServlet {

    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();
    private final ProfileService profileService = new ProfileService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        try {
            req.setAttribute("user", userDao.findById(userId));
            req.setAttribute("jobs", jobDao.findAll());
            req.setAttribute("specs", profileService.getSpecs(userId));
            req.setAttribute("projects", profileService.getProjects(userId));
            req.setAttribute("skills", profileService.getSkills(userId));
        } catch (SQLException e) {
            throw new ServletException("프로필을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = currentUserId(req);

        String email = req.getParameter("email");
        String major = req.getParameter("major");
        String grade = req.getParameter("grade");
        String interestField = req.getParameter("interestField");
        String desiredJobStatus = req.getParameter("desiredJobStatus");
        String desiredJobIdParam = req.getParameter("desiredJobId");

        Long desiredJobId = null;
        if ("SET".equals(desiredJobStatus) && desiredJobIdParam != null && !desiredJobIdParam.isBlank()) {
            try {
                desiredJobId = Long.valueOf(desiredJobIdParam);
            } catch (NumberFormatException e) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
        }

        try {
            profileService.updateBasicInfo(userId, email, major, grade, interestField, desiredJobId, desiredJobStatus);

            // 세션에 들고 있는 사본도 최신화 (비밀번호 해시는 세션에 두지 않는다)
            UserDto refreshed = userDao.findById(userId);
            refreshed.setPasswordHash(null);
            req.getSession().setAttribute("loginUser", refreshed);
        } catch (SQLException e) {
            throw new ServletException("프로필 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
