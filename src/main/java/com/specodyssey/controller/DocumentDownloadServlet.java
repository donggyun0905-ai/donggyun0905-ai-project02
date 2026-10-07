package com.specodyssey.controller;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.service.DocumentContentService;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.FileStorageUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * DOCUMENTS(서류 보관함) 다운로드 전용 서블릿.
 * 관련 요구사항: FR-62
 *
 * 파일은 DB(DOCUMENTS.file_data, 예전 서류는 디스크)에 있어 URL로 직접 접근할 수 없다 — 이 서블릿을 거쳐야만
 * 받을 수 있고, 그때도 세션의 본인 소유 문서인지 확인한다(다른 사용자 id로 남의 파일을 못 받게).
 */
@WebServlet("/documents/*")
public class DocumentDownloadServlet extends HttpServlet {

    private final DocumentDao documentDao = new DocumentDao();
    private final DocumentContentService documentContentService = new DocumentContentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        Long documentId = parseDocumentId(req.getPathInfo());
        if (documentId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        DocumentDto document;
        try {
            document = documentDao.findById(documentId);
        } catch (SQLException e) {
            throw new ServletException("파일 정보를 불러오는 중 오류가 발생했습니다.", e);
        }
        if (document == null || !document.getUserId().equals(userId)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 내용이 없으면(다른 PC 디스크에만 있는 예전 서류, 테스트 통과 버튼으로 만든 파일 없는 서류) 500 대신 "없음"으로 답한다
        if (!documentContentService.exists(document)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 저장된 MIME(브라우저가 보낸 값)은 믿지 않고 확장자로 다시 정한다
        resp.setContentType(FileStorageUtil.mimeTypeFor(document.getOriginalName()));
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(document.getOriginalName(), StandardCharsets.UTF_8));
        try {
            documentContentService.writeTo(document, resp.getOutputStream());
        } catch (SQLException e) {
            throw new ServletException("파일을 불러오는 중 오류가 발생했습니다.", e);
        }
    }

    private Long parseDocumentId(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        try {
            return Long.valueOf(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
