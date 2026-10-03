package com.specodyssey.controller;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.SurveyQuestionDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.discovery.JobDiscoveryService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 직무 찾기(설문 → 후보 → 선택). 관련 요구사항: FR-34 · 38 · 39
 * 화면설계 PDF "7. 직무 발굴" 기준. 담당: C(직무 발굴).
 *
 * GET                     설문 문항 + (있으면) 이전 응답과 추천 후보
 * POST action=survey      설문 제출 → 추천 계산·저장 → GET으로 리다이렉트 (새로고침 중복 제출 방지)
 * POST action=select      후보 선택 → /gap-analysis?jobId=… 로 이동 (FR-39 막다른 길 방지)
 */
@WebServlet("/job-discovery")
public class JobDiscoveryServlet extends HttpServlet {

    private final JobDiscoveryService discoveryService = new JobDiscoveryService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = loginUserId(req);
        // 설문 전에 다른 화면에 가려다 넘어온 경우의 안내 — 한 번만 보여 준다
        Object notice = req.getSession(false).getAttribute(OnboardingFilter.NOTICE_KEY);
        if (notice != null) {
            req.setAttribute("onboardingNotice", notice);
            req.getSession(false).removeAttribute(OnboardingFilter.NOTICE_KEY);
        }
        try {
            req.setAttribute("questions", discoveryService.getQuestions());
            req.setAttribute("myAnswers", discoveryService.getMyAnswers(userId));
            req.setAttribute("recommendations", discoveryService.getRecommendations(userId));
        } catch (SQLException e) {
            throw new ServletException("직무 찾기 화면을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/job-discovery.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = loginUserId(req);
        String action = req.getParameter("action");

        try {
            if ("select".equals(action)) {
                Long recommendationId = Long.valueOf(req.getParameter("recommendationId"));
                Long jobId = discoveryService.selectRecommendation(userId, recommendationId);
                if (jobId == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "추천 후보를 찾을 수 없습니다.");
                    return;
                }
                // 희망 직무가 DB에서 바뀌었으니 세션 사본도 맞춘다 — 안 하면 다시 로그인할 때까지 트렌드 위젯 등이
                // 예전(비어 있던) 희망 직무로 동작한다(2026-10-03 발견).
                refreshSessionUser(req, userId);
                resp.sendRedirect(req.getContextPath() + "/gap-analysis?jobId=" + jobId);
                return;
            }

            Map<Long, Integer> answers = new HashMap<>();
            for (SurveyQuestionDto q : discoveryService.getQuestions()) {
                String v = req.getParameter("q" + q.getId());
                if (v != null && !v.isBlank()) {
                    answers.put(q.getId(), Integer.valueOf(v));
                }
            }
            discoveryService.submitSurvey(userId, answers);
            resp.sendRedirect(req.getContextPath() + "/job-discovery#results");
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
        } catch (JobDiscoveryService.InvalidSurveyException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("직무 추천 처리 중 오류가 발생했습니다.", e);
        }
    }

    // 세션에 들고 있는 사본도 최신화 (비밀번호 해시는 세션에 두지 않는다) — ProfileServlet과 같은 방식
    private void refreshSessionUser(HttpServletRequest req, Long userId) throws SQLException {
        UserDto refreshed = new UserDao().findById(userId);
        if (refreshed != null) {
            refreshed.setPasswordHash(null);
            refreshed.setRecoveryCodeHash(null);
            req.getSession().setAttribute("loginUser", refreshed);
        }
    }

    // SessionFilter가 로그인을 보장하므로 여기서는 세션이 있다고 본다.
    private Long loginUserId(HttpServletRequest req) {
        return ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
    }
}
