package com.specodyssey.controller;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.service.DocumentContentService;
import com.specodyssey.service.ShareViewService;
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
 * 면접관 공유 화면에 실린 서류(자격증 증빙 · 프로젝트 제출 서류)를 여는 전용 서블릿. 관련 요구사항: FR-81·85
 *
 * 로그인 세션이 없는 면접관이 접근하는 경로라 DocumentDownloadServlet(소유자 세션 확인)을 그대로 쓸 수
 * 없다 — 대신 공유 토큰으로 권한을 확인한다. 어떤 서류를 열 수 있는지는 ShareViewService.loadSharedDocument가
 * 정한다(그 링크의 화면에 실제로 실린 서류만). 안 맞으면 404 — 존재 여부 자체를 노출하지 않는다.
 */
@WebServlet("/share/documents/*")
public class ShareDocumentServlet extends HttpServlet {

    private final ShareViewService shareViewService = new ShareViewService();
    private final DocumentContentService documentContentService = new DocumentContentService();

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
            DocumentDto document = shareViewService.loadSharedDocument(token, documentId);
            // 내용은 DB에 있다. DB로 옮기기 전의 예전 서류는 올린 PC 디스크에만 있어 이 서버에는 없을 수 있다
            if (!documentContentService.exists(document)) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // 주소에 토큰이 들어 있으므로 캐시·검색 수집을 막는다
            resp.setHeader("Cache-Control", "no-store");
            resp.setHeader("X-Robots-Tag", "noindex, nofollow");
            // 브라우저가 보낸 MIME은 믿지 않고 확장자로 정하며, 스크립트가 돌 수 있는 형식은 열지 않고 내려받게만 한다
            resp.setContentType(FileStorageUtil.mimeTypeFor(document.getOriginalName()));
            String disposition = FileStorageUtil.isInlineSafe(document.getOriginalName()) ? "inline" : "attachment";
            resp.setHeader("Content-Disposition", disposition + "; filename*=UTF-8''"
                    + URLEncoder.encode(document.getOriginalName(), StandardCharsets.UTF_8));
            documentContentService.writeTo(document, resp.getOutputStream());
        } catch (SQLException e) {
            throw new ServletException("서류를 불러오는 중 오류가 발생했습니다.", e);
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
