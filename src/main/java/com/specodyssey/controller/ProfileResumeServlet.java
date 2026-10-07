package com.specodyssey.controller;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ResumeService;
import com.specodyssey.util.FileStorageUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 내 프로필의 이력서·자소서 올리기 · 바꾸기 · 삭제. 화면은 내 프로필(profile.jsp)에 있고 끝나면 그리로 돌아간다.
 * "/profile/resume"은 이력서, "/profile/cover-letter"는 자소서 — 규칙(형식·10MB·교체 시 이전 파일 삭제)이 같아서
 * 한 서블릿이 주소로 가른다. 내려받기는 DocumentDownloadServlet("/documents/{id}")이 소유자 확인 후 처리한다.
 */
@WebServlet({"/profile/resume", "/profile/cover-letter"})
@MultipartConfig(
        maxFileSize = 10L * 1024 * 1024,     // 이력서 1개 10MB
        maxRequestSize = 11L * 1024 * 1024,
        fileSizeThreshold = 0
)
public class ProfileResumeServlet extends HttpServlet {

    private static final String COVER_LETTER_PATH = "/profile/cover-letter";

    private final ResumeService resumeService = new ResumeService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
        boolean coverLetter = COVER_LETTER_PATH.equals(req.getServletPath());
        String label = coverLetter ? "자소서" : "이력서";

        try {
            if ("delete".equals(req.getParameter("action"))) {
                if (coverLetter) {
                    resumeService.removeCoverLetter(userId);
                } else {
                    resumeService.removeResume(userId);
                }
            } else {
                String error = upload(req, userId, coverLetter, label);
                if (error != null) {
                    req.getSession(false).setAttribute(coverLetter ? "coverLetterMessage" : "resumeMessage", error);
                }
            }
        } catch (SQLException e) {
            throw new ServletException(label + " 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    // 실패하면 화면에 보여줄 문구를, 성공하면 null을 돌려준다.
    private String upload(HttpServletRequest req, Long userId, boolean coverLetter, String label)
            throws IOException, ServletException, SQLException {
        Part part;
        try {
            part = req.getPart(coverLetter ? "coverLetter" : "resume");
        } catch (IllegalStateException e) {
            // 컨테이너가 @MultipartConfig의 maxFileSize/maxRequestSize 초과를 이렇게(비검사 예외) 알린다.
            return "파일 용량이 너무 큽니다 (10MB 이하).";
        }
        String originalName = part == null ? null : part.getSubmittedFileName();
        if (originalName == null || originalName.isBlank() || part.getSize() == 0) {
            return "올릴 " + label + " 파일을 선택해주세요.";
        }
        if (!ResumeService.isAllowedFile(originalName)) {
            return "PDF, Word(doc·docx), 한글(hwp·hwpx) 파일만 올릴 수 있습니다.";
        }

        FileStorageUtil.SavedFile saved;
        try (var in = part.getInputStream()) {
            saved = FileStorageUtil.save(in, originalName);
        }
        DocumentDto document = new DocumentDto();
        document.setOriginalName(originalName);
        document.setStoredName(saved.getStoredName());
        document.setFilePath(saved.getFilePath());
        document.setFileData(saved.getData()); // 파일 내용은 DB(DOCUMENTS.file_data)에
        document.setFileSize(saved.getFileSize());
        document.setMimeType(FileStorageUtil.mimeTypeFor(originalName));
        document.setChecksum(saved.getChecksum());
        try {
            if (coverLetter) {
                resumeService.replaceCoverLetter(userId, document);
            } else {
                resumeService.replaceResume(userId, document);
            }
        } catch (SQLException | RuntimeException e) {
            // DB에 등록하지 못했으면 방금 쓴 파일을 남겨 두지 않는다
            FileStorageUtil.deleteQuietly(saved.getFilePath());
            throw e;
        }
        return null;
    }
}
