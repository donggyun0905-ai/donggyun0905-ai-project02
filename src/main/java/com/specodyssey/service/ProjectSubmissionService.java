package com.specodyssey.service;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.ProjectDocumentItemDao;
import com.specodyssey.dao.ProjectTechNoteDao;
import com.specodyssey.dao.SkillAliasDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.ProjectDocumentItemDto;
import com.specodyssey.dto.ProjectTechNoteDto;
import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserProjectDto;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 프로젝트를 완료할 때 받는 것의 저장·검증·미리채움. 관련 요구사항: FR-24 · 개발일지 4-4
 *
 * - 코드 저장소 링크·배포 주소·완료 회고는 참고용이다(완료 판정에 쓰지 않는다).
 * - 문서 체크리스트: 문서 종류마다 "제출" 또는 "해당 없음". README와 실행 화면(SCREENSHOT) 둘만 필수라
 *   "해당 없음"으로 둘 수 없고, 둘 다 제출돼 있어야 완료할 수 있다.
 * - 기술 활용 설명서: 프로젝트에 쓴 기술마다 활용 방법을 문장으로. SKILL 마스터에 있는 기술만 저장한다.
 *
 * 단계 완료를 취소했다가 다시 완료하면 새 프로젝트를 또 만들지 않고, 단계에 연결된 기존 프로젝트
 * (ROADMAP_STEP.evidence_project_id)를 갱신한다. 이미 제출한 문서는 다시 올리지 않아도 된다.
 */
public class ProjectSubmissionService {

    /** 문서 종류와 표시 이름 — 순서가 화면에 보이는 순서다. */
    public static final Map<String, String> DOC_TYPE_LABELS = docTypeLabels();
    public static final Set<String> REQUIRED_DOC_TYPES = Set.of("README", "SCREENSHOT");

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_NOT_APPLICABLE = "NOT_APPLICABLE";

    static final int MAX_URL_LENGTH = 500;
    static final int MAX_RETROSPECTIVE_LENGTH = 1000;
    static final int MAX_NOTE_LENGTH = 1000;

