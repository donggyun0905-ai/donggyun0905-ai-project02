package com.specodyssey.controller;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ShareViewService;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;

/**
 * 면접관 뷰(로그인 없이 링크로만 접근). 관련 요구사항: FR-81~86
 * 화면설계 PDF "13. 면접관 뷰", "14. 면접관 비교 뷰", "6-4. 유효하지 않은 공유 링크" 기준.
 * "/share/*"는 SessionFilter의 PUBLIC_PREFIXES에 있어 로그인 없이 열린다(FR-14 면접관은 계정이 없음).
 * "/share/{token}"은 지원자 이력(ShareViewService). 접근 제어는 ShareViewService가 토큰·활성·만료·공개 범위로 대신한다.
 * 비교(FR-82·83)는 면접관 계정 화면(InterviewerServlet "/interviewer/compare")이 처리한다.
 */
@WebServlet("/share/*")
public class ShareViewServlet extends HttpServlet {

    private static final String RESUME_SUFFIX = "/resume";
    private static final String COVER_LETTER_SUFFIX = "/cover-letter";

    private final ShareViewService shareViewService = new ShareViewService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        // 지원자 비교는 면접관 계정 화면으로 옮겼다 — 예전 주소로 들어오면 그쪽으로 보낸다(로그인 필요).
        if ("/compare".equals(pathInfo)) {
            resp.sendRedirect(req.getContextPath() + "/interviewer/compare");
            return;
        }

        // "?view=inline"이면 PDF를 내려받지 않고 화면 안(iframe)에 원본 그대로 연다 — PDF가 아니면 무시하고 내려받기
        boolean inline = "inline".equals(req.getParameter("view"));

        // "/share/{토큰}/resume" — 이력서 파일 내려받기
        if (pathInfo != null && pathInfo.endsWith(RESUME_SUFFIX)) {
            downloadFile(resp, pathInfo.substring(1, pathInfo.length() - RESUME_SUFFIX.length()),
                    shareViewService::loadResume, "이력서", inline);
            return;
        }

        // "/share/{토큰}/cover-letter" — 자소서 파일 내려받기
        if (pathInfo != null && pathInfo.endsWith(COVER_LETTER_SUFFIX)) {
            downloadFile(resp, pathInfo.substring(1, pathInfo.length() - COVER_LETTER_SUFFIX.length()),
                    shareViewService::loadCoverLetter, "자소서", inline);
            return;
        }

        String token = (pathInfo == null || pathInfo.length() < 2) ? "" : pathInfo.substring(1);
        HttpSession session = req.getSession(false);
        UserDto loginUser = session == null ? null : (UserDto) session.getAttribute("loginUser");
        ShareViewDto view;
        try {
            view = shareViewService.loadView(token, req.getRemoteAddr(), loginUser == null ? null : loginUser.getId());
        } catch (SQLException e) {
            throw new ServletException("공유 이력을 불러오는 중 오류가 발생했습니다.", e);
        }

        // 주소에 토큰이 들어 있으므로 캐시·검색 수집·외부 사이트로의 Referer 전달을 막는다
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Robots-Tag", "noindex, nofollow");
        resp.setHeader("Referrer-Policy", "no-referrer");

        req.setAttribute("valid", view != null);
        req.setAttribute("view", view);
        // 비교 목록에 담기는 면접관 계정만 할 수 있다 — 열람 자체는 로그인 없이도 된다(FR-85)
        req.setAttribute("token", token);
        req.setAttribute("interviewer", loginUser != null && RoleFilter.INTERVIEWER.equals(loginUser.getUserType()));
        req.getRequestDispatcher("/WEB-INF/views/interviewer-view.jsp").forward(req, resp);
    }

    // 지원자가 이 링크에 이력서·자소서 공개를 고른 경우에만 내려준다 — 조건 확인은 ShareViewService.loadResume·loadCoverLetter가 한다.
    // 받을 수 없는 경우는 이유를 구분하지 않고 404로 답한다(링크가 유효한지 떠볼 단서를 주지 않는다).
    private void downloadFile(HttpServletResponse resp, String token, FileLoader loader, String label, boolean inline)
            throws ServletException, IOException {
        DocumentDto file;
        try {
            file = loader.load(token);
        } catch (SQLException e) {
            throw new ServletException(label + "를 불러오는 중 오류가 발생했습니다.", e);
        }
        // 업로드 폴더는 서버 PC마다 따로라, DB에는 있는데 이 서버에는 파일이 없을 수 있다
        if (file == null || !Files.isRegularFile(Paths.get(file.getFilePath()))) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Robots-Tag", "noindex, nofollow");
        resp.setContentType(FileStorageUtil.mimeTypeFor(file.getOriginalName()));
        // 화면에 끼워 여는 건 PDF만 — 스크립트가 돌 수 있는 형식은 열지 않는다
        String disposition = inline && FileStorageUtil.isPdf(file.getOriginalName()) ? "inline" : "attachment";
        resp.setHeader("Content-Disposition", disposition + "; filename*=UTF-8''"
                + URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8));
        FileStorageUtil.writeTo(file.getFilePath(), resp.getOutputStream());
    }

    @FunctionalInterface
    private interface FileLoader {
        DocumentDto load(String token) throws SQLException;
    }
}
