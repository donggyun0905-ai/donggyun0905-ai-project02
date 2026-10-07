package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserEducationDto;
import com.specodyssey.service.EducationService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 프로필 학력 저장 · 삭제. 관련 요구사항: FR-81 이력(학력)
 * 세션의 본인 학력만 다룬다 — 다른 사용자 id를 받지 않는다.
 */
@WebServlet("/profile/education")
public class ProfileEducationServlet extends HttpServlet {

    private final EducationService educationService = new EducationService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();

        try {
            if ("delete".equals(req.getParameter("action"))) {
                educationService.delete(userId);
            } else {
                UserEducationDto education = new UserEducationDto();
                education.setSchoolName(req.getParameter("schoolName"));
                education.setGraduationStatus(req.getParameter("graduationStatus"));
                String date = trimToNull(req.getParameter("graduationDate"));
                education.setGraduationDate(date == null ? null : LocalDate.parse(date));
                String gpa = trimToNull(req.getParameter("gpa"));
                education.setGpa(gpa == null ? null : new BigDecimal(gpa));
                String gpaMax = trimToNull(req.getParameter("gpaMax"));
                education.setGpaMax(gpaMax == null ? null : new BigDecimal(gpaMax));
                educationService.save(userId, education);
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (SQLException e) {
            throw new ServletException("학력 저장 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
