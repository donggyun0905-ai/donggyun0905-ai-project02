package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.InterviewerService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 면접관 계정 화면 — 공유받은 이력("/interviewer/shared"), 지원자 비교("/interviewer/compare"),
 * 내 프로필("/interviewer/profile"). 관련 요구사항: FR-82 · 83
 * 면접관 계정만 들어올 수 있다(RoleFilter). 항상 세션의 본인 비교 목록만 다룬다.
 */
@WebServlet("/interviewer/*")
public class InterviewerServlet extends HttpServlet {

    private static final String SHARED = "/shared";
    private static final String COMPARE = "/compare";
    private static final String PROFILE = "/profile";

    private final InterviewerService interviewerService = new InterviewerService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            show(req, resp, req.getPathInfo());
        } catch (SQLException e) {
            throw new ServletException("면접관 화면을 불러오는 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = loginUserId(req);
        String page = req.getPathInfo();
        String action = req.getParameter("action");

        try {
            try {
                if (SHARED.equals(page) && "add".equals(action)) {
                    interviewerService.addLink(userId, req.getParameter("link"));
                } else if (SHARED.equals(page) && "remove".equals(action)) {
                    interviewerService.removeItem(userId, Long.valueOf(req.getParameter("itemId")));
                } else if (COMPARE.equals(page) && "addCriterion".equals(action)) {
                    interviewerService.saveCriterion(userId, req.getParameter("skillName"),
                            Integer.parseInt(req.getParameter("weight")));
                } else if (COMPARE.equals(page) && "removeCriterion".equals(action)) {
                    interviewerService.removeCriterion(userId, Long.valueOf(req.getParameter("criterionId")));
                } else if (PROFILE.equals(page)) {
                    interviewerService.updateProfile(userId, req.getParameter("name"),
                            req.getParameter("email"), req.getParameter("companyName"));
                    req.getSession(false).setAttribute("interviewerNotice", "저장했습니다.");
                } else {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                    return;
                }
            } catch (NumberFormatException e) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            } catch (IllegalArgumentException e) {
                req.setAttribute("errorMessage", e.getMessage());
                show(req, resp, page);
                return;
            }
        } catch (SQLException e) {
            throw new ServletException("면접관 화면 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/interviewer" + page);
    }

    private void show(HttpServletRequest req, HttpServletResponse resp, String page)
            throws SQLException, ServletException, IOException {
        Long userId = loginUserId(req);
        if (SHARED.equals(page)) {
            req.setAttribute("compare", interviewerService.loadCompare(userId));
            forward(req, resp, "interviewer-shared.jsp");
        } else if (COMPARE.equals(page)) {
            req.setAttribute("compare", interviewerService.loadCompare(userId));
            req.setAttribute("skills", interviewerService.listSkills());
            req.setAttribute("company", interviewerService.getOrCreateSession(userId).getCompanyName());
            forward(req, resp, "interviewer-compare.jsp");
        } else if (PROFILE.equals(page)) {
            Object notice = req.getSession(false).getAttribute("interviewerNotice");
            if (notice != null) {
                req.getSession(false).removeAttribute("interviewerNotice");
                req.setAttribute("notice", notice);
            }
            req.setAttribute("user", interviewerService.findUser(userId));
            req.setAttribute("company", interviewerService.getOrCreateSession(userId).getCompanyName());
            forward(req, resp, "interviewer-profile.jsp");
        } else {
            resp.sendRedirect(req.getContextPath() + RoleFilter.INTERVIEWER_HOME);
        }
    }

    private void forward(HttpServletRequest req, HttpServletResponse resp, String jsp)
            throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/" + jsp).forward(req, resp);
    }

    private Long loginUserId(HttpServletRequest req) {
        return ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
    }
}
