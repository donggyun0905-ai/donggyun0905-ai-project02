package com.specodyssey.controller;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.util.FileStorageUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * 면접관 공유 화면에서 CERT 증빙 서류를 내려받는 전용 서블릿. 관련 요구사항: FR-81·85
 *
 * 로그인 세션이 없는 면접관이 접근하는 경로라 DocumentDownloadServlet(소유자 세션 확인)을 그대로 쓸 수
 * 없다 — 대신 공유 토큰으로 권한을 확인한다: 토큰이 유효(활성·미만료)해야 하고, scope_basic을 공개한
 * 링크여야 하며(이력 타임라인 범위), 문서가 그 토큰의 주인(user_id) 소유여야 한다. 셋 중 하나라도
 * 안 맞으면 404 — 존재 여부 자체를 노출하지 않는다.
 */
@WebServlet("/share/documents/*")
public class ShareDocumentServlet extends HttpServlet {

    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final DocumentDao documentDao = new DocumentDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] parts = parsePathInfo(req.getPathInfo());
        if (parts == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String token = parts[0];
        Long documentId;
        try {
            documentId = Long.valueOf(parts[1]);
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            ShareLinkDto link = shareLinkDao.findByToken(token);
            if (link == null || !link.isScopeBasic()) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            DocumentDto document = documentDao.findById(documentId);
            if (document == null || !document.getUserId().equals(link.getUserId())) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            resp.setContentType(document.getMimeType() != null ? document.getMimeType() : "application/octet-stream");
            resp.setHeader("Content-Disposition", "inline; filename*=UTF-8''"
                    + URLEncoder.encode(document.getOriginalName(), StandardCharsets.UTF_8));
            FileStorageUtil.writeTo(document.getFilePath(), resp.getOutputStream());
        } catch (SQLException e) {
            throw new ServletException("증빙 서류를 불러오는 중 오류가 발생했습니다.", e);
        }
    }

    // pathInfo는 "/{token}/{documentId}" 형태 — 토큰 안에 '/'가 없다고 보장되므로(Base64 URL-safe)
    // 마지막 구간만 documentId로 떼어내고 나머지를 토큰으로 합친다.
    private String[] parsePathInfo(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        String trimmed = pathInfo.substring(1);
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash <= 0 || lastSlash == trimmed.length() - 1) {
            return null;
        }
        return new String[] { trimmed.substring(0, lastSlash), trimmed.substring(lastSlash + 1) };
    }
}
