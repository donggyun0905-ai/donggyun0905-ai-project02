package com.specodyssey.controller;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.AdminAuditService;
import com.specodyssey.service.AdminRoadmapService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 로드맵 수정 화면(2026-10-06) — 아이디로 사용자를 찾아 대표 로드맵의 단계별 완료 표시를
 * 고치거나(점수는 안 건드림), 잘못 생성된 미완료 단계를 지운다.
 */
@WebServlet("/admin/roadmap")
public class AdminRoadmapServlet extends HttpServlet {

    static final String MESSAGE_KEY = "adminMessage";
    static final String ERROR_KEY = "adminError";

    private final AdminRoadmapService adminRoadmapService = new AdminRoadmapService();
    private final AdminAuditService auditService = new AdminAuditService();
    private final SkillDao skillDao = new SkillDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        HttpSession session = req.getSession(false);
        for (String key : new String[] {MESSAGE_KEY, ERROR_KEY}) {
            Object value = session == null ? null : session.getAttribute(key);
            if (value != null) {
                req.setAttribute(key, value);
                session.removeAttribute(key);
            }
        }
        String loginId = req.getParameter("loginId");
        try {
            if (loginId != null && !loginId.isBlank()) {
                loadRoadmapFor(req, loginId.trim());
            }
        } catch (SQLException e) {
            throw new ServletException("로드맵을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.setAttribute("loginId", loginId);
        req.getRequestDispatcher("/WEB-INF/views/admin-roadmap.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        HttpSession session = req.getSession();
        String action = req.getParameter("action");
        String loginId = req.getParameter("loginId");
        try {
            Long userId = Long.valueOf(req.getParameter("userId"));
            Long stepId = Long.valueOf(req.getParameter("stepId"));
            if ("complete".equals(action)) {
                adminRoadmapService.setCompleted(stepId, userId, true);
                auditService.record(AdminSession.loginUser(req), AdminAuditService.ROADMAP_STEP_COMPLETE,
                        "ROADMAP_STEP", stepId, "대상 회원 id " + userId);
                session.setAttribute(MESSAGE_KEY, "완료로 바꿨습니다(점수는 변경되지 않습니다).");
            } else if ("uncomplete".equals(action)) {
                adminRoadmapService.setCompleted(stepId, userId, false);
                auditService.record(AdminSession.loginUser(req), AdminAuditService.ROADMAP_STEP_UNCOMPLETE,
                        "ROADMAP_STEP", stepId, "대상 회원 id " + userId);
                session.setAttribute(MESSAGE_KEY, "미완료로 바꿨습니다(점수는 변경되지 않습니다).");
            } else if ("delete".equals(action)) {
                adminRoadmapService.deleteIncompleteStep(stepId, userId);
                auditService.record(AdminSession.loginUser(req), AdminAuditService.ROADMAP_STEP_DELETE,
                        "ROADMAP_STEP", stepId, "대상 회원 id " + userId);
                session.setAttribute(MESSAGE_KEY, "단계를 지웠습니다.");
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("로드맵 처리 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/admin/roadmap?loginId=" + java.net.URLEncoder.encode(
                loginId == null ? "" : loginId, java.nio.charset.StandardCharsets.UTF_8));
    }

    private void loadRoadmapFor(HttpServletRequest req, String loginId) throws SQLException {
        UserDto user = userDao.findByLoginId(loginId);
        if (user == null) {
            req.setAttribute("adminError", "사용자를 찾을 수 없습니다.");
            return;
        }
        req.setAttribute("targetUser", user);
        RoadmapDto roadmap = adminRoadmapService.findPrimaryRoadmap(user.getId());
        if (roadmap == null) {
            req.setAttribute("adminError", "이 사용자는 아직 생성된 로드맵이 없습니다.");
            return;
        }
        req.setAttribute("roadmap", roadmap);
        List<RoadmapStepDto> steps = adminRoadmapService.listSteps(roadmap.getId());
        req.setAttribute("steps", steps);
        req.setAttribute("stepLabels", buildLabels(steps));
    }

    // 화면에 "기술명" 또는 "자격증명"을 보여주려고 단계마다 한 줄 설명을 미리 만든다(JSP는 계산하지 않는다).
    private List<String> buildLabels(List<RoadmapStepDto> steps) throws SQLException {
        List<String> labels = new ArrayList<>();
        for (RoadmapStepDto step : steps) {
            if (step.getRelatedSkillId() != null) {
                SkillDto skill = skillDao.findById(step.getRelatedSkillId());
                labels.add(skill == null ? "(삭제된 기술)" : skill.getSkillName());
            } else if (step.getCertificationId() != null) {
                CertificationDto cert = certificationDao.findById(step.getCertificationId());
                labels.add(cert == null ? "(삭제된 자격증)" : cert.getCertName());
            } else {
                labels.add("—");
            }
        }
        return labels;
    }

}
