package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ProjectSubmission;
import com.specodyssey.service.ProjectSubmissionService;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.util.TransactionUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 프로필에서 프로젝트를 "전부" 입력하는 화면 (2026-10-07 사용자 요청).
 * 관련 요구사항: FR-24 · FR-81
 *
 * /profile의 프로젝트 칸은 제목·설명·기술스택·기간·링크·팀 정보만 받았다. 그런데 면접관 화면에 보이는
 * 값에는 완료 회고(USER_PROJECTS.retrospective) · 기술 활용 설명서(PROJECT_TECH_NOTE) ·
 * 제출 서류(PROJECT_DOCUMENT_ITEM) · 증빙 파일(DOCUMENTS)도 있다. 그 네 가지는 로드맵 PROJECT 단계를
 * 완료할 때만 채울 수 있어서, 프로필에서 직접 적은 프로젝트는 면접관에게 거의 빈 칸으로 보였다.
 *
 * 그래서 로드맵 제출이 쓰는 ProjectSubmissionService·RoadmapSubmissionForm을 그대로 재사용한다 —
 * 입력칸(common/project-submit-fields.jsp)까지 같은 파일이라 두 화면이 받는 항목이 갈라지지 않는다.
 * 다른 점은 하나뿐이다: README·실행 화면 캡처를 필수로 받지 않는다(validate의 requireDocs=false).
 * 로드맵에서는 그 서류가 "단계를 끝냈다"는 증빙이지만, 프로필은 예전에 한 프로젝트를 적어 두는 자리다.
 *
 * 간단히 추가하는 칸은 /profile에 그대로 남겨 뒀다(ProfileProjectServlet) — 짧게 적고 넘어가는 길을
 * 막지 않으려고. 이 화면은 거기서 "자세히 입력"으로 들어온다.
 */
@WebServlet("/profile/projects/edit")
@MultipartConfig(
        maxFileSize = 20L * 1024 * 1024,       // 파일 1개당 20MB — 로드맵 제출과 같은 한도
        maxRequestSize = 100L * 1024 * 1024,   // 요청 전체 100MB (서류 여러 개 + 증빙 파일)
        fileSizeThreshold = 0
)
public class ProfileProjectEditServlet extends HttpServlet {

    private final ProjectSubmissionService projectSubmissionService = new ProjectSubmissionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = loginUserId(req);
        Long projectId = parseId(req.getParameter("projectId"));

        try {
            // 남의 프로젝트 id를 넣으면 loadDraft가 null을 준다(findById가 user_id로 걸러낸다) — 그때는
            // 수정이 아니라 "추가"로 떨어진다. 남의 내용이 화면에 뜨지 않으면 되고, 400을 돌려줄 필요는 없다.
            ProjectSubmissionService.ProjectDraft draft = projectSubmissionService.loadDraft(userId, projectId);
            req.setAttribute("draft", draft);
            req.setAttribute("projectId", draft == null ? null : projectId);
        } catch (SQLException e) {
            throw new ServletException("프로젝트를 불러오는 중 오류가 발생했습니다.", e);
        }
        putFormAttributes(req);
        ProfileNotice.consume(req); // 저장 실패 안내를 이 화면에서도 보여 준다
        req.getRequestDispatcher("/WEB-INF/views/profile-project-edit.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = loginUserId(req);
        Long projectId = parseId(req.getParameter("projectId"));

        ProjectSubmission submission;
        try {
            submission = RoadmapSubmissionForm.buildSubmission(req);
        } catch (IllegalArgumentException e) {
            // 입력 실수는 에러 페이지가 아니라 폼으로 돌려보낸다 — 적던 내용을 잃지 않게
            redirectBackWithError(req, resp, projectId, e.getMessage());
            return;
        }

        try {
            projectSubmissionService.validate(submission, projectId, false);
            Long saved = projectId;
            TransactionUtil.runInTransaction(conn ->
                    projectSubmissionService.save(conn, userId, null, saved, null, submission));
        } catch (IllegalArgumentException e) {
            deleteUploadedFiles(submission); // 저장이 안 됐으면 방금 받은 파일도 남기지 않는다
            redirectBackWithError(req, resp, projectId, e.getMessage());
            return;
        } catch (SQLException e) {
            deleteUploadedFiles(submission);
            throw new ServletException("프로젝트 저장 중 오류가 발생했습니다.", e);
        }

        if (!submission.getSkippedTechNotes().isEmpty()) {
            // 아는 기술 이름이 아니면 설명서를 저장할 수 없다 — 프로젝트는 저장됐으니 그 사실만 알려 준다
            ProfileNotice.putError(req, "기술 활용 설명서에서 알 수 없는 기술은 빼고 저장했어요: "
                    + String.join(", ", submission.getSkippedTechNotes()));
        }
        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    /** 입력칸(project-submit-fields.jsp)이 쓰는 값. profileMode=true면 서류를 필수로 요구하지 않는다. */
    private static void putFormAttributes(HttpServletRequest req) {
        req.setAttribute("docTypeLabels", ProjectSubmissionService.DOC_TYPE_LABELS);
        req.setAttribute("requiredDocTypes", ProjectSubmissionService.REQUIRED_DOC_TYPES);
        req.setAttribute("profileMode", true);
    }

    private void redirectBackWithError(HttpServletRequest req, HttpServletResponse resp, Long projectId,
                                       String message) throws IOException {
        ProfileNotice.putError(req, message);
        resp.sendRedirect(req.getContextPath() + "/profile/projects/edit"
                + (projectId == null ? "" : "?projectId=" + projectId));
    }

    /** 저장에 실패하면 디스크에 쓴 파일을 지운다 — 아무 데서도 가리키지 않는 파일이 쌓이지 않게 */
    private static void deleteUploadedFiles(ProjectSubmission submission) {
        submission.allNewFiles().forEach(file -> FileStorageUtil.deleteQuietly(file.getFilePath()));
    }

    private static Long loginUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return ((UserDto) session.getAttribute("loginUser")).getId();
    }

    /** 숫자가 아니면 "추가"로 본다 — 주소를 손으로 고친 경우라 에러 페이지까지 띄울 일은 아니다. */
    private static Long parseId(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
