package com.specodyssey.controller;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.GapAnalysisService;
import com.specodyssey.service.ProjectSubmission;
import com.specodyssey.service.ProjectSubmissionService;
import com.specodyssey.service.RoadmapService;
import com.specodyssey.service.RoadmapHistory;
import com.specodyssey.service.RoadmapProgress;
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
import java.util.Collections;
import java.util.List;
import static com.specodyssey.controller.RoadmapSubmissionForm.buildSubmission;
import static com.specodyssey.controller.RoadmapSubmissionForm.deleteNewFiles;
import static com.specodyssey.controller.RoadmapSubmissionForm.noteSkippedTechNotes;
import static com.specodyssey.controller.RoadmapSubmissionForm.trimToNull;

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
    private final GapAnalysisService gapAnalysisService = new GapAnalysisService();
    private final UserDao userDao = new UserDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final TierCelebration tierCelebration = new TierCelebration();
    private final ProjectSubmissionService projectSubmissionService = new ProjectSubmissionService();

    /**
     * 개발·시연용 "[TEST] 파일 없이 통과" 버튼과 서버 경로. 기본은 켜짐이고, 운영 배포에서는 .env에
     * ENABLE_TEST_SHORTCUT=false 를 넣어 끈다(끄면 버튼도 안 보이고 요청을 직접 보내도 거절된다).
     * 테스트에서는 testShortcutOverride로 값을 고정한다.
     */
    static Boolean testShortcutOverride;

    static boolean testShortcutEnabled() {
        if (testShortcutOverride != null) {
            return testShortcutOverride;
        }
        return !"false".equalsIgnoreCase(com.specodyssey.util.AppConfig.get("ENABLE_TEST_SHORTCUT"));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        req.setAttribute("testShortcut", testShortcutEnabled());
        try {
            // 로드맵은 목표 직무가 있어야 의미가 있다 — 희망 직무가 아직 없으면 로드맵 내용 대신
            // 직무 찾기/프로필로 안내하는 작은 카드만 보여준다(사용자 요청, 2026-09-29).
            UserDto user = userDao.findById(userId);
            if (user.getDesiredJobId() == null) {
                req.setAttribute("noTargetJob", true);
                req.getRequestDispatcher("/WEB-INF/views/roadmap.jsp").forward(req, resp);
                return;
            }

            // 주기가 지난 기술의 복습 단계를 여정 뒤에 이어 붙인다 — 부가 기능이라 실패해도 로드맵 조회는 막지 않는다.
            // 검사는 세션당 하루 한 번만 한다(ReviewCheckGate) — 매번 DB를 훑지 않도록.
            if (ReviewCheckGate.shouldCheck(req.getSession(false), java.time.LocalDate.now())) {
                try {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    roadmapService.appendDueReviews(userId, now);
                    roadmapService.appendDueUpkeep(userId, now);
                } catch (SQLException e) {
                    ReviewCheckGate.reset(req.getSession(false)); // 실패했으면 다음에 다시 시도한다
                    getServletContext().log("복습 단계 생성 실패", e);
                }
            }
            RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(userId);
            req.setAttribute("roadmap", roadmap);
            List<RoadmapStepDto> steps = roadmap == null
                    ? Collections.emptyList()
                    : roadmapService.getSteps(roadmap.getId());
            // 진행도는 전체 단계로 계산하고, 화면에는 끝낸 단계를 최근 것만 보여준다(복습이 계속 붙어 길어지므로).
            boolean showAllHistory = "all".equals(req.getParameter("history"));
            RoadmapHistory history = RoadmapHistory.of(steps, RoadmapHistory.DEFAULT_KEEP_COMPLETED, showAllHistory);
            req.setAttribute("steps", history.getVisible());
            req.setAttribute("hiddenCompletedCount", history.getHiddenCompleted());
            req.setAttribute("historyAll", showAllHistory);
            req.setAttribute("historyCollapsible", history.isCollapsible(RoadmapHistory.DEFAULT_KEEP_COMPLETED));
            req.setAttribute("progress", roadmapService.computeProgress(steps));
            // 티어 돌파 환영 모달 — 완료 처리 직후 한 번만 뜨도록 세션에 잠깐 실어둔 신호를 꺼내 쓰고 지운다
            // (새로고침하면 이미 지워져 있어서 다시 안 뜬다).
            tierCelebration.consumeTierCelebration(req);
            // CORE/ADVANCED SKILL 단계의 "기존 프로젝트 업그레이드" 선택지용 — 2026-09-30 팀 결정.
            req.setAttribute("userProjects", userProjectDao.findByUserId(userId));
            // 프로젝트 제출 폼 미리채움 — 완료를 취소했던 단계는 이전에 낸 프로젝트·서류를 그대로 보여준다.
            req.setAttribute("projectDrafts", loadProjectDrafts(userId, steps));
            req.setAttribute("docTypeLabels", ProjectSubmissionService.DOC_TYPE_LABELS);
            req.setAttribute("requiredDocTypes", ProjectSubmissionService.REQUIRED_DOC_TYPES);
            HttpSession noticeSession = req.getSession(false);
            if (noticeSession != null && noticeSession.getAttribute("roadmapNotice") != null) {
                req.setAttribute("roadmapNotice", noticeSession.getAttribute("roadmapNotice"));
                noticeSession.removeAttribute("roadmapNotice");
            }
            // "요구 기술이 바뀌었어요" 배너 — 로드맵이 기준으로 삼은 분석이 낡았는지(2026-09-30 팀 결정).
            if (roadmap != null) {
                req.setAttribute("requirementOutdated", roadmapService.isJobRequirementOutdated(userId));
            }
        } catch (SQLException e) {
            throw new ServletException("로드맵을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/roadmap.jsp").forward(req, resp);
    }

    // 단계 id → 이전 제출 초안. 프로젝트가 연결된(= 한 번 제출했던) 단계만 들어 있다.
    private java.util.Map<Long, ProjectSubmissionService.ProjectDraft> loadProjectDrafts(Long userId,
            List<RoadmapStepDto> steps) throws SQLException {
        java.util.Map<Long, ProjectSubmissionService.ProjectDraft> drafts = new java.util.HashMap<>();
        for (RoadmapStepDto step : steps) {
            // 프로젝트·기술 프로젝트 제출 폼의 초안 — 프로젝트 업데이트 단계도 프로젝트를 가리키지만 그 폼은 쓰지 않는다
            boolean projectForm = "PROJECT".equals(step.getStepType()) || "SKILL".equals(step.getStepType());
            if (projectForm && step.getEvidenceProjectId() != null && !step.isCompleted()) {
                ProjectSubmissionService.ProjectDraft draft =
                        projectSubmissionService.loadDraft(userId, step.getEvidenceProjectId());
                if (draft != null) {
                    drafts.put(step.getId(), draft);
                }
            }
        }
        return drafts;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        String action = req.getParameter("action");
        // 단계 완료로 이어질 수 있는 액션만 전/후 진행도를 비교한다 — generate 등은 티어 구성 자체가
        // 바뀌므로 "방금 티어를 끝냈다"로 오인하면 안 된다.
        boolean mayCompleteStep = "complete".equals(action) || "completeProject".equals(action)
                || "submitSkillNote".equals(action) || "submitSkillProject".equals(action)
                || "submitCertProof".equals(action) || "completeReview".equals(action)
                || "completeUpkeep".equals(action) || "submitArticleUpdate".equals(action);
        Long scoreTierBefore = null;

        try {
            scoreTierBefore = mayCompleteStep ? tierCelebration.currentScoreTierId(userId) : null;
            if ("generate".equals(action)) {
                ReviewCheckGate.reset(req.getSession(false));
                roadmapService.generate(userId);
            } else if ("complete".equals(action)) {
                Long stepId = Long.valueOf(req.getParameter("stepId"));
                boolean completed = "true".equals(req.getParameter("completed"));
                if (completed && !testShortcutEnabled()) {
                    // 완료는 단계마다 정해진 증빙(프로젝트·노트·서류·기록)을 내야만 된다. 체크만으로 완료하는 길은 없다 —
                    // 이 액션은 "완료 취소"에만 쓴다. (프로필에서 직접 추가한 스킬·자격증의 자동 완료는 서비스가 따로 처리한다)
                    // 예외: .env에 ENABLE_TEST_SHORTCUT=true 를 켠 개발·시연 환경의 [TEST] 버튼.
                    throw new IllegalArgumentException("이 단계는 증빙을 제출해야 완료할 수 있습니다.");
                }
                roadmapService.completeStep(userId, stepId, completed);
            } else if ("completeProject".equals(action)) {
                if (!handleCompleteProject(req, resp, userId)) {
                    return;
                }
            } else if ("submitSkillNote".equals(action)) {
                if (!handleSubmitSkillNote(req, resp, userId, false)) {
                    return;
                }
            } else if ("submitSkillProject".equals(action)) {
                if (!handleSubmitSkillProject(req, resp, userId)) {
                    return;
                }
            } else if ("completeReview".equals(action)) {
                int points = roadmapService.completeReview(userId, Long.valueOf(req.getParameter("stepId")),
                        req.getParameter("reviewNote"));
                if (points > 0) {
                    req.getSession().setAttribute("roadmapNotice", "복습 완료! +" + points + "점을 받았어요.");
                }
            } else if ("completeUpkeep".equals(action)) {
                // 프로젝트 업데이트·트렌딩 학습 — 기록을 내면 끝난다
                int points = roadmapService.completeUpkeep(userId, Long.valueOf(req.getParameter("stepId")),
                        req.getParameter("note"));
                if (points > 0) {
                    req.getSession().setAttribute("roadmapNotice", "기록을 남겼어요! +" + points + "점을 받았어요.");
                }
            } else if ("submitArticleUpdate".equals(action)) {
                if (!handleSubmitSkillNote(req, resp, userId, true)) {
                    return;
                }
            } else if ("submitCertProof".equals(action)) {
                if (!handleSubmitCertProof(req, resp, userId)) {
                    return;
                }
            } else if ("reanalyzeAndRegenerate".equals(action)) {
                // "요구 기술이 바뀌었어요" 배너의 액션 — 여기서만 실제 재분석(비용 발생 지점)이 일어난다
                // (2026-09-30 팀 결정). 목표 직무는 UserDto.desiredJobId를 그대로 쓴다.
                UserDto user = userDao.findById(userId);
                if (user.getDesiredJobId() != null) {
                    ReviewCheckGate.reset(req.getSession(false));
                    gapAnalysisService.analyze(userId, user.getDesiredJobId());
                    roadmapService.generate(userId);
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
        } catch (IllegalArgumentException e) {
            // "complete" 액션으로 CERT 단계를 직접 완료 시도한 경우 등 — 파일 첨부가 없는 액션이라
            // handleXxx의 failWith 패턴을 쓸 수 없어 여기서 한 번에 처리한다.
            req.setAttribute("errorMessage", e.getMessage());
            doGet(req, resp);
            return;
        } catch (SQLException e) {
            throw new ServletException("로드맵 처리 중 오류가 발생했습니다.", e);
        }

        if (mayCompleteStep) {
            try {
                tierCelebration.recordTierCelebration(req, userId, scoreTierBefore);
            } catch (SQLException e) {
                // 축하 모달은 부가 기능 — 조회 실패로 이미 끝난 완료 처리 응답까지 망치지 않는다.
            }
        }
        resp.sendRedirect(req.getContextPath() + "/roadmap");
    }

    // 파일 저장(디스크 I/O)은 DB 트랜잭션 밖에서 먼저 끝내고, 실패하면 이미 쓴 파일을 되돌린 뒤
    // 에러 메시지와 함께 로드맵 화면으로 돌아간다. 성공하면 true, 이미 응답을 처리했으면 false를 반환한다.
    private boolean handleCompleteProject(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException, SQLException {
        Long stepId = Long.valueOf(req.getParameter("stepId"));

        ProjectSubmission submission;
        try {
            submission = buildSubmission(req);
        } catch (IllegalArgumentException e) {
            return failWith(req, resp, e.getMessage());
        }

        boolean applied;
        try {
            applied = roadmapService.completeProjectStep(userId, stepId, submission);
        } catch (IllegalArgumentException e) {
            deleteNewFiles(submission);
            return failWith(req, resp, e.getMessage());
        } catch (SQLException e) {
            // DB 저장이 실패해도 디스크엔 이미 파일이 써져 있으니, DB에 남길 게 없으면 지운다.
            deleteNewFiles(submission);
            throw e;
        }
        if (!applied) {
            // 이미 완료된 단계거나 소유자가 아니라서 아무 것도 반영 안 됨 — 방금 저장한 파일은 고아가 되니 지운다.
            deleteNewFiles(submission);
        } else {
            noteSkippedTechNotes(req, submission);
        }
        return true;
    }

    // ENTRY(공부노트)/EXPERT(기술 설명 글) SKILL 단계 제출 — PDF 1개를 받아 PdfTextUtil로 텍스트를
    // 꺼낸 뒤 서비스에 넘긴다. 통과/미통과 여부는 서비스가 ROADMAP_STEP.review_status/review_note에
    // 저장해두므로, 여기서는 그냥 리다이렉트만 해도 다음 GET에서 roadmap.jsp가 최신 판정 결과를
    // 그대로 보여준다. PDFBox가 텍스트와 파일 저장에 각각 스트림을 소비하므로 한 번만 읽어 바이트
    // 배열로 들고 있다가 두 번 재사용한다.
    private boolean handleSubmitSkillNote(HttpServletRequest req, HttpServletResponse resp, Long userId,
            boolean articleUpdate) throws ServletException, IOException, SQLException {
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
        document.setMimeType(FileStorageUtil.mimeTypeFor(filename));
        document.setChecksum(saved.getChecksum());

        try {
            // 기술 글 업데이트도 같은 PDF 제출 흐름이고, 판정 서비스만 다르다
            if (articleUpdate) {
                roadmapService.submitArticleUpdate(userId, stepId, extractedText, document);
            } else {
                roadmapService.submitSkillNote(userId, stepId, extractedText, document);
            }
        } catch (IllegalArgumentException e) {
            FileStorageUtil.deleteQuietly(document.getFilePath());
            return failWith(req, resp, e.getMessage());
        } catch (SQLException e) {
            FileStorageUtil.deleteQuietly(document.getFilePath());
            throw e;
        }
        return true;
    }

    // CERT 단계 제출 — 자격증 취득을 증명하는 서류(합격 확인서 캡처·자격증 사진 등) 1개를 받아
    // 완료 처리한다(2026-09-30 팀 결정). 텍스트 추출·규칙 판정 없이 첨부 자체를 증빙으로 신뢰하므로
    // 파일 형식 제한은 FileStorageUtil의 공통 허용 목록만 적용한다(이미지·PDF 등).
    private boolean handleSubmitCertProof(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException, SQLException {
        Long stepId = Long.valueOf(req.getParameter("stepId"));

        // [TEST] 파일 없이 통과 — roadmap.jsp의 테스트 전용 버튼 하나만 이 파라미터를 보낸다.
        // 실제 운영 배포 전에는 이 분기와 그 버튼을 함께 지울 것(2026-09-30, 사용자 요청).
        if (testShortcutEnabled() && "1".equals(req.getParameter("testShortcut"))) {
            DocumentDto testDocument = new DocumentDto();
            testDocument.setOriginalName("test-cert-shortcut.txt");
            testDocument.setStoredName("test-cert-shortcut-" + System.nanoTime() + ".txt");
            testDocument.setFilePath("");
            testDocument.setFileSize(0L);
            testDocument.setMimeType("text/plain");
            testDocument.setChecksum("test-shortcut");
            try {
                roadmapService.submitCertProof(userId, stepId, testDocument);
            } catch (IllegalArgumentException e) {
                return failWith(req, resp, e.getMessage());
            }
            return true;
        }

        Part filePart;
        try {
            filePart = req.getPart("file");
        } catch (IllegalStateException e) {
            return failWith(req, resp, "첨부 파일 용량이 너무 큽니다 (파일당 20MB 이하).");
        }
        if (filePart == null || filePart.getSubmittedFileName() == null || filePart.getSubmittedFileName().isBlank()) {
            return failWith(req, resp, "자격증 증빙 서류를 첨부해야 합니다.");
        }
        String filename = filePart.getSubmittedFileName();
        if (!FileStorageUtil.isAllowedFile(filename)) {
            return failWith(req, resp, "업로드할 수 없는 파일 형식입니다: " + filename);
        }

        FileStorageUtil.SavedFile saved;
        try (InputStream in = filePart.getInputStream()) {
            saved = FileStorageUtil.save(in, filename);
        }
        DocumentDto document = new DocumentDto();
        document.setOriginalName(filename);
        document.setStoredName(saved.getStoredName());
        document.setFilePath(saved.getFilePath());
        document.setFileSize(saved.getFileSize());
        document.setMimeType(FileStorageUtil.mimeTypeFor(filename));
        document.setChecksum(saved.getChecksum());

        try {
            roadmapService.submitCertProof(userId, stepId, document);
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
    // 완료 처리한다. handleCompleteProject와 입력 방식은 같고, 대상 단계 타입(PROJECT vs SKILL)과
    // upgradeFromProjectId 유무만 다르다.
    private boolean handleSubmitSkillProject(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException, SQLException {
        Long stepId = Long.valueOf(req.getParameter("stepId"));
        String upgradeParam = trimToNull(req.getParameter("upgradeFromProjectId"));
        Long upgradeFromProjectId = upgradeParam == null ? null : Long.valueOf(upgradeParam);

        ProjectSubmission submission;
        try {
            submission = buildSubmission(req);
        } catch (IllegalArgumentException e) {
            return failWith(req, resp, e.getMessage());
        }

        boolean applied;
        try {
            applied = roadmapService.submitSkillProjectStep(userId, stepId, submission, upgradeFromProjectId);
        } catch (IllegalArgumentException e) {
            deleteNewFiles(submission);
            return failWith(req, resp, e.getMessage());
        } catch (SQLException e) {
            deleteNewFiles(submission);
            throw e;
        }
        if (!applied) {
            deleteNewFiles(submission);
        } else {
            noteSkippedTechNotes(req, submission);
        }
        return true;
    }

    private boolean failWith(HttpServletRequest req, HttpServletResponse resp, String message)
            throws ServletException, IOException {
        req.setAttribute("errorMessage", message);
        doGet(req, resp);
        return false;
    }

    private RoadmapProgress currentProgress(Long userId) throws SQLException {
        RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(userId);
        return roadmap == null ? null : roadmapService.computeProgress(roadmapService.getSteps(roadmap.getId()));
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
