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
 * 보유 기술 스택 추가 · 수정 · 삭제.
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
            } else if ("update".equals(action)) {
                UserSkillDto skill = parseSkill(req, resp);
                if (skill == null) {
                    return;
                }
                skill.setId(Long.valueOf(req.getParameter("userSkillId")));
                profileService.updateSkill(userId, skill);
            } else {
                UserSkillDto skill = parseSkill(req, resp);
                if (skill == null) {
                    return;
                }
                profileService.addSkill(userId, skill);
            }
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (ProfileService.DuplicateSkillException e) {
            resp.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
            return;
        } catch (SQLException e) {
            throw new ServletException("기술 스택 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    // 유효성 검사 실패 시 400 응답을 직접 보내고 null을 반환한다.
    private UserSkillDto parseSkill(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawInput = req.getParameter("rawInput");
        if (rawInput == null || rawInput.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "기술명을 입력해주세요.");
            return null;
        }
        UserSkillDto skill = new UserSkillDto();
        skill.setRawInput(rawInput);
        skill.setProficiency(req.getParameter("proficiency"));
        return skill;
    }
}
