package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ScoringRuleAdminService;
import com.specodyssey.util.AdminAccess;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 화면 — 점수·복습 주기 규칙(SCORING_RULE)을 보고 고친다. 직무 기술 트렌드 재집계는
 * /admin/job-skill-trend 에 있고 두 화면은 위쪽 탭으로 오간다. 관리자 판단은 AdminAccess(로그인 아이디 기준).
 * 값을 저장하면 서비스가 규칙 캐시를 비우지만, 다른 서버 인스턴스에는 최대 1분 안에 반영된다.
 */
@WebServlet("/admin")
public class AdminServlet extends HttpServlet {

    static final String MESSAGE_KEY = "adminMessage";
    static final String ERROR_KEY = "adminError";
    private static final String RULE_PARAM_PREFIX = "rule_";

    private final ScoringRuleAdminService ruleService = new ScoringRuleAdminService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        HttpSession session = req.getSession(false);
        // 저장 직후 리다이렉트로 돌아온 한 번만 보여 준다
        for (String key : new String[] {MESSAGE_KEY, ERROR_KEY}) {
            Object value = session == null ? null : session.getAttribute(key);
            if (value != null) {
                req.setAttribute(key, value);
                session.removeAttribute(key);
            }
        }
        try {
            req.setAttribute("ruleGroups", ruleService.listByGroup());
        } catch (SQLException e) {
            throw new ServletException("규칙을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/admin.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        Map<String, String> submitted = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : req.getParameterMap().entrySet()) {
            if (e.getKey().startsWith(RULE_PARAM_PREFIX) && e.getValue().length > 0) {
                submitted.put(e.getKey().substring(RULE_PARAM_PREFIX.length()), e.getValue()[0]);
            }
        }
        HttpSession session = req.getSession();
        try {
            int changed = ruleService.save(submitted);
            session.setAttribute(MESSAGE_KEY, changed == 0 ? "바뀐 값이 없습니다." : changed + "개 규칙을 저장했습니다.");
        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("규칙을 저장하는 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/admin");
    }

    private static boolean isAdmin(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto user = session == null ? null : (UserDto) session.getAttribute("loginUser");
        return AdminAccess.isAdmin(user);
    }
}
