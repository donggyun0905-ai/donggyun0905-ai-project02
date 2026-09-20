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
 * 로그인.
 * 관련 요구사항: FR-12
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String loginId = req.getParameter("loginId");
        String password = req.getParameter("password");

        try {
            UserDto user = userService.login(loginId, password);
            user.setPasswordHash(null); // 세션에는 해시조차 남기지 않는다
            HttpSession session = req.getSession();
            session.setAttribute("loginUser", user);
            resp.sendRedirect(req.getContextPath() + "/");
        } catch (UserService.InvalidCredentialException e) {
            req.setAttribute("errorMessage", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
        } catch (SQLException e) {
            throw new ServletException("로그인 처리 중 오류가 발생했습니다.", e);
        }
    }
}
