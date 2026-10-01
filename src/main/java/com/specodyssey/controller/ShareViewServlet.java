package com.specodyssey.controller;

import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.EvaluationCompareService;
import com.specodyssey.service.ShareViewService;
import com.specodyssey.util.FileStorageUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;

/**
 * 면접관 뷰(로그인 없이 링크로만 접근). 관련 요구사항: FR-81~86
 * 화면설계 PDF "13. 면접관 뷰", "14. 면접관 비교 뷰", "6-4. 유효하지 않은 공유 링크" 기준.
 * "/share/*"는 SessionFilter의 PUBLIC_PREFIXES에 있어 로그인 없이 열린다(FR-14 면접관은 계정이 없음).
 * "/share/{token}"은 지원자 이력(ShareViewService). 접근 제어는 ShareViewService가 토큰·활성·만료·공개 범위로 대신한다.
 * 비교(FR-82·83)는 두 갈래다 — 로그인한 면접관 계정은 InterviewerServlet "/interviewer/compare"로 보내고,
 * 계정 없이 들어온 접속은 "/share/compare"(EvaluationCompareService)에서 쿠키의 session_token을
 * 소유 증명으로 쓴다(EVALUATION_SESSION.user_id가 NULL인 익명 세션).
 */
@WebServlet("/share/*")
public class ShareViewServlet extends HttpServlet {

    private static final String SESSION_COOKIE_NAME = "evalSessionToken";
    private static final int SESSION_COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60; // 30일
    private static final String RESUME_SUFFIX = "/resume";

    private final ShareViewService shareViewService = new ShareViewService();
    private final EvaluationCompareService evaluationCompareService = new EvaluationCompareService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        // 비교: 로그인한 면접관 계정은 계정 화면으로, 계정 없는 접속은 쿠키 세션 비교(익명)로 처리한다.
        if ("/compare".equals(pathInfo)) {
            UserDto compareUser = (UserDto) (req.getSession(false) == null
                    ? null : req.getSession(false).getAttribute("loginUser"));
            if (compareUser != null && RoleFilter.INTERVIEWER.equals(compareUser.getUserType())) {
                resp.sendRedirect(req.getContextPath() + "/interviewer/compare");
                return;
            }
            handleCompareGet(req, resp);
            return;
        }

        // "/share/{토큰}/resume" — 이력서 파일 내려받기
        if (pathInfo != null && pathInfo.endsWith(RESUME_SUFFIX)) {
            downloadResume(resp, pathInfo.substring(1, pathInfo.length() - RESUME_SUFFIX.length()));
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
        // 비교 목록에 담기는 면접관 계정만 할 수 있다 — 열람 자체는 로그인 없이도 된다(FR-85)
        req.setAttribute("token", token);
        req.setAttribute("interviewer", loginUser != null && RoleFilter.INTERVIEWER.equals(loginUser.getUserType()));
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
        req.getRequestDispatcher("/WEB-INF/views/share-compare.jsp").forward(req, resp);
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

    // 지원자가 이 링크에 이력서 공개를 고른 경우에만 내려준다 — 조건 확인은 ShareViewService.loadResume이 한다.
    // 받을 수 없는 경우는 이유를 구분하지 않고 404로 답한다(링크가 유효한지 떠볼 단서를 주지 않는다).
    private void downloadResume(HttpServletResponse resp, String token) throws ServletException, IOException {
        DocumentDto resume;
        try {
            resume = shareViewService.loadResume(token);
        } catch (SQLException e) {
            throw new ServletException("이력서를 불러오는 중 오류가 발생했습니다.", e);
        }
        // 업로드 폴더는 서버 PC마다 따로라, DB에는 있는데 이 서버에는 파일이 없을 수 있다
        if (resume == null || !Files.isRegularFile(Paths.get(resume.getFilePath()))) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Robots-Tag", "noindex, nofollow");
        resp.setContentType(resume.getMimeType() != null ? resume.getMimeType() : "application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(resume.getOriginalName(), StandardCharsets.UTF_8));
        FileStorageUtil.writeTo(resume.getFilePath(), resp.getOutputStream());
    }
}
