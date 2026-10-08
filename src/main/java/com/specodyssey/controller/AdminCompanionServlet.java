package com.specodyssey.controller;

import com.specodyssey.dto.CompanionReleaseDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.AdminAuditService;
import com.specodyssey.service.companion.CompanionReleaseService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 관리자 · 데스크톱 캐릭터 — 설치 파일(Setup.exe) 새 버전 올리기. 올린 파일은 DB에 나눠 저장되고(CompanionReleaseService),
 * 사이트 [내려받기]와 캐릭터 [업데이트]가 바로 이 파일을 쓴다. 관리자만 (AdminSession).
 */
@WebServlet("/admin/companion")
@MultipartConfig(
        maxFileSize = CompanionReleaseService.MAX_FILE_BYTES,
        maxRequestSize = CompanionReleaseService.MAX_FILE_BYTES + 1024 * 1024,
        fileSizeThreshold = 1024 * 1024
)
public class AdminCompanionServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AdminCompanionServlet.class.getName());
    private static final String MESSAGE_KEY = "adminMessage";
    private static final String ERROR_KEY = "adminError";

    private final CompanionReleaseService releaseService = new CompanionReleaseService();
    private final AdminAuditService auditService = new AdminAuditService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        HttpSession session = req.getSession(false);
        for (String key : new String[]{MESSAGE_KEY, ERROR_KEY}) {
            Object value = session == null ? null : session.getAttribute(key);
            if (value != null) {
                req.setAttribute(key, value);
                session.removeAttribute(key);
            }
        }
        try {
            req.setAttribute("releases", releaseService.active());
        } catch (SQLException e) {
            throw new ServletException("캐릭터 버전 목록을 불러오지 못했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/admin-companion.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AdminSession.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        HttpSession session = req.getSession();
        UserDto admin = AdminSession.loginUser(req);
        try {
            Part file = req.getPart("file");
            if (file == null || file.getSize() == 0) {
                throw new IllegalArgumentException("설치 파일(Setup.exe)을 골라 주세요.");
            }
            String name = file.getSubmittedFileName();
            if (name == null || !name.toLowerCase().endsWith(".exe")) {
                throw new IllegalArgumentException("build-companion.ps1이 만든 SpecOdysseyCompanion-Setup.exe를 올려 주세요.");
            }
            CompanionReleaseDto saved;
            try (InputStream in = file.getInputStream()) {
                saved = releaseService.upload(admin.getId(), req.getParameter("version"), req.getParameter("notes"),
                        "SpecOdysseyCompanion-Setup.exe", in);
            }
            auditService.record(admin, AdminAuditService.COMPANION_RELEASE_UPLOAD, "COMPANION_RELEASE", saved.getId(),
                    saved.getVersion() + " · " + saved.getSizeText());
            session.setAttribute(MESSAGE_KEY, "캐릭터 " + saved.getVersion() + " 버전을 올렸어요. 설치된 캐릭터는 곧 업데이트 안내를 띄워요.");
        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());
        } catch (IllegalStateException e) {
            session.setAttribute(ERROR_KEY, "파일이 너무 커요 (200MB 이하).");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "캐릭터 설치 파일 올리기 실패", e);
            session.setAttribute(ERROR_KEY, "올리지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
        resp.sendRedirect(req.getContextPath() + "/admin/companion");
    }
}
