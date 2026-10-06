package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.RoadmapService;
import com.specodyssey.service.discovery.JobDiscoveryService;
import com.specodyssey.util.AiNotices;
import com.specodyssey.util.AiNotices.RetryTarget;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * AI 대체 안내 배너의 "다시 시도" 버튼 (FR-111, 2026-10-06). POST만 받고, 로그인한 본인 데이터만 다룬다.
 *
 * - DISCOVERY: 저장된 설문 답변으로 다시 제출해 추천 이유를 다시 받는다 → /job-discovery#results
 * - ROADMAP_PROJECT: 기본 문구로 대체된 프로젝트 단계의 문구만 다시 받는다 → /roadmap
 *   ("로드맵 다시 만들기"는 바뀐 부분만 반영해서 이미 있는 프로젝트 단계를 다시 만들지 않는다)
 * 또 실패하면 서비스가 안내를 다시 남기므로 다음 화면에 배너가 다시 뜬다.
 * 연타로 LLM 한도를 쓰지 않게 사용자마다 짧은 간격을 둔다.
 */
@WebServlet("/ai-retry")
public class AiRetryServlet extends HttpServlet {

    static final long MIN_INTERVAL_MS = 10_000;
    static final String TOO_SOON_NOTICE = "방금 다시 시도했어요. 잠시 후 다시 눌러 주세요.";
    private static final String LAST_RETRY_KEY = "aiRetryLastAt";

    private final JobDiscoveryService discoveryService = new JobDiscoveryService();
    private final RoadmapService roadmapService = new RoadmapService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        RetryTarget target = parseTarget(req.getParameter("target"));
        if (target == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        }
        String next = req.getContextPath() + (target == RetryTarget.DISCOVERY ? "/job-discovery#results" : "/roadmap");
        HttpSession session = req.getSession(false);
        if (!allowNow(session, System.currentTimeMillis())) {
            AiNotices.add(TOO_SOON_NOTICE, target);
            resp.sendRedirect(next);
            return;
        }
        Long userId = ((UserDto) session.getAttribute("loginUser")).getId();
        try {
            if (target == RetryTarget.DISCOVERY) {
                Map<Long, Integer> answers = discoveryService.getMyAnswers(userId);
                if (answers.isEmpty()) {
                    resp.sendRedirect(req.getContextPath() + "/job-discovery");
                    return;
                }
                discoveryService.submitSurvey(userId, answers);
            } else {
                roadmapService.retryProjectIdea(userId);
            }
        } catch (JobDiscoveryService.InvalidSurveyException e) {
            // 그 사이 새 문항이 추가돼 저장된 답만으로는 제출할 수 없다 — 설문 화면에서 다시 답하게 한다
            resp.sendRedirect(req.getContextPath() + "/job-discovery");
            return;
        } catch (SQLException e) {
            throw new ServletException("AI 다시 시도 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(next);
    }

    static RetryTarget parseTarget(String value) {
        if (value == null) {
            return null;
        }
        try {
            return RetryTarget.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 마지막 시도에서 MIN_INTERVAL_MS가 지났으면 true를 돌려주고 시각을 기록한다
    static boolean allowNow(HttpSession session, long now) {
        if (session.getAttribute(LAST_RETRY_KEY) instanceof Long last && now - last < MIN_INTERVAL_MS) {
            return false;
        }
        session.setAttribute(LAST_RETRY_KEY, now);
        return true;
    }
}
