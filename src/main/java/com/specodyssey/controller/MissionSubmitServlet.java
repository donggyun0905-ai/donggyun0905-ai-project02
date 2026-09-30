package com.specodyssey.controller;

import com.specodyssey.dto.DailyMissionViewDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.CodeCompileService.Language;
import com.specodyssey.service.MissionSubmitService;
import com.specodyssey.service.MissionSubmitService.SubmitResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 일일 미션 "정답 입력하기" — 풀이 코드 입력 화면(GET)과 제출(POST).
 * 관련 요구사항: FR-53
 * 컴파일 확인을 통과하면 저장 후 /mission으로 돌아가고, 실패하면 입력한 코드를 그대로 둔 채 오류를 보여 준다.
 */
@WebServlet("/mission/submit")
public class MissionSubmitServlet extends HttpServlet {

    private static final String VIEW = "/WEB-INF/views/mission-submit.jsp";

    private final MissionSubmitService submitService = new MissionSubmitService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long missionId = parseId(req.getParameter("missionId"));
        DailyMissionViewDto mission = missionId == null ? null : findOwnMission(req, missionId);
        if (mission == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "미션을 찾을 수 없습니다.");
            return;
        }
        render(req, resp, mission, MissionSubmitService.defaultLanguage(mission).name(), mission.getSubmittedCode());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long missionId = parseId(req.getParameter("missionId"));
        DailyMissionViewDto mission = missionId == null ? null : findOwnMission(req, missionId);
        if (mission == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "미션을 찾을 수 없습니다.");
            return;
        }
        String language = req.getParameter("language");
        String code = req.getParameter("code");

        SubmitResult result;
        try {
            result = submitService.submit(userId(req), missionId, language, code);
        } catch (SQLException e) {
            throw new ServletException("풀이 코드 저장 중 오류가 발생했습니다.", e);
        }
        if (result.isSaved()) {
            resp.sendRedirect(req.getContextPath() + "/mission");
            return;
        }
        if (result.status() == MissionSubmitService.Status.COMPILE_ERROR) {
            req.setAttribute("compileError", result.message());
        } else {
            req.setAttribute("errorMessage", result.message());
        }
        render(req, resp, mission, language, code);
    }

    private void render(HttpServletRequest req, HttpServletResponse resp, DailyMissionViewDto mission,
                        String language, String code) throws ServletException, IOException {
        req.setAttribute("mission", mission);
        req.setAttribute("languages", Language.values());
        req.setAttribute("selectedLanguage", language);
        req.setAttribute("code", code);
        req.getRequestDispatcher(VIEW).forward(req, resp);
    }

    private DailyMissionViewDto findOwnMission(HttpServletRequest req, Long missionId) throws ServletException {
        try {
            return submitService.findOwnMission(userId(req), missionId);
        } catch (SQLException e) {
            throw new ServletException("미션 조회 중 오류가 발생했습니다.", e);
        }
    }

    // SessionFilter가 로그인을 보장하는 경로라 세션의 loginUser가 항상 있다
    private static Long userId(HttpServletRequest req) {
        return ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
    }

    private static Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
