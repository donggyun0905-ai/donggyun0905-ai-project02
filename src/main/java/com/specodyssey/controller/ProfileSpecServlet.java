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
import java.time.format.DateTimeParseException;

/**
 * 보유 스펙 추가 · 수정 · 삭제.
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
            } else if ("update".equals(action)) {
                UserSpecDto spec = parseSpec(req, resp);
                if (spec == null) {
                    return;
                }
                spec.setId(Long.valueOf(req.getParameter("specId")));
                profileService.updateSpec(userId, spec);
            } else {
                UserSpecDto spec = parseSpec(req, resp);
                if (spec == null) {
                    return;
                }
                profileService.addSpec(userId, spec);
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (SQLException e) {
            throw new ServletException("스펙 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    // 유효성 검사 실패 시 400 응답을 직접 보내고 null을 반환한다.
    private UserSpecDto parseSpec(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String title = req.getParameter("title");
        if (title == null || title.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "명칭을 입력해주세요.");
            return null;
        }
        UserSpecDto spec = new UserSpecDto();
        spec.setSpecType(req.getParameter("specType"));
        spec.setTitle(title);
        spec.setIssuer(req.getParameter("issuer"));
        spec.setScore(req.getParameter("score"));
        String acquiredDateParam = req.getParameter("acquiredDate");
        if (acquiredDateParam != null && !acquiredDateParam.isBlank()) {
            spec.setAcquiredDate(LocalDate.parse(acquiredDateParam));
        }
        return spec;
    }
}
