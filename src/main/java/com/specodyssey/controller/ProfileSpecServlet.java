package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSpecDto;
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
 * 보유 스펙 추가 · 삭제.
 * 관련 요구사항: FR-23
 */
@WebServlet("/profile/specs")
public class ProfileSpecServlet extends HttpServlet {

    private final ProfileService profileService = new ProfileService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        Long userId = ((UserDto) session.getAttribute("loginUser")).getId();
        String action = req.getParameter("action");

        try {
            if ("delete".equals(action)) {
                Long specId = Long.valueOf(req.getParameter("specId"));
                profileService.deleteSpec(userId, specId);
            } else {
                UserSpecDto spec = new UserSpecDto();
                spec.setSpecType(req.getParameter("specType"));
                spec.setTitle(req.getParameter("title"));
                spec.setIssuer(req.getParameter("issuer"));
                spec.setScore(req.getParameter("score"));
                String acquiredDateParam = req.getParameter("acquiredDate");
                if (acquiredDateParam != null && !acquiredDateParam.isBlank()) {
                    spec.setAcquiredDate(LocalDate.parse(acquiredDateParam));
                }
                profileService.addSpec(userId, spec);
            }
        } catch (SQLException e) {
            throw new ServletException("스펙 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }
}
