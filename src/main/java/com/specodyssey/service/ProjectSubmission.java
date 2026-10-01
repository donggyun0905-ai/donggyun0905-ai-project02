package com.specodyssey.service;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserProjectDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 프로젝트 단계를 완료할 때 한 번에 받는 것 — 프로젝트 정보, 기술 활용 설명서, 문서 체크리스트, 기타 증빙 파일.
 * (개발일지 4-4) 서블릿이 폼에서 만들어 서비스에 넘기고, 파일은 이미 디스크에 저장돼 있다.
 */
public class ProjectSubmission {

    /** 기술 한 개의 활용 설명. 기술 이름은 사용자가 입력한 그대로이고, SKILL 마스터와의 매칭은 저장할 때 한다. */
    public static final class TechNote {
        private final String techName;
        private final String description;
        private final boolean consentForTraining;

        public TechNote(String techName, String description, boolean consentForTraining) {
            this.techName = techName;
            this.description = description;
            this.consentForTraining = consentForTraining;
        }

        public String getTechName() {
            return techName;
        }

        public String getDescription() {
            return description;
        }

        public boolean isConsentForTraining() {
            return consentForTraining;
        }
    }

    /** 문서 종류 하나의 입력 — 새 파일을 올리거나("제출"), "해당 없음"을 고른다. 둘 다 아니면 슬롯을 만들지 않는다. */
    public static final class DocSlot {
        private final DocumentDto file;
        private final boolean notApplicable;

        private DocSlot(DocumentDto file, boolean notApplicable) {
            this.file = file;
            this.notApplicable = notApplicable;
        }

        public static DocSlot submitted(DocumentDto file) {
            return new DocSlot(file, false);
        }

        public static DocSlot notApplicable() {
            return new DocSlot(null, true);
        }

        public DocumentDto getFile() {
            return file;
        }

        public boolean isNotApplicable() {
            return notApplicable;
        }
    }

    private final UserProjectDto project;
    private final List<TechNote> techNotes = new ArrayList<>();
    private final Map<String, DocSlot> docs = new LinkedHashMap<>();
    private final List<DocumentDto> extraFiles = new ArrayList<>();
    // 저장 결과: SKILL 마스터에 없는 기술이라 설명서를 저장하지 못한 기술 이름 — 화면에 알려준다
    private final List<String> skippedTechNotes = new ArrayList<>();

    public ProjectSubmission(UserProjectDto project) {
        this.project = project;
    }

    public UserProjectDto getProject() {
        return project;
    }

    public List<TechNote> getTechNotes() {
        return techNotes;
    }

    public Map<String, DocSlot> getDocs() {
        return docs;
    }

    public List<DocumentDto> getExtraFiles() {
        return extraFiles;
    }

    public List<String> getSkippedTechNotes() {
        return skippedTechNotes;
    }

    /** 이번 제출로 디스크에 새로 써진 파일 전부 — 저장에 실패하거나 반영이 안 됐을 때 지우려고 쓴다. */
    public List<DocumentDto> allNewFiles() {
        List<DocumentDto> files = new ArrayList<>();
        for (DocSlot slot : docs.values()) {
            if (slot.getFile() != null) {
                files.add(slot.getFile());
            }
        }
        files.addAll(extraFiles);
        return files;
    }
}
