package com.specodyssey.controller;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.service.AdminAuditService;
import com.specodyssey.service.AdminReferenceService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 관리자 기준 데이터 관리 화면(2026-10-06) — 직무·기술·자격증·기술 별칭·직무별 요구 기술.
 * 탭(tab 파라미터)으로 다섯 영역을 오간다. 사용자 개인 데이터는 범위 밖(AdminUserServlet 참고).
 */
@WebServlet("/admin/reference")
public class AdminReferenceServlet extends HttpServlet {

    static final String MESSAGE_KEY = "adminMessage";
    static final String ERROR_KEY = "adminError";
    private static final String DEFAULT_TAB = "jobs";

    private final AdminReferenceService referenceService = new AdminReferenceService();
    private final AdminAuditService auditService = new AdminAuditService();
    private final SkillDao skillDao = new SkillDao();
    private final JobDao jobDao = new JobDao();

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
        String tab = req.getParameter("tab");
        if (tab == null || tab.isBlank()) {
            tab = DEFAULT_TAB;
        }
        req.setAttribute("tab", tab);
        try {
            switch (tab) {
                case "skills" -> req.setAttribute("skills", referenceService.listSkills());
                case "certifications" -> req.setAttribute("certifications", referenceService.listCertifications());
                case "aliases" -> {
                    req.setAttribute("aliases", referenceService.listSkillAliases());
                    req.setAttribute("allSkills", skillDao.findAll());
                }
                case "requirements" -> {
                    req.setAttribute("allJobs", jobDao.findAll());
                    req.setAttribute("allSkills", skillDao.findAll());
                    String jobIdParam = req.getParameter("jobId");
                    if (jobIdParam != null && !jobIdParam.isBlank()) {
                        Long jobId = Long.valueOf(jobIdParam);
                        req.setAttribute("selectedJobId", jobId);
                        req.setAttribute("requirements", referenceService.listRequiredSkills(jobId));
                    }
                }
                default -> req.setAttribute("jobs", referenceService.listJobs());
            }
        } catch (SQLException e) {
            throw new ServletException("기준 데이터를 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/admin-reference.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "관리자 전용 화면입니다.");
            return;
        }
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession();
        String action = req.getParameter("action");
        String tab = req.getParameter("tab");
        try {
            handle(req, action);
            // 기준 데이터는 액션 종류가 많아 action 이름을 detail에 적어 어느 표를 고쳤는지 남긴다
            auditService.record(AdminSession.loginUser(req),
                    action != null && action.startsWith("delete")
                            ? AdminAuditService.REFERENCE_DELETE : AdminAuditService.REFERENCE_SAVE,
                    "REFERENCE", null, "탭 " + req.getParameter("tab") + " · " + action);
            session.setAttribute(MESSAGE_KEY, "저장했습니다.");
        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("기준 데이터를 저장하는 중 오류가 발생했습니다.", e);
        }
        String jobIdParam = req.getParameter("jobId");
        String extra = "requirements".equals(tab) && jobIdParam != null && !jobIdParam.isBlank()
                ? "&jobId=" + jobIdParam : "";
        resp.sendRedirect(req.getContextPath() + "/admin/reference?tab=" + tab + extra);
    }

    private void handle(HttpServletRequest req, String action) throws SQLException {
        switch (action) {
            case "saveJob" -> referenceService.saveJob(parseLong(req.getParameter("id")),
                    req.getParameter("jobName"), blankToNull(req.getParameter("jobCategory")),
                    req.getParameter("popular") != null);
            case "saveSkill" -> referenceService.saveSkill(parseLong(req.getParameter("id")),
                    req.getParameter("skillName"), blankToNull(req.getParameter("category")));
            case "saveCertification" -> referenceService.saveCertification(parseLong(req.getParameter("id")),
                    req.getParameter("certName"), blankToNull(req.getParameter("issuer")),
                    blankToNull(req.getParameter("jobCategory")), parseInt(req.getParameter("difficultyLevel")));
            case "addAlias" -> referenceService.addSkillAlias(parseLong(req.getParameter("skillId")),
                    req.getParameter("aliasName"));
            case "deleteAlias" -> referenceService.deleteSkillAlias(parseLong(req.getParameter("id")));
            case "addRequirement" -> referenceService.addRequiredSkill(parseLong(req.getParameter("jobId")),
                    parseLong(req.getParameter("skillId")), req.getParameter("importance"),
                    blankToNull(req.getParameter("requiredLevel")));
            case "updateRequirement" -> referenceService.updateRequiredSkill(parseLong(req.getParameter("id")),
                    req.getParameter("importance"), blankToNull(req.getParameter("requiredLevel")));
            case "deleteRequirement" -> referenceService.deleteRequiredSkill(parseLong(req.getParameter("id")));
            default -> throw new IllegalArgumentException("잘못된 요청입니다.");
        }
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
