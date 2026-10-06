package com.specodyssey.controller;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.AdminUserService;
import com.specodyssey.util.AdminAccess;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 관리자 회원 관리 화면(2026-10-06) — 검색 → 프로필 수정 / 비밀번호 재설정 / 탈퇴 처리·취소.
 * 로그인 아이디·user_type(관리자 권한 자체)은 여기서 안 바꾼다 — 사고 위험이 더 커서 범위 밖으로 뺐다.
 */
@WebServlet("/admin/users")
public class AdminUserServlet extends HttpServlet {

    static final String MESSAGE_KEY = "adminMessage";
    static final String ERROR_KEY = "adminError";

    private final AdminUserService adminUserService = new AdminUserService();
    private final JobDao jobDao = new JobDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        HttpSession session = req.getSession(false);
        for (String key : new String[] {MESSAGE_KEY, ERROR_KEY}) {
            Object value = session == null ? null : session.getAttribute(key);
            if (value != null) {
                req.setAttribute(key, value);
                session.removeAttribute(key);
            }
        }
        String keyword = req.getParameter("q");
        String editIdParam = req.getParameter("edit");
        try {
            req.setAttribute("keyword", keyword);
            req.setAttribute("results", adminUserService.search(keyword));
            if (editIdParam != null) {
                UserDto target = adminUserService.find(Long.valueOf(editIdParam));
                if (target == null) {
                    req.setAttribute("adminError", "사용자를 찾을 수 없습니다.");
                } else {
                    req.setAttribute("editUser", target);
                    req.setAttribute("jobs", jobDao.findAll());
                }
            }
        } catch (SQLException e) {
            throw new ServletException("회원 정보를 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/admin-users.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession();
        String action = req.getParameter("action");
        Long userId = parseLong(req.getParameter("userId"));
        try {
            if ("updateProfile".equals(action)) {
                adminUserService.updateProfile(userId, blankToNull(req.getParameter("name")),
                        parseInt(req.getParameter("age")), blankToNull(req.getParameter("careerStatus")),
                        blankToNull(req.getParameter("email")), blankToNull(req.getParameter("major")),
                        blankToNull(req.getParameter("grade")), blankToNull(req.getParameter("interestField")),
                        parseLong(req.getParameter("desiredJobId")), blankToNull(req.getParameter("desiredJobStatus")));
                session.setAttribute(MESSAGE_KEY, "프로필을 저장했습니다.");
            } else if ("resetPassword".equals(action)) {
                adminUserService.resetPassword(userId, req.getParameter("newPassword"));
                session.setAttribute(MESSAGE_KEY, "비밀번호를 재설정했습니다. 당사자에게 새 비밀번호를 안전하게 전달해주세요.");
            } else if ("softDelete".equals(action)) {
                adminUserService.softDelete(userId);
                session.setAttribute(MESSAGE_KEY, "탈퇴 처리했습니다.");
            } else if ("cancelWithdrawal".equals(action)) {
                boolean cancelled = adminUserService.cancelWithdrawal(userId);
                session.setAttribute(MESSAGE_KEY, cancelled ? "탈퇴를 취소했습니다." : "유예 기간이 지나 취소할 수 없습니다.");
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("회원 처리 중 오류가 발생했습니다.", e);
        }
        String redirectQuery = req.getParameter("returnQuery");
        resp.sendRedirect(req.getContextPath() + "/admin/users"
                + (redirectQuery == null || redirectQuery.isBlank() ? "" : "?" + redirectQuery));
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isAdmin(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto user = session == null ? null : (UserDto) session.getAttribute("loginUser");
        return AdminAccess.isAdmin(user);
    }
}
