package com.specodyssey.controller;

import com.specodyssey.service.archive.ArchiveContentCodec;
import com.specodyssey.service.archive.SpecArchiveRules;
import com.specodyssey.service.archive.SpecArchiveService;
import com.specodyssey.service.archive.SpecArchiveService.UploadedImage;
import com.specodyssey.util.FileStorageUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스펙 아카이브 글쓰기. 상위 티어만 쓸 수 있다 (서비스에서 다시 확인한다).
 *
 * 편집기(js/spec-archive-editor.js)가 보내는 것:
 *   title · content — 사진 자리에 [[upload:K]]가 들어간 본문
 *   image_K         — K번 사진 파일
 * 첨부 개수 제한은 없고 용량만 본다: 글 하나에 올리는 사진을 모두 합쳐 10MB.
 * 요청 전체 상한은 사진 10MB + 글·제목 여유 2MB — 사진 합계는 아래에서 따로 다시 센다.
 * 사진은 파일 앞부분으로 실제 형식(PNG·JPEG·GIF·WebP)을 확인하고, 저장 확장자도 그 형식으로 정한다.
 */
@WebServlet("/spec-archive/write")
@MultipartConfig(
        maxFileSize = SpecArchiveRules.MAX_IMAGE_BYTES,
        maxRequestSize = SpecArchiveRules.MAX_TOTAL_UPLOAD_BYTES + 2 * 1024 * 1024,
        fileSizeThreshold = 1024 * 1024
)
public class SpecArchiveWriteServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(SpecArchiveWriteServlet.class.getName());
    private static final String VIEW = "/WEB-INF/views/spec-archive-write.jsp";
    private static final Pattern IMAGE_PART = Pattern.compile("image_(\\d{1,4})");
    private static final Map<String, String> EXTENSION_OF = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/gif", "gif", "image/webp", "webp");

    private final SpecArchiveService archiveService = new SpecArchiveService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        prepare(req, SpecArchiveServlet.currentUserId(req));
        req.getRequestDispatcher(VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = SpecArchiveServlet.currentUserId(req);

        Collection<Part> parts;
        try {
            parts = req.getParts(); // 용량 초과는 여기서 드러난다
        } catch (IllegalStateException e) {
            req.setAttribute("errorMessage", "사진은 글 하나에 모두 합쳐 10MB까지 올릴 수 있습니다.");
            prepare(req, userId);
            req.getRequestDispatcher(VIEW).forward(req, resp);
            return;
        }

        String content = req.getParameter("content");
        Map<Integer, UploadedImage> uploads = new LinkedHashMap<>();
        try {
            // 사진 합계가 10MB를 넘으면 하나도 저장하지 않는다 (요청 상한은 글 여유분 2MB가 더 있어서 여기서 정확히 센다)
            long imageBytes = parts.stream().filter(p -> IMAGE_PART.matcher(p.getName()).matches()).mapToLong(Part::getSize).sum();
            if (imageBytes > SpecArchiveRules.MAX_TOTAL_UPLOAD_BYTES) {
                throw new IllegalArgumentException("사진은 글 하나에 모두 합쳐 10MB까지 올릴 수 있습니다.");
            }
            for (Part part : parts) {
                Matcher m = IMAGE_PART.matcher(part.getName());
                if (m.matches() && part.getSize() > 0) {
                    uploads.put(Integer.parseInt(m.group(1)), saveImage(part));
                }
            }
            Long articleId = archiveService.create(userId, req.getParameter("title"), content, uploads);
            // 본문에서 빠져 저장되지 않은 사진(편집 중 지운 것 등)은 디스크에 남기지 않는다
            SpecArchiveService.unusedUploadKeys(content, uploads.keySet())
                    .forEach(k -> FileStorageUtil.deleteQuietly(uploads.get(k).filePath()));
            req.getSession().setAttribute(SpecArchiveServlet.MESSAGE_KEY, "글을 올렸습니다.");
            resp.sendRedirect(req.getContextPath() + "/spec-archive/post?id=" + articleId);
        } catch (IllegalArgumentException | SecurityException e) {
            uploads.values().forEach(u -> FileStorageUtil.deleteQuietly(u.filePath())); // 글이 안 올라갔으면 파일도 남기지 않는다
            showFormAgain(req, resp, userId, e.getMessage(), content, !uploads.isEmpty());
        } catch (SQLException | IOException | RuntimeException e) {
            // 저장 중 오류(DB·디스크)도 오류 페이지로 보내지 않고 같은 화면에 안내한다 — 쓰던 글은 그대로 남긴다.
            // (오류 페이지에서 뒤로 가면 브라우저가 예전 화면을 되살려 이전 첨부가 다시 올라가던 문제도 함께 막는다)
            uploads.values().forEach(u -> FileStorageUtil.deleteQuietly(u.filePath()));
            LOG.log(Level.SEVERE, "스펙 아카이브 글 저장 실패 (userId=" + userId + ")", e);
            showFormAgain(req, resp, userId, "글을 저장하지 못했습니다. 잠시 후 다시 올려 주세요. 계속 안 되면 관리자에게 알려 주세요.",
                    content, !uploads.isEmpty());
        }
    }

    /** 오류 안내와 함께 글쓰기 화면을 다시 보여준다. 사진은 보안상 다시 넣어야 한다. */
    private void showFormAgain(HttpServletRequest req, HttpServletResponse resp, Long userId, String message,
                               String content, boolean hadImages) throws ServletException, IOException {
        req.setAttribute("errorMessage", message + (hadImages ? " 사진은 다시 넣어 주세요." : ""));
        req.setAttribute("title", req.getParameter("title"));
        req.setAttribute("content", ArchiveContentCodec.withoutUploadTokens(content));
        prepare(req, userId);
        req.getRequestDispatcher(VIEW).forward(req, resp);
    }

    private void prepare(HttpServletRequest req, Long userId) throws ServletException {
        try {
            req.setAttribute("canWrite", archiveService.canWrite(userId));
            req.setAttribute("writerTitles", String.join("·", archiveService.writerTierTitles()));
        } catch (SQLException e) {
            throw new ServletException("글쓰기 화면을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.setAttribute("titleMax", SpecArchiveRules.TITLE_MAX);
        req.setAttribute("contentMax", SpecArchiveRules.CONTENT_MAX);
        req.setAttribute("maxImageMb", SpecArchiveRules.MAX_IMAGE_BYTES / (1024 * 1024));
        req.setAttribute("maxTotalMb", SpecArchiveRules.MAX_TOTAL_UPLOAD_BYTES / (1024 * 1024));
        req.setAttribute("codeLanguages", SpecArchiveRules.CODE_LANGUAGES); // 코드 블록 언어 선택 목록
    }

    private UploadedImage saveImage(Part part) throws IOException {
        String originalName = part.getSubmittedFileName() == null || part.getSubmittedFileName().isBlank()
                ? "붙여넣은 사진" : part.getSubmittedFileName();
        try (InputStream raw = part.getInputStream(); BufferedInputStream in = new BufferedInputStream(raw)) {
            in.mark(16);
            byte[] head = in.readNBytes(12);
            in.reset();
            String mime = SpecArchiveRules.detectImageMime(head);
            if (mime == null) {
                throw new IllegalArgumentException("사진 파일만 올릴 수 있습니다 (PNG·JPG·GIF·WebP). — " + originalName);
            }
            // 저장 파일 확장자는 원본 이름이 아니라 확인한 형식으로 정한다
            FileStorageUtil.SavedFile saved = FileStorageUtil.save(in, "image." + EXTENSION_OF.get(mime));
            String name = originalName.length() <= 255 ? originalName : originalName.substring(0, 255);
            return new UploadedImage(name, saved.getStoredName(), saved.getFilePath(), saved.getFileSize(), mime);
        }
    }
}