    private static Map<String, String> docTypeLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("README", "README / 프로젝트 소개서");
        m.put("SCREENSHOT", "실행 화면 캡처");
        m.put("PLANNING", "기획서·요구사항 정의서");
        m.put("DESIGN", "설계 문서(아키텍처·ERD)");
        m.put("API_SPEC", "API 명세서");
        m.put("TEST_REPORT", "테스트 결과서");
        m.put("PRESENTATION", "발표자료·데모 영상 링크");
        return java.util.Collections.unmodifiableMap(m);
    }

    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final ProjectDocumentItemDao itemDao = new ProjectDocumentItemDao();
    private final ProjectTechNoteDao noteDao = new ProjectTechNoteDao();
    private final SkillDao skillDao = new SkillDao();
    private final SkillAliasDao skillAliasDao = new SkillAliasDao();

    /** 폼 미리채움용 — 이전에 제출한 프로젝트와 그 문서·기술 설명서. */
    public static final class ProjectDraft {
        private final UserProjectDto project;
        private final Map<String, DraftDoc> docs;
        private final List<ProjectTechNoteDto> notes;

        ProjectDraft(UserProjectDto project, Map<String, DraftDoc> docs, List<ProjectTechNoteDto> notes) {
            this.project = project;
            this.docs = docs;
            this.notes = notes;
        }

        public UserProjectDto getProject() {
            return project;
        }

        /** 문서 종류 → 이전 제출 상태. 제출한 적 없는 종류는 키가 없다. */
        public Map<String, DraftDoc> getDocs() {
            return docs;
        }

        public List<ProjectTechNoteDto> getNotes() {
            return notes;
        }
    }

    public static final class DraftDoc {
        private final boolean notApplicable;
        private final Long documentId;
        private final String documentName;

        DraftDoc(boolean notApplicable, Long documentId, String documentName) {
            this.notApplicable = notApplicable;
            this.documentId = documentId;
            this.documentName = documentName;
        }

        public boolean isNotApplicable() {
            return notApplicable;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public String getDocumentName() {
            return documentName;
        }

        /** 파일이 아직 서류 보관함에 남아 있어 "제출됨"으로 칠 수 있는지 */
        public boolean isSubmitted() {
            return !notApplicable && documentId != null;
        }
    }

    public ProjectDraft loadDraft(Long userId, Long projectId) throws SQLException {
        if (projectId == null) {
            return null;
        }
        UserProjectDto project = userProjectDao.findById(projectId, userId);
        if (project == null) {
            return null;
        }
        return new ProjectDraft(project, currentDocs(projectId), noteDao.findByProjectId(projectId));
    }

    // 이전 제출 상태 — 파일이 서류 보관함에서 지워졌으면 "제출됨"이 아니다(항목은 삭제 때 이미 풀렸지만 한 번 더 확인).
    private Map<String, DraftDoc> currentDocs(Long projectId) throws SQLException {
        Map<String, DraftDoc> docs = new LinkedHashMap<>();
        for (ProjectDocumentItemDto item : itemDao.findByProjectId(projectId)) {
            if (STATUS_NOT_APPLICABLE.equals(item.getStatus())) {
                docs.put(item.getDocType(), new DraftDoc(true, null, null));
            } else if (item.getDocumentId() != null) {
                DocumentDto document = documentDao.findById(item.getDocumentId());
                if (document != null) {
                    docs.put(item.getDocType(), new DraftDoc(false, document.getId(), document.getOriginalName()));
                }
            }
        }
        return docs;
    }

    /**
     * 제출 내용을 검증한다. DB를 읽는 것은 "이전에 제출한 필수 문서가 있는지"뿐이다.
     * @param existingProjectId 이 단계에 연결돼 있던 프로젝트(없으면 null)
     * @throws IllegalArgumentException 입력이 잘못된 경우 — 메시지를 그대로 화면에 보여준다
     */
    public void validate(ProjectSubmission submission, Long existingProjectId) throws SQLException {
        UserProjectDto project = submission.getProject();
        requireUrl(project.getRepoUrl(), "코드 저장소 링크");
        requireUrl(project.getDeployUrl(), "배포 주소");
        if (project.getRetrospective() != null && project.getRetrospective().length() > MAX_RETROSPECTIVE_LENGTH) {
            throw new IllegalArgumentException("완료 회고는 " + MAX_RETROSPECTIVE_LENGTH + "자 이내로 적어주세요.");
        }
        for (ProjectSubmission.TechNote note : submission.getTechNotes()) {
            if (note.getDescription() != null && note.getDescription().length() > MAX_NOTE_LENGTH) {
                throw new IllegalArgumentException("기술 활용 설명은 기술마다 " + MAX_NOTE_LENGTH + "자 이내로 적어주세요.");
            }
        }

        Map<String, DraftDoc> before = existingProjectId == null ? Map.of() : currentDocs(existingProjectId);
        for (String type : DOC_TYPE_LABELS.keySet()) {
            if (!REQUIRED_DOC_TYPES.contains(type)) {
                continue;
            }
            ProjectSubmission.DocSlot slot = submission.getDocs().get(type);
            if (slot != null && slot.isNotApplicable()) {
                throw new IllegalArgumentException(
                        DOC_TYPE_LABELS.get(type) + "는 필수라서 \"해당 없음\"으로 둘 수 없습니다.");
            }
            boolean hasNewFile = slot != null && slot.getFile() != null;
            DraftDoc old = before.get(type);
            boolean hasOldFile = old != null && old.isSubmitted();
            if (!hasNewFile && !hasOldFile) {
                throw new IllegalArgumentException(
                        "README와 실행 화면 캡처는 꼭 제출해야 합니다. 빠진 문서: " + DOC_TYPE_LABELS.get(type));
            }
        }
    }

    /**
     * 프로젝트와 문서·기술 설명서를 저장한다(호출한 트랜잭션 안에서).
     * @param existingProjectId 이 단계에 연결돼 있던 프로젝트. 있으면 새로 만들지 않고 갱신한다
     * @param upgradedFromProjectId "업그레이드"로 완료하는 경우 이전 프로젝트(아니면 null)
     * @return 저장한 프로젝트 id
     */
    public Long save(Connection conn, Long userId, Long stepId, Long existingProjectId,
                     Long upgradedFromProjectId, ProjectSubmission submission) throws SQLException {
        UserProjectDto project = submission.getProject();
        project.setUserId(userId);
        if (upgradedFromProjectId != null) {
            project.setUpgradedFromProjectId(upgradedFromProjectId);
        }

        UserProjectDto existing = existingProjectId == null ? null : userProjectDao.findById(conn, existingProjectId, userId);
        Long projectId;
        if (existing != null) {
            project.setId(existing.getId());
            if (project.getUpgradedFromProjectId() == null) {
                project.setUpgradedFromProjectId(existing.getUpgradedFromProjectId());
            }
            userProjectDao.update(conn, project, userId);
            projectId = existing.getId();
        } else {
            projectId = userProjectDao.insert(conn, project);
        }

        for (Map.Entry<String, ProjectSubmission.DocSlot> entry : submission.getDocs().entrySet()) {
            ProjectSubmission.DocSlot slot = entry.getValue();
            ProjectDocumentItemDto item = new ProjectDocumentItemDto();
            item.setProjectId(projectId);
            item.setDocType(entry.getKey());
            if (slot.isNotApplicable()) {
                item.setStatus(STATUS_NOT_APPLICABLE);
            } else {
                item.setStatus(STATUS_SUBMITTED);
                item.setDocumentId(insertDocument(conn, userId, stepId, projectId, slot.getFile()));
            }
            itemDao.upsert(conn, item);
        }
        for (DocumentDto file : submission.getExtraFiles()) {
            insertDocument(conn, userId, stepId, projectId, file);
        }

        for (ProjectSubmission.TechNote note : submission.getTechNotes()) {
            if (note.getDescription() == null || note.getDescription().isBlank()) {
                continue;
            }
            Long skillId = resolveSkillId(note.getTechName());
            if (skillId == null) {
                submission.getSkippedTechNotes().add(note.getTechName());
                continue;
            }
            ProjectTechNoteDto dto = new ProjectTechNoteDto();
            dto.setProjectId(projectId);
            dto.setSkillId(skillId);
            dto.setDescription(note.getDescription().trim());
            dto.setConsentForTraining(note.isConsentForTraining());
            noteDao.upsert(conn, dto);
        }
        return projectId;
    }

    private Long insertDocument(Connection conn, Long userId, Long stepId, Long projectId, DocumentDto file)
            throws SQLException {
        file.setUserId(userId);
        file.setProjectId(projectId);
        file.setRoadmapStepId(stepId);
        return documentDao.insert(conn, file);
    }

    // 이름이 SKILL.skill_name과 같거나(대소문자 무시), 등록된 별칭이면 그 기술로 본다.
    private Long resolveSkillId(String techName) throws SQLException {
        if (techName == null || techName.isBlank()) {
            return null;
        }
        String name = techName.trim();
        SkillDto skill = skillDao.findByName(name);
        if (skill != null) {
            return skill.getId();
        }
        SkillAliasDto alias = skillAliasDao.findByAliasName(name);
        return alias == null ? null : alias.getSkillId();
    }

    private static void requireUrl(String value, String label) {
        if (value == null) {
            return;
        }
        if (value.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(label + "는 " + MAX_URL_LENGTH + "자 이내로 입력해주세요.");
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!web || uri.getHost() == null) {
                throw new IllegalArgumentException(label + "는 http:// 또는 https://로 시작하는 주소여야 합니다.");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + "는 http:// 또는 https://로 시작하는 올바른 주소여야 합니다.");
        }
    }
}
