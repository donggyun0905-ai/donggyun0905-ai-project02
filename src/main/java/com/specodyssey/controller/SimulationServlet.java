package com.specodyssey.controller;

import com.google.gson.Gson;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.simulation.SimulationRunner;
import com.specodyssey.service.simulation.SimulationService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 테스트 계정 시뮬레이션 — 오른쪽 위 패널(common/header.jsp, js/simulation.js)이 부른다.
 *   GET  /simulation               → 진행 상태 JSON (패널이 1~2초마다 확인)
 *   POST /simulation action=start target=점수 persona=DILIGENT|STEADY|ON_OFF|(비움=무작위)
 *                                 → ▶ 목표 점수까지 시작·이어 하기 (끝난 뒤 더 높은 목표면 이어서). 결과는 판마다 무작위
 *   POST /simulation action=pause  → ⏸ 일시정지
 *   POST /simulation action=reset  → ↺ 이 계정의 테스트 데이터 초기화
 *   POST /simulation action=follow on=true|false → 화면 따라가기 (켜면 하루 간격이 길어진다)
 * 항상 세션의 본인 계정에만 적용한다(다른 사용자 id를 받지 않는다). 테스트 계정이 아니면 403.
 * POST는 SecurityHeadersFilter가 X-CSRF-Token 헤더를 확인한다.
 */
@WebServlet("/simulation")
public class SimulationServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(SimulationServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final SimulationService simulationService = new SimulationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long userId = currentUserId(req);
        try {
            if (!simulationService.isTester(userId)) {
                writeJson(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("message", "테스트 계정만 쓸 수 있는 기능입니다."));
                return;
            }
            writeJson(resp, HttpServletResponse.SC_OK, statusBody(userId, null));
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "시뮬레이션 상태 조회 실패", e);
            writeJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Map.of("message", "상태를 불러오지 못했어요."));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long userId = currentUserId(req);
        String action = req.getParameter("action");
        try {
            if (!simulationService.isTester(userId)) {
                writeJson(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("message", "테스트 계정만 쓸 수 있는 기능입니다."));
                return;
            }
            String message;
            if ("start".equals(action)) {
                SimulationRunner.start(userId, parseTarget(req.getParameter("target")), req.getParameter("persona"));
                message = "시뮬레이션을 진행합니다.";
            } else if ("pause".equals(action)) {
                SimulationRunner.pause(userId);
                message = "지금 하루까지만 마치고 멈춥니다.";
            } else if ("follow".equals(action)) {
                boolean on = "true".equals(req.getParameter("on"));
                SimulationRunner.setFollow(userId, on);
                message = on ? "화면 따라가기를 켰어요. 바뀌는 화면으로 따라가며 천천히 진행합니다." : null;
            } else if ("reset".equals(action)) {
                Map<String, Integer> deleted = SimulationRunner.reset(userId);
                int total = deleted.values().stream().mapToInt(Integer::intValue).sum();
                message = "초기화했어요. 테스트 데이터 " + total + "건을 지웠습니다.";
            } else {
                writeJson(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("message", "잘못된 요청입니다."));
                return;
            }
            writeJson(resp, HttpServletResponse.SC_OK, statusBody(userId, message));
        } catch (SecurityException e) {
            writeJson(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            // 이미 끝남·희망 직무 없음 같은 안내
            writeJson(resp, HttpServletResponse.SC_CONFLICT, Map.of("message", e.getMessage()));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "시뮬레이션 처리 실패 (userId=" + userId + ", action=" + action + ")", e);
            writeJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Map.of("message", "처리하지 못했어요. 잠시 후 다시 눌러 주세요."));
        }
    }

    private Map<String, Object> statusBody(Long userId, String message) throws SQLException {
        SimulationService.Status s = simulationService.status(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", s.status());
        body.put("daysDone", s.daysDone());
        body.put("maxDays", s.maxDays());
        body.put("targetScore", s.targetScore());
        body.put("score", s.score());
        body.put("reached", s.reached());
        body.put("currentDate", s.currentDate());
        body.put("persona", s.persona());
        body.put("personaCode", s.personaCode());
        body.put("error", s.message());
        body.put("follow", SimulationRunner.isFollowing(userId));
        body.put("report", SimulationService.lastReport(userId)); // 마지막으로 돌린 하루 — 패널 한 줄 요약·화면 따라가기
        body.put("message", message);
        return body;
    }

    // 목표 점수 — 비었거나 숫자가 아니면 null (이어 하기는 원래 목표로, 처음 시작이면 서비스가 안내한다)
    private static Integer parseTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("목표 점수는 숫자로 넣어 주세요.");
        }
    }

    private static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(GSON.toJson(body));
    }

    private static Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = session == null ? null : (UserDto) session.getAttribute("loginUser");
        return loginUser == null ? null : loginUser.getId();
    }
}
