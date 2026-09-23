package com.specodyssey.controller;

import com.specodyssey.dao.DocumentDao;
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
 * 파일은 webapp 밖(FileStorageUtil 저장 경로)에 있어 URL로 직접 접근할 수 없다 — 이 서블릿을 거쳐야만
 * 받을 수 있고, 그때도 세션의 본인 소유 문서인지 확인한다(다른 사용자 id로 남의 파일을 못 받게).
 */
@WebServlet("/documents/*")
public class DocumentDownloadServlet extends HttpServlet {

    private final DocumentDao documentDao = new DocumentDao();

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

        resp.setContentType(document.getMimeType() != null ? document.getMimeType() : "application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(document.getOriginalName(), StandardCharsets.UTF_8));
        FileStorageUtil.writeTo(document.getFilePath(), resp.getOutputStream());
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
