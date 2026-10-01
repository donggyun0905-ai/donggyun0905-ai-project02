package com.specodyssey.controller;

import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.EvaluationCompareService;
import com.specodyssey.service.ShareViewService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 면접관 뷰(로그인 없이 링크로만 접근). 관련 요구사항: FR-81~86
 * 화면설계 PDF "13. 면접관 뷰", "14. 면접관 비교 뷰", "6-4. 유효하지 않은 공유 링크" 기준.
 * "/share/*"는 SessionFilter의 PUBLIC_PREFIXES에 있어 로그인 없이 열린다(FR-14 면접관은 계정이 없음).
 * "/share/{token}"은 지원자 이력(ShareViewService), "/share/compare"는 비교(장바구니,
 * EvaluationCompareService) — 면접관도 계정이 없어 쿠키의 session_token이 소유 증명을
 * 대신한다(EVALUATION_SESSION, FR-82).
 */
@WebServlet("/share/*")
public class ShareViewServlet extends HttpServlet {

    private static final String SESSION_COOKIE_NAME = "evalSessionToken";
    private static final int SESSION_COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60; // 30일

    private final ShareViewService shareViewService = new ShareViewService();
    private final EvaluationCompareService evaluationCompareService = new EvaluationCompareService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        if ("/compare".equals(pathInfo)) {
            handleCompareGet(req, resp);
            return;
        }

        String token = (pathInfo == null || pathInfo.length() < 2) ? "" : pathInfo.substring(1);
        HttpSession session = req.getSession(false);
        UserDto loginUser = session == null ? null : (UserDto) session.getAttribute("loginUser");
        ShareViewDto view;
        try {
            view = shareViewService.loadView(token, req.getRemoteAddr(), loginUser == null ? null : loginUser.getId());
        } catch (SQLException e) {
            throw new ServletException("공유 이력을 불러오는 중 오류가 발생했습니다.", e);
        }

        // 주소에 토큰이 들어 있으므로 캐시·검색 수집·외부 사이트로의 Referer 전달을 막는다
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Robots-Tag", "noindex, nofollow");
        resp.setHeader("Referrer-Policy", "no-referrer");

        req.setAttribute("valid", view != null);
        req.setAttribute("view", view);
        req.setAttribute("token", token);
        req.getRequestDispatcher("/WEB-INF/views/interviewer-view.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!"/compare".equals(req.getPathInfo())) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        }

        EvaluationSessionDto session;
        try {
            session = resolveSession(req, resp);
            String action = req.getParameter("action");
            if ("addCandidate".equals(action)) {
                evaluationCompareService.addCandidate(session.getId(), req.getParameter("linkInput"));
            } else if ("removeCandidate".equals(action)) {
                evaluationCompareService.removeCandidate(Long.valueOf(req.getParameter("itemId")),
                        session.getSessionToken());
            } else if ("addCriterion".equals(action)) {
                int weight = Integer.parseInt(req.getParameter("weight"));
                evaluationCompareService.addOrUpdateCriterion(session.getId(), session.getSessionToken(),
                        req.getParameter("skillName"), weight);
            } else if ("removeCriterion".equals(action)) {
                evaluationCompareService.removeCriterion(Long.valueOf(req.getParameter("criteriaId")),
                        session.getSessionToken());
            } else if ("renameSession".equals(action)) {
                evaluationCompareService.renameSession(session.getId(), session.getSessionToken(),
                        req.getParameter("companyName"));
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
        } catch (IllegalArgumentException e) {
            req.setAttribute("errorMessage", e.getMessage());
            handleCompareGet(req, resp);
            return;
        } catch (SQLException e) {
            throw new ServletException("비교 세션 처리 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/share/compare");
    }

    private void handleCompareGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            EvaluationSessionDto session = resolveSession(req, resp);
            req.setAttribute("compareView", evaluationCompareService.buildCompareView(
                    session.getId(), session.getSessionToken()));
        } catch (SQLException e) {
            throw new ServletException("비교 목록을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/interviewer-compare.jsp").forward(req, resp);
    }

    // 쿠키의 세션 토큰을 재사용하거나, 없거나 만료됐으면 새로 만들어 쿠키를 다시 심는다.
    private EvaluationSessionDto resolveSession(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException {
        String existingToken = readCookie(req, SESSION_COOKIE_NAME);
        EvaluationSessionDto session = evaluationCompareService.getOrCreateSession(existingToken);
        if (!session.getSessionToken().equals(existingToken)) {
            Cookie cookie = new Cookie(SESSION_COOKIE_NAME, session.getSessionToken());
            cookie.setPath(req.getContextPath() + "/share");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(SESSION_COOKIE_MAX_AGE_SECONDS);
            resp.addCookie(cookie);
        }
        return session;
    }

    private String readCookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : req.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
