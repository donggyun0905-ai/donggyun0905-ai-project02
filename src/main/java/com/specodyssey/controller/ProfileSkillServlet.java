package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSkillDto;
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
 * 보유 기술 스택 추가 · 삭제.
 * 관련 요구사항: FR-25
 * skill_id 매칭(임베딩)은 2주차 배치 범위라 여기서는 raw_input만 저장한다.
 */
@WebServlet("/profile/skills")
public class ProfileSkillServlet extends HttpServlet {

    private final ProfileService profileService = new ProfileService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        Long userId = ((UserDto) session.getAttribute("loginUser")).getId();
        String action = req.getParameter("action");

        try {
            if ("delete".equals(action)) {
                Long userSkillId = Long.valueOf(req.getParameter("userSkillId"));
                profileService.deleteSkill(userId, userSkillId);
            } else {
                UserSkillDto skill = new UserSkillDto();
                skill.setRawInput(req.getParameter("rawInput"));
                skill.setProficiency(req.getParameter("proficiency"));
                profileService.addSkill(userId, skill);
            }
        } catch (SQLException e) {
            throw new ServletException("기술 스택 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }
}
