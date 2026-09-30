package com.specodyssey.controller;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.service.RoadmapService;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.util.PdfTextUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 로드맵 조회 · 생성 · 완료 체크.
 * 관련 요구사항: FR-32 · 33 · 36
 *
 * PROJECT 단계 완료(completeProject)는 파일 첨부가 필요해 이 서블릿에 @MultipartConfig를 붙였다.
 * generate/complete 액션은 그대로 application/x-www-form-urlencoded로 오므로 영향 없다.
 */
@WebServlet("/roadmap")
@MultipartConfig(
        maxFileSize = 20L * 1024 * 1024,       // 파일 1개당 20MB
        maxRequestSize = 100L * 1024 * 1024,   // 요청 전체 100MB (여러 파일 첨부 허용)
        fileSizeThreshold = 0
)
public class RoadmapServlet extends HttpServlet {

    private final RoadmapService roadmapService = new RoadmapService();
    private final UserDao userDao = new UserDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        try {
            // 로드맵은 목표 직무가 있어야 의미가 있다 — 희망 직무가 아직 없으면 로드맵 내용 대신
            // 직무 찾기/프로필로 안내하는 작은 카드만 보여준다(사용자 요청, 2026-09-29).
            UserDto user = userDao.findById(userId);
            if (user.getDesiredJobId() == null) {
                req.setAttribute("noTargetJob", true);
                req.getRequestDispatcher("/WEB-INF/views/roadmap.jsp").forward(req, resp);
                return;
            }

            RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(userId);
            req.setAttribute("roadmap", roadmap);
            List<RoadmapStepDto> steps = roadmap == null
                    ? Collections.emptyList()
                    : roadmapService.getSteps(roadmap.getId());
            req.setAttribute("steps", steps);
            req.setAttribute("progress", roadmapService.computeProgress(steps));
            // CORE/ADVANCED SKILL 단계의 "기존 프로젝트 업그레이드" 선택지용 — 2026-09-30 팀 결정.
            req.setAttribute("userProjects", userProjectDao.findByUserId(userId));
        } catch (SQLException e) {
            throw new ServletException("로드맵을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/roadmap.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        String action = req.getParameter("action");

        try {
            if ("generate".equals(action)) {
                roadmapService.generate(userId);
            } else if ("complete".equals(action)) {
                Long stepId = Long.valueOf(req.getParameter("stepId"));
                boolean completed = "true".equals(req.getParameter("completed"));
                roadmapService.completeStep(userId, stepId, completed);
            } else if ("completeProject".equals(action)) {
                if (!handleCompleteProject(req, resp, userId)) {
                    return;
                }
            } else if ("submitSkillNote".equals(action)) {
                if (!handleSubmitSkillNote(req, resp, userId)) {
                    return;
                }
            } else if ("submitSkillProject".equals(action)) {
                if (!handleSubmitSkillProject(req, resp, userId)) {
                    return;
                }
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (RoadmapService.NoGapAnalysisException e) {
            req.setAttribute("errorMessage", e.getMessage());
            doGet(req, resp);
            return;
        } catch (SQLException e) {
            throw new ServletException("로드맵 처리 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/roadmap");
    }

    // 파일 저장(디스크 I/O)은 DB 트랜잭션 밖에서 먼저 끝내고, 실패하면 이미 쓴 파일을 되돌린 뒤
    // 에러 메시지와 함께 로드맵 화면으로 돌아간다. 성공하면 true, 이미 응답을 처리했으면 false를 반환한다.
    private boolean handleCompleteProject(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException, SQLException {
        Long stepId = Long.valueOf(req.getParameter("stepId"));

        UserProjectDto project = new UserProjectDto();
        project.setTitle(trimToNull(req.getParameter("title")));
        project.setDescription(trimToNull(req.getParameter("description")));
        project.setTechStack(trimToNull(req.getParameter("techStack")));
        project.setStartDate(parseDate(req.getParameter("startDate")));
        project.setEndDate(parseDate(req.getParameter("endDate")));

        if (project.getTitle() == null || project.getDescription() == null || project.getTechStack() == null) {
            return failWith(req, resp, "프로젝트명·설명·기술스택은 모두 필수입니다.");
        }

        List<DocumentDto> savedFiles;
        try {
            savedFiles = collectUploadedFiles(req);
        } catch (IllegalArgumentException e) {
            return failWith(req, resp, e.getMessage());
        }

        boolean applied;
        try {
            applied = roadmapService.completeProjectStep(userId, stepId, project, savedFiles);
        } catch (IllegalArgumentException e) {
            for (DocumentDto document : savedFiles) {
                FileStorageUtil.deleteQuietly(document.getFilePath());
            }
            return failWith(req, resp, e.getMessage());
        } catch (SQLException e) {
            // DB 저장이 실패해도 디스크엔 이미 파일이 써져 있으니, DB에 남길 게 없으면 지운다.
            for (DocumentDto document : savedFiles) {
                FileStorageUtil.deleteQuietly(document.getFilePath());
            }
            throw e;
        }
        if (!applied) {
            // 이미 완료된 단계거나 소유자가 아니라서 아무 것도 반영 안 됨 — 방금 저장한 파일은 고아가 되니 지운다.
            for (DocumentDto document : savedFiles) {
                FileStorageUtil.deleteQuietly(document.getFilePath());
            }
        }
        return true;
    }

    // ENTRY(공부노트)/EXPERT(기술 설명 글) SKILL 단계 제출 — PDF 1개를 받아 PdfTextUtil로 텍스트를
    // 꺼낸 뒤 서비스에 넘긴다. 통과/미통과 여부는 서비스가 ROADMAP_STEP.review_status/review_note에
    // 저장해두므로, 여기서는 그냥 리다이렉트만 해도 다음 GET에서 roadmap.jsp가 최신 판정 결과를
    // 그대로 보여준다. PDFBox가 텍스트와 파일 저장에 각각 스트림을 소비하므로 한 번만 읽어 바이트
    // 배열로 들고 있다가 두 번 재사용한다.
    private boolean handleSubmitSkillNote(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException, SQLException {
        Long stepId = Long.valueOf(req.getParameter("stepId"));

        Part filePart;
        try {
            filePart = req.getPart("file");
        } catch (IllegalStateException e) {
            return failWith(req, resp, "첨부 파일 용량이 너무 큽니다 (파일당 20MB 이하).");
        }
        if (filePart == null || filePart.getSubmittedFileName() == null || filePart.getSubmittedFileName().isBlank()) {
            return failWith(req, resp, "PDF 파일을 첨부해야 합니다.");
        }
        String filename = filePart.getSubmittedFileName();
        if (!filename.toLowerCase().endsWith(".pdf")) {
            return failWith(req, resp, "PDF 파일만 업로드할 수 있습니다.");
        }

        byte[] fileBytes;
        try (InputStream in = filePart.getInputStream()) {
            fileBytes = in.readAllBytes();
        }

        String extractedText;
        try (InputStream textIn = new ByteArrayInputStream(fileBytes)) {
            extractedText = PdfTextUtil.extractText(textIn);
        } catch (IOException e) {
            return failWith(req, resp, "PDF 내용을 읽을 수 없습니다: " + e.getMessage());
        }

        FileStorageUtil.SavedFile saved;
        try (InputStream saveIn = new ByteArrayInputStream(fileBytes)) {
            saved = FileStorageUtil.save(saveIn, filename);
        }
        DocumentDto document = new DocumentDto();
        document.setOriginalName(filename);
        document.setStoredName(saved.getStoredName());
        document.setFilePath(saved.getFilePath());
        document.setFileSize(saved.getFileSize());
        document.setMimeType(filePart.getContentType());
        document.setChecksum(saved.getChecksum());

        try {
            roadmapService.submitSkillNote(userId, stepId, extractedText, document);
        } catch (IllegalArgumentException e) {
            FileStorageUtil.deleteQuietly(document.getFilePath());
            return failWith(req, resp, e.getMessage());
        } catch (SQLException e) {
            FileStorageUtil.deleteQuietly(document.getFilePath());
            throw e;
        }
        return true;
    }

    // CORE/ADVANCED SKILL 단계 제출 — "프로젝트 등록 또는 기존 프로젝트 업그레이드 + 증빙 파일"로
    // 완료 처리한다. handleCompleteProject와 파일 업로드 방식은 같고, 대상 단계 타입(PROJECT vs SKILL)과
    // upgradeFromProjectId 유무만 다르다.
    private boolean handleSubmitSkillProject(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException, SQLException {
        Long stepId = Long.valueOf(req.getParameter("stepId"));

        UserProjectDto project = new UserProjectDto();
        project.setTitle(trimToNull(req.getParameter("title")));
        project.setDescription(trimToNull(req.getParameter("description")));
        project.setTechStack(trimToNull(req.getParameter("techStack")));
        project.setStartDate(parseDate(req.getParameter("startDate")));
        project.setEndDate(parseDate(req.getParameter("endDate")));
        if (project.getTitle() == null || project.getDescription() == null || project.getTechStack() == null) {
            return failWith(req, resp, "프로젝트명·설명·기술스택은 모두 필수입니다.");
        }

        String upgradeParam = trimToNull(req.getParameter("upgradeFromProjectId"));
        Long upgradeFromProjectId = upgradeParam == null ? null : Long.valueOf(upgradeParam);

        List<DocumentDto> savedFiles;
        try {
            savedFiles = collectUploadedFiles(req);
        } catch (IllegalArgumentException e) {
            return failWith(req, resp, e.getMessage());
        }

        boolean applied;
        try {
            applied = roadmapService.submitSkillProjectStep(userId, stepId, project, savedFiles, upgradeFromProjectId);
        } catch (IllegalArgumentException e) {
            for (DocumentDto document : savedFiles) {
                FileStorageUtil.deleteQuietly(document.getFilePath());
            }
            return failWith(req, resp, e.getMessage());
        } catch (SQLException e) {
            for (DocumentDto document : savedFiles) {
                FileStorageUtil.deleteQuietly(document.getFilePath());
            }
            throw e;
        }
        if (!applied) {
            for (DocumentDto document : savedFiles) {
                FileStorageUtil.deleteQuietly(document.getFilePath());
            }
        }
        return true;
    }

    // 첨부 파일(files 파트)을 모아 디스크에 저장한다. 검증 실패·저장 실패는 전부
    // IllegalArgumentException(사용자에게 보여줄 메시지)으로 통일해 호출부가 failWith로 처리하게 한다.
    private List<DocumentDto> collectUploadedFiles(HttpServletRequest req) throws ServletException, IOException {
        List<Part> fileParts = new ArrayList<>();
        try {
            for (Part part : req.getParts()) {
                if ("files".equals(part.getName()) && part.getSubmittedFileName() != null
                        && !part.getSubmittedFileName().isBlank()) {
                    fileParts.add(part);
                }
            }
        } catch (IllegalStateException e) {
            // 컨테이너가 @MultipartConfig의 maxFileSize/maxRequestSize 초과를 이렇게(비검사 예외) 알린다.
            throw new IllegalArgumentException("첨부 파일 용량이 너무 큽니다 (파일당 20MB, 전체 100MB 이하).");
        }
        if (fileParts.isEmpty()) {
            throw new IllegalArgumentException("증빙 파일을 최소 1개 첨부해야 합니다.");
        }
        for (Part part : fileParts) {
            if (FileStorageUtil.isExtensionBlocked(part.getSubmittedFileName())) {
                throw new IllegalArgumentException("업로드할 수 없는 파일 형식입니다: " + part.getSubmittedFileName());
            }
        }

        List<DocumentDto> savedFiles = new ArrayList<>();
        try {
            for (Part part : fileParts) {
                FileStorageUtil.SavedFile saved;
                try (var in = part.getInputStream()) {
                    saved = FileStorageUtil.save(in, part.getSubmittedFileName());
                }
                DocumentDto document = new DocumentDto();
                document.setOriginalName(part.getSubmittedFileName());
                document.setStoredName(saved.getStoredName());
                document.setFilePath(saved.getFilePath());
                document.setFileSize(saved.getFileSize());
                document.setMimeType(part.getContentType());
                document.setChecksum(saved.getChecksum());
                savedFiles.add(document);
            }
        } catch (IOException e) {
            for (DocumentDto document : savedFiles) {
                FileStorageUtil.deleteQuietly(document.getFilePath());
            }
            throw new IllegalArgumentException("파일 저장 중 오류가 발생했습니다.");
        }
        return savedFiles;
    }

    private boolean failWith(HttpServletRequest req, HttpServletResponse resp, String message)
            throws ServletException, IOException {
        req.setAttribute("errorMessage", message);
        doGet(req, resp);
        return false;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
