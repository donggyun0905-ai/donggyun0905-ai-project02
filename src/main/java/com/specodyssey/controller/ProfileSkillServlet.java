package com.specodyssey.controller;

import com.google.gson.Gson;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.service.ProfileInputChecker;
import com.specodyssey.service.ProfileService;
import com.specodyssey.service.RoadmapService;

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
 * GET ?check= : 입력하는 동안 이름 검사(엉터리 글자·오타 제안) — 결과는 JSON (2026-10-06)
 */
@WebServlet("/profile/skills")
public class ProfileSkillServlet extends HttpServlet {

    private static final Gson GSON = new Gson();

    private final ProfileService profileService = new ProfileService();
    private final RoadmapService roadmapService = new RoadmapService();
    private final ProfileInputChecker inputChecker = new ProfileInputChecker();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String check = req.getParameter("check");
        if (check == null) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        try {
            writeJson(resp, inputChecker.checkSkill(check));
        } catch (SQLException e) {
            throw new ServletException("기술명 확인 중 오류가 발생했습니다.", e);
        }
    }

    static void writeJson(HttpServletResponse resp, ProfileInputChecker.Result result) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        // 입력값마다 결과가 달라 저장할 가치가 없다
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(GSON.toJson(result));
    }

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
                if (skill == null || rejected(skill, resp)) {
                    return;
                }
                skill.setId(Long.valueOf(req.getParameter("userSkillId")));
                profileService.updateSkill(userId, skill);
            } else {
                UserSkillDto skill = parseSkill(req, resp);
                if (skill == null || rejected(skill, resp)) {
                    return;
                }
                profileService.addSkill(userId, skill);
                // 로드맵 SKILL 단계를 거치지 않고 프로필에서 직접 기술을 추가한 경우 — 일치하는
                // 미완료 SKILL 단계가 있으면 로드맵도 같이 완료 처리한다.
                roadmapService.syncSkillAddedFromProfile(userId, skill.getRawInput());
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

    // 화면의 검사를 거치지 않은 요청도 엉터리 이름은 저장하지 않는다
    private boolean rejected(UserSkillDto skill, HttpServletResponse resp) throws SQLException, IOException {
        if (!inputChecker.rejectsSkill(skill.getRawInput())) {
            return false;
        }
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "의미 없는 글자처럼 보여요. 기술명을 다시 확인해 주세요.");
        return true;
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
