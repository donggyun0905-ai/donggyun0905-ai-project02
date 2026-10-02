package com.specodyssey.controller;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.DocumentService;
import com.specodyssey.util.FileStorageUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 서류 보관함 — 목록·올리기·연결 프로젝트 변경·삭제. 관련 요구사항: FR-61~63
 * 정확히 "/documents"만 여기서 받는다 — "/documents/{id}" 내려받기는 DocumentDownloadServlet("/documents/*")이
 * 소유자 확인 후 처리한다(겹치지 않는다). 결과 문구는 세션에 한 번만 담았다가 다음 GET에서 보여준다.
 */
@WebServlet("/documents")
@MultipartConfig(
        maxFileSize = DocumentService.MAX_FILE_BYTES,
        maxRequestSize = DocumentService.MAX_FILE_BYTES + 1024 * 1024,
        fileSizeThreshold = 0
)
public class DocumentsManageServlet extends HttpServlet {

    private static final String MESSAGE_KEY = "documentsMessage";
    private static final String ERROR_KEY = "documentsError";

    private final DocumentService documentService = new DocumentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        try {
            req.setAttribute("documents", documentService.listViews(userId));
            req.setAttribute("projects", documentService.listProjects(userId));
        } catch (SQLException e) {
            throw new ServletException("서류 목록을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.setAttribute("allowedHint", DocumentService.ALLOWED_HINT);
        HttpSession session = req.getSession(false);
        for (String key : new String[] {MESSAGE_KEY, ERROR_KEY}) {
            Object value = session.getAttribute(key);
            if (value != null) {
                session.removeAttribute(key);
                req.setAttribute(key, value);
            }
        }
        req.getRequestDispatcher("/WEB-INF/views/documents-manage.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = currentUserId(req);
        String action = req.getParameter("action");
        HttpSession session = req.getSession(false);

        try {
            if ("delete".equals(action)) {
                boolean removed = documentService.delete(userId, parseId(req.getParameter("documentId")));
                if (removed) {
                    session.setAttribute(MESSAGE_KEY, "서류를 삭제했습니다.");
                } else {
                    session.setAttribute(ERROR_KEY, "삭제할 서류를 찾을 수 없습니다.");
                }
            } else if ("link".equals(action)) {
                boolean changed = documentService.changeProject(userId, parseId(req.getParameter("documentId")),
                        parseOptionalId(req.getParameter("projectId")));
                session.setAttribute(changed ? MESSAGE_KEY : ERROR_KEY,
                        changed ? "연결 프로젝트를 바꿨습니다." : "바꿀 서류를 찾을 수 없습니다.");
            } else {
                String error = upload(req, userId);
                session.setAttribute(error == null ? MESSAGE_KEY : ERROR_KEY, error == null ? "서류를 올렸습니다." : error);
            }
        } catch (IllegalArgumentException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("서류 처리 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/documents");
    }

    // 실패하면 화면에 보여줄 문구를, 성공하면 null을 돌려준다.
    private String upload(HttpServletRequest req, Long userId) throws IOException, ServletException, SQLException {
        Part part;
        try {
            part = req.getPart("file");
        } catch (IllegalStateException e) {
            return "파일 용량이 너무 큽니다 (20MB 이하).";
        }
        String originalName = part == null ? null : part.getSubmittedFileName();
        if (originalName == null || originalName.isBlank() || part.getSize() == 0) {
            return "올릴 파일을 선택해주세요.";
        }
        if (!DocumentService.isAllowedFile(originalName)) {
            return "올릴 수 없는 파일 형식입니다. (" + DocumentService.ALLOWED_HINT + ")";
        }
        Long projectId = parseOptionalId(req.getParameter("projectId"));

        FileStorageUtil.SavedFile saved;
        try (var in = part.getInputStream()) {
            saved = FileStorageUtil.save(in, originalName);
        }
        DocumentDto document = new DocumentDto();
        document.setOriginalName(originalName);
        document.setStoredName(saved.getStoredName());
        document.setFilePath(saved.getFilePath());
        document.setFileSize(saved.getFileSize());
        document.setMimeType(FileStorageUtil.mimeTypeFor(originalName));
        document.setChecksum(saved.getChecksum());
        try {
            documentService.upload(userId, document, projectId);
        } catch (SQLException | RuntimeException e) {
            // DB에 등록하지 못했으면 방금 쓴 파일을 남겨 두지 않는다
            FileStorageUtil.deleteQuietly(saved.getFilePath());
            if (e instanceof IllegalArgumentException) {
                return e.getMessage();
            }
            throw e;
        }
        return null;
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 요청입니다.");
        }
    }

    // 비어 있으면(= "연결 안 함") null
    private Long parseOptionalId(String value) {
        return value == null || value.isBlank() ? null : parseId(value);
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return ((UserDto) session.getAttribute("loginUser")).getId();
    }
}
