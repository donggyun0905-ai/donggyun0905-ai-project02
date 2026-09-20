package com.specodyssey.controller;

import com.specodyssey.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 회원가입.
 * 관련 요구사항: FR-11 · 12 · 13 · 14
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/signup.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String loginId = req.getParameter("loginId");
        String password = req.getParameter("password");
        String email = req.getParameter("email");
        String major = req.getParameter("major");
        String grade = req.getParameter("grade");
        String interestField = req.getParameter("interestField");

        try {
            userService.register(loginId, password, email, major, grade, interestField);
            resp.sendRedirect(req.getContextPath() + "/login");
        } catch (UserService.DuplicateLoginIdException e) {
            req.setAttribute("errorMessage", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/signup.jsp").forward(req, resp);
        } catch (SQLException e) {
            throw new ServletException("회원가입 처리 중 오류가 발생했습니다.", e);
        }
    }
}
