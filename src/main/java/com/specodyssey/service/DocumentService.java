package com.specodyssey.service;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.ProjectDocumentItemDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 서류 보관함 — 목록·올리기·연결 프로젝트 변경·삭제. 관련 요구사항: FR-61 · 62 · 63
 *
 * 서류 한 건은 여러 곳에서 가리킨다 — USERS.resume_document_id(이력서)·cover_letter_document_id(자소서),
 * PROJECT_DOCUMENT_ITEM.document_id(프로젝트 문서 체크리스트). 서류를 지우면 이 참조를 같은 트랜잭션에서 풀어야
 * "지웠는데 이력서·체크리스트에는 계속 제출된 것으로 남는" 일이 없다.
 * 로드맵 단계 완료 표시와 점수는 서류를 지워도 그대로 둔다(완료 취소와 마찬가지로 "이미 한 일"은 지우지 않는다).
 */
public class DocumentService {

    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    public static final String ALLOWED_HINT = "PDF, DOCX, HWP, PPTX, TXT, MD, PNG, JPG, ZIP 등 · 파일당 최대 20MB";

    private final DocumentDao documentDao = new DocumentDao();
    private final UserDao userDao = new UserDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final ProjectDocumentItemDao projectDocumentItemDao = new ProjectDocumentItemDao();

    /** 서류로 올릴 수 있는 파일 형식인지 — 디스크에 쓰기 전에 확인한다. 허용 목록은 FileStorageUtil 한 곳에만 있다. */
    public static boolean isAllowedFile(String originalFilename) {
        return FileStorageUtil.isAllowedFile(originalFilename);
    }

    /** 화면에 보여줄 서류 한 줄. JSP는 출력만 하므로 용도·크기·날짜를 여기서 만들어 넘긴다. */
    public static final class DocumentView {
        private final Long id;
        private final String originalName;
        private final Long projectId;
        private final String projectTitle;
        private final String purpose;
        private final String sizeLabel;
        private final String uploadedDate;

        DocumentView(Long id, String originalName, Long projectId, String projectTitle, String purpose,
                     String sizeLabel, String uploadedDate) {
            this.id = id;
            this.originalName = originalName;
            this.projectId = projectId;
            this.projectTitle = projectTitle;
            this.purpose = purpose;
            this.sizeLabel = sizeLabel;
            this.uploadedDate = uploadedDate;
        }

        public Long getId() {
            return id;
        }

        public String getOriginalName() {
            return originalName;
        }

        public Long getProjectId() {
            return projectId;
        }

        public String getProjectTitle() {
            return projectTitle;
        }

        /** 이력서 / 자소서 / 연습장 노트 / 로드맵 증빙 / 프로젝트 서류 / 빈 문자열(일반 서류) */
        public String getPurpose() {
            return purpose;
        }

        public String getSizeLabel() {
            return sizeLabel;
        }

        public String getUploadedDate() {
            return uploadedDate;
        }
    }

    public List<DocumentView> listViews(Long userId) throws SQLException {
        UserDto user = userDao.findById(userId);
        Map<Long, String> projectTitles = userProjectDao.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserProjectDto::getId, UserProjectDto::getTitle, (a, b) -> a));
        List<DocumentView> views = new ArrayList<>();
        for (DocumentDto d : documentDao.findByUserId(userId)) {
            String purpose = "";
            if (user != null && d.getId().equals(user.getResumeDocumentId())) {
                purpose = "이력서";
            } else if (user != null && d.getId().equals(user.getCoverLetterDocumentId())) {
                purpose = "자소서";
            } else if (NoteService.NOTE_FILE_NAME.equals(d.getOriginalName())) {
                purpose = "연습장 노트";
            } else if (d.getRoadmapStepId() != null) {
                purpose = "로드맵 증빙";
            } else if (d.getProjectId() != null) {
                purpose = "프로젝트 서류";
            }
            views.add(new DocumentView(d.getId(), d.getOriginalName(), d.getProjectId(),
                    d.getProjectId() == null ? null : projectTitles.get(d.getProjectId()), purpose,
                    sizeLabel(d.getFileSize()), d.getCreatedAt() == null ? "" : d.getCreatedAt().toLocalDate().toString()));
        }
        return views;
    }

    /** 내 프로젝트 목록 — 올릴 때·연결을 바꿀 때 고르는 목록. */
    public List<UserProjectDto> listProjects(Long userId) throws SQLException {
        return userProjectDao.findByUserId(userId);
    }

    /**
     * 이미 디스크에 저장된 파일을 서류로 등록한다.
     * @param projectId 연결할 프로젝트(선택). 내 프로젝트가 아니면 거절한다.
     * @throws IllegalArgumentException 입력이 잘못된 경우 — 메시지를 그대로 화면에 보여준다
     */
    public Long upload(Long userId, DocumentDto document, Long projectId) throws SQLException {
        requireOwnProject(userId, projectId);
        document.setUserId(userId);
        document.setProjectId(projectId);
        document.setRoadmapStepId(null);
        return documentDao.insert(document);
    }

    /** 연결 프로젝트 변경(FR-63). projectId가 null이면 연결을 푼다. 내 서류·내 프로젝트가 아니면 false. */
    public boolean changeProject(Long userId, Long documentId, Long projectId) throws SQLException {
        requireOwnProject(userId, projectId);
        return TransactionUtil.runInTransaction(conn -> documentDao.updateProject(conn, documentId, userId, projectId));
    }

    /**
     * 서류를 지운다(논리 삭제 + 디스크 파일 삭제). 이 서류를 가리키던 이력서·자소서 지정과 프로젝트 문서
     * 체크리스트 줄도 같이 푼다. 내 서류가 아니면 아무것도 하지 않고 false.
     */
    public boolean delete(Long userId, Long documentId) throws SQLException {
        DocumentDto document = documentDao.findById(documentId);
        if (document == null || !userId.equals(document.getUserId())) {
            return false;
        }
        UserDto user = userDao.findById(userId);
        TransactionUtil.runInTransaction(conn -> {
            if (user != null && documentId.equals(user.getResumeDocumentId())) {
                userDao.updateResumeDocument(conn, userId, null);
            }
            if (user != null && documentId.equals(user.getCoverLetterDocumentId())) {
                userDao.updateCoverLetterDocument(conn, userId, null);
            }
            projectDocumentItemDao.softDeleteByDocumentId(conn, documentId, userId);
            documentDao.delete(conn, documentId, userId);
            return null;
        });
        // DB가 확정된 뒤에 디스크에서 지운다 — 먼저 지우면 롤백됐을 때 파일만 사라진다
        FileStorageUtil.deleteQuietly(document.getFilePath());
        return true;
    }

    private void requireOwnProject(Long userId, Long projectId) throws SQLException {
        if (projectId == null) {
            return;
        }
        boolean mine = userProjectDao.findByUserId(userId).stream().anyMatch(p -> projectId.equals(p.getId()));
        if (!mine) {
            throw new IllegalArgumentException("내 프로젝트만 연결할 수 있습니다.");
        }
    }

    static String sizeLabel(Long bytes) {
        if (bytes == null) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
