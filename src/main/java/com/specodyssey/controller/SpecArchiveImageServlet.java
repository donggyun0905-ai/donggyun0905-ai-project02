package com.specodyssey.controller;

import com.specodyssey.dao.TechArticleAttachmentDao;
import com.specodyssey.dto.TechArticleAttachmentDto;
import com.specodyssey.service.archive.SpecArchiveRules;
import com.specodyssey.util.FileStorageUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.sql.SQLException;

/**
 * 스펙 아카이브 업로드 이미지 보여주기 — /spec-archive/image/{첨부 id}.
 * 사진 내용은 DB(file_data)에 있다 — 팀원 누구의 서버에서든 같은 DB면 보인다.
 * file_data가 비어 있는 예전 행(디스크 저장 시절)만 file_path에서 읽는다. 이 서블릿으로만 내보낸다. 로그인한 사용자만(SessionFilter),
 * 공개된 글의 업로드 이미지만 내보낸다 — 지운 글의 이미지는 주소를 알아도 받을 수 없다.
 * 응답 형식은 저장값을 다시 확인한 이미지 형식으로만 정하고 nosniff를 붙여, 브라우저가 다른 형식으로 해석하지 못하게 한다.
 */
@WebServlet("/spec-archive/image/*")
public class SpecArchiveImageServlet extends HttpServlet {

    private final TechArticleAttachmentDao attachmentDao = new TechArticleAttachmentDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long attachmentId = parseId(req.getPathInfo());
        if (attachmentId == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        TechArticleAttachmentDto image;
        try {
            image = attachmentDao.findVisibleUploadedImage(attachmentId);
        } catch (SQLException e) {
            throw new ServletException("이미지를 불러오는 중 오류가 발생했습니다.", e);
        }
        if (image == null || !SpecArchiveRules.isServableImageMime(image.getMimeType())) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        resp.setContentType(image.getMimeType());
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Content-Disposition", "inline");
        resp.setHeader("Cache-Control", "private, max-age=86400");
        byte[] data = image.getFileData();
        if (data != null) {
            resp.setContentLength(data.length);
            resp.getOutputStream().write(data);
            return;
        }
        if (image.getFilePath() == null || image.getFilePath().isBlank()) {
            resp.reset();
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (image.getFileSize() != null) {
            resp.setContentLengthLong(image.getFileSize());
        }
        try {
            FileStorageUtil.writeTo(image.getFilePath(), resp.getOutputStream());
        } catch (NoSuchFileException e) {
            resp.reset();
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private static Long parseId(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        try {
            return Long.valueOf(pathInfo.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
