package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 회원 탈퇴 (논리 삭제) — 비밀번호 재확인 후 처리.
 * 관련 요구사항: FR-13
 */
@WebServlet("/profile/withdraw")
public class AccountServlet extends HttpServlet {

    private final UserService userService = new UserService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        Long userId = ((UserDto) session.getAttribute("loginUser")).getId();
        String password = req.getParameter("password");

        try {
            userService.withdraw(userId, password);
        } catch (UserService.InvalidCredentialException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (SQLException e) {
            throw new ServletException("회원 탈퇴 처리 중 오류가 발생했습니다.", e);
        }

        session.invalidate();
        resp.sendRedirect(req.getContextPath() + "/login");
    }
}
