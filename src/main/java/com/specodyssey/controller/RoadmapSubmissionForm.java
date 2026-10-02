package com.specodyssey.controller;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.service.ProjectSubmission;
import com.specodyssey.service.ProjectSubmissionService;
import com.specodyssey.util.FileStorageUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.time.LocalDate;

/**
 * 프로젝트 제출 폼(PROJECT·CORE/ADVANCED 단계)을 읽어 ProjectSubmission으로 만들고 파일을 저장하는 도우미.
 * RoadmapServlet 안에 있던 것을 그대로 옮겼다 — 폼 필드 이름: doc_<종류>(제출) · na_<종류>(해당 없음) ·
 * techName_i/techDesc_i/techConsent_i(기술 활용 설명서) · files(기타 증빙).
 */
final class RoadmapSubmissionForm {

    private RoadmapSubmissionForm() {
    }

    private static final int MAX_TECH_NOTES = 5;

    // 폼 → ProjectSubmission. 문서 종류마다 파일 파트 doc_<TYPE>(제출) 또는 체크박스 na_<TYPE>(해당 없음),
    // 기술 활용 설명서는 techName_i / techDesc_i / techConsent_i, 기타 증빙은 files 파트.
    // 파일은 여기서 디스크에 저장한다 — 실패하면 이미 쓴 파일을 지우고 IllegalArgumentException으로 알린다.
    static ProjectSubmission buildSubmission(HttpServletRequest req) throws ServletException, IOException {
        UserProjectDto project = new UserProjectDto();
        project.setTitle(trimToNull(req.getParameter("title")));
        project.setDescription(trimToNull(req.getParameter("description")));
        project.setTechStack(trimToNull(req.getParameter("techStack")));
        project.setRepoUrl(trimToNull(req.getParameter("repoUrl")));
        project.setDeployUrl(trimToNull(req.getParameter("deployUrl")));
        project.setRetrospective(trimToNull(req.getParameter("retrospective")));
        try {
            project.setStartDate(parseDate(req.getParameter("startDate")));
            project.setEndDate(parseDate(req.getParameter("endDate")));
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다.");
        }
        if (project.getTitle() == null || project.getDescription() == null || project.getTechStack() == null) {
            throw new IllegalArgumentException("프로젝트명·설명·기술스택은 모두 필수입니다.");
        }

        ProjectSubmission submission = new ProjectSubmission(project);
        submission.setLinks(ProjectLinkForm.parse(req));
        for (int i = 0; i < MAX_TECH_NOTES; i++) {
            String name = trimToNull(req.getParameter("techName_" + i));
            String desc = trimToNull(req.getParameter("techDesc_" + i));
            if (name != null && desc != null) {
                submission.getTechNotes().add(new ProjectSubmission.TechNote(name, desc,
                        req.getParameter("techConsent_" + i) != null));
            }
        }

        try {
            for (String type : ProjectSubmissionService.DOC_TYPE_LABELS.keySet()) {
                Part part = req.getPart("doc_" + type);
                if (part != null && part.getSubmittedFileName() != null && !part.getSubmittedFileName().isBlank()) {
                    submission.getDocs().put(type, ProjectSubmission.DocSlot.submitted(savePart(part)));
                } else if (req.getParameter("na_" + type) != null) {
                    submission.getDocs().put(type, ProjectSubmission.DocSlot.notApplicable());
                }
            }
            for (Part part : req.getParts()) {
                if ("files".equals(part.getName()) && part.getSubmittedFileName() != null
                        && !part.getSubmittedFileName().isBlank()) {
                    submission.getExtraFiles().add(savePart(part));
                }
            }
        } catch (IllegalStateException e) {
            // 컨테이너가 @MultipartConfig의 maxFileSize/maxRequestSize 초과를 이렇게(비검사 예외) 알린다.
            deleteNewFiles(submission);
            throw new IllegalArgumentException("첨부 파일 용량이 너무 큽니다 (파일당 20MB, 전체 100MB 이하).");
        } catch (IllegalArgumentException | IOException e) {
            deleteNewFiles(submission);
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("파일 저장 중 오류가 발생했습니다.");
        }
        return submission;
    }

    static DocumentDto savePart(Part part) throws IOException {
        if (!FileStorageUtil.isAllowedFile(part.getSubmittedFileName())) {
            throw new IllegalArgumentException("업로드할 수 없는 파일 형식입니다: " + part.getSubmittedFileName());
        }
        FileStorageUtil.SavedFile saved;
        try (var in = part.getInputStream()) {
            saved = FileStorageUtil.save(in, part.getSubmittedFileName());
        }
        DocumentDto document = new DocumentDto();
        document.setOriginalName(part.getSubmittedFileName());
        document.setStoredName(saved.getStoredName());
        document.setFilePath(saved.getFilePath());
        document.setFileSize(saved.getFileSize());
        document.setMimeType(FileStorageUtil.mimeTypeFor(part.getSubmittedFileName()));
        document.setChecksum(saved.getChecksum());
        return document;
    }

    static void deleteNewFiles(ProjectSubmission submission) {
        for (DocumentDto document : submission.allNewFiles()) {
            FileStorageUtil.deleteQuietly(document.getFilePath());
        }
    }

    // SKILL 마스터에 없는 기술이라 설명서를 저장하지 못한 것은 완료 후 안내 메시지로 알린다.
    static void noteSkippedTechNotes(HttpServletRequest req, ProjectSubmission submission) {
        if (!submission.getSkippedTechNotes().isEmpty()) {
            req.getSession().setAttribute("roadmapNotice", "기술 활용 설명 중 등록되지 않은 기술은 저장하지 않았습니다: "
                    + String.join(", ", submission.getSkippedTechNotes()));
        }
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }
}
