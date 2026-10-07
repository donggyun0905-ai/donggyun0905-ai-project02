package com.specodyssey.dto;

import com.specodyssey.util.FileStorageUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 면접관 뷰에 보여줄 지원자 이력 — 공유 링크의 공개 범위(scope_*)로 걸러낸 읽기 전용 뷰.
 * 관련 요구사항: FR-81 · 84 · 85, NFR-4
 * 공개하지 않은 범위의 값은 아예 채우지 않는다. 지원자를 식별하는 id·로그인 아이디·이메일은 담지 않는다.
 */
public class ShareViewDto {

    private boolean scopeBasic;
    private boolean scopeSkills;
    private boolean scopeGrowth;
    private boolean scopeResume;
    private boolean scopeCoverLetter;
    private boolean scopeAge;
    private boolean scopeProjectDocs;
    private boolean scopeEducation;

    // scope_basic
    private String name;
    private Integer age; // scopeAge일 때만. 미입력이면 null
    private String major;
    private String grade;
    private String desiredJobName;
    private List<TimelineItem> timeline = new ArrayList<>();
    private List<String> certNames = new ArrayList<>(); // 비교 뷰용 — 자격증 이름만
    private int projectCount;

    // scope_skills — "Spring Boot · 중급"처럼 바로 출력할 문구
    private List<String> skills = new ArrayList<>();
    private List<SkillItem> skillItems = new ArrayList<>(); // skills와 같은 순서, 숙련도·근거 프로젝트 포함
    // 비교 뷰의 적합도 계산용. 기술은 격차 분석을 돌려야 SKILL 마스터에 매칭(skill_id)되므로,
    // 아직 매칭되지 않은 기술은 입력한 이름(소문자)으로 비교한다.
    private Set<Long> skillIds = new HashSet<>();
    private Set<String> skillNames = new HashSet<>();

    // scope_resume — 이력서 파일 이름. 공개했지만 올린 이력서가 없으면 null
    private String resumeFileName;

    // scope_cover_letter — 자소서 파일 이름. 공개했지만 올린 자소서가 없으면 null
    private String coverLetterFileName;

    // scope_growth — 날짜 오름차순
    private List<SpecScoreHistoryDto> growth = new ArrayList<>();
    // 학력 공개 링크에서만 채운다. 지원자가 입력하지 않았으면 null
    private UserEducationDto education;

    /** 타임라인 항목에 붙는 링크 한 개(이름 + 주소). */
    public static class Link {
        private final String label;
        private final String url;

        public Link(String label, String url) {
            this.label = label;
            this.url = url;
        }

        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }
    }

    /**
     * 보유 기술 하나 — 숙련도와 "어느 프로젝트에서 썼는지" 근거. 면접관 뷰 기술 카드와 적합도 계산이 같이 쓴다.
     * 근거 프로젝트는 기본 이력(프로젝트 목록)도 공개한 링크에서만 채운다.
     */
    public static class SkillItem {
        private final String label;        // "Java · 중급" — 기존 skills 목록과 같은 표기
        private final Long skillId;        // 마스터 매칭 전이면 null
        private final String nameKey;      // 입력한 이름(소문자·앞뒤 공백 제거) — 매칭 전 기술 맞추기용
        private final String proficiency;  // BEGINNER / INTERMEDIATE / ADVANCED, 미입력이면 null
        private final List<String> projectTitles;

        public SkillItem(String label, Long skillId, String nameKey, String proficiency, List<String> projectTitles) {
            this.label = label;
            this.skillId = skillId;
            this.nameKey = nameKey;
            this.proficiency = proficiency;
            this.projectTitles = projectTitles;
        }

        public String getLabel() {
            return label;
        }

        public Long getSkillId() {
            return skillId;
        }

        public String getNameKey() {
            return nameKey;
        }

        public String getProficiency() {
            return proficiency;
        }

        public List<String> getProjectTitles() {
            return projectTitles;
        }
    }

    /** 프로젝트에 제출한 서류 한 종류 (PROJECT_DOCUMENT_ITEM). */
    public static class SubmittedDoc {
        private final String docType;
        private final String label;      // "README / 프로젝트 소개서"
        private final Long sourceDocumentId; // 서비스 안에서만 — 파일을 열 수 있는지 판단용
        private Long documentId;         // 공개한 링크에서만 채운다. null이면 종류 이름만 보인다
        private String fileName;
        private String previewType;      // "pdf" / "image" — 화면 안 미리보기 가능 형식. 그 외 null

        public SubmittedDoc(String docType, String label, Long sourceDocumentId) {
            this.docType = docType;
            this.label = label;
            this.sourceDocumentId = sourceDocumentId;
        }

        public String getDocType() {
            return docType;
        }

        public String getLabel() {
            return label;
        }

        public Long getSourceDocumentId() {
            return sourceDocumentId;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public String getFileName() {
            return fileName;
        }

        public String getPreviewType() {
            return previewType;
        }

        public void share(Long documentId, String fileName, String previewType) {
            this.documentId = documentId;
            this.fileName = fileName;
            this.previewType = previewType;
        }
    }

    /** 프로젝트에서 기술 하나를 어떻게 썼는지 (PROJECT_TECH_NOTE). */
    public static class TechNote {
        private final String skillName;
        private final String description;

        public TechNote(String skillName, String description) {
            this.skillName = skillName;
            this.description = description;
        }

        public String getSkillName() {
            return skillName;
        }

        public String getDescription() {
            return description;
        }
    }

    /** 타임라인 한 줄 (FR-81). */
    public static class TimelineItem {
        private final String dateText;
        private final String typeLabel;
        private final String title;
        private final String detail;
        // CERT 항목이면서 완료 당시 증빙 서류를 첨부했을 때만 채워진다 — 그 외엔 null
        // (화면에서 null이면 "서류 보기" 링크를 안 보여준다).
        private final Long documentId;
        // 프로젝트 항목의 저장소·배포·기타 링크. 웹 주소(http/https)만 담긴다 — 없으면 빈 목록
        private final List<Link> links;
        // 프로젝트 항목에만 — 면접관이 "무엇을 배우고 해결했는지"를 보는 곳. 없으면 null·빈 목록
        private String retrospective;
        private List<TechNote> techNotes = List.of();
        // 프로젝트에 제출한 서류. 파일은 "프로젝트 서류"를 공개한 링크에서만 열린다(documentId가 채워짐)
        private List<SubmittedDoc> submittedDocs = List.of();
        // 팀 규모·본인 역할 — 면접관이 기여도를 가장 먼저 확인하는 값. 미입력이면 null
        private Integer teamSize;
        private String myRole;

        public TimelineItem(String dateText, String typeLabel, String title, String detail) {
            this(dateText, typeLabel, title, detail, null);
        }

        public TimelineItem(String dateText, String typeLabel, String title, String detail, Long documentId) {
            this(dateText, typeLabel, title, detail, documentId, List.of());
        }

        public TimelineItem(String dateText, String typeLabel, String title, String detail, Long documentId,
                            List<Link> links) {
            this.dateText = dateText;
            this.typeLabel = typeLabel;
            this.title = title;
            this.detail = detail;
            this.documentId = documentId;
            this.links = links;
        }

        public List<Link> getLinks() {
            return links;
        }

        public String getRetrospective() {
            return retrospective;
        }

        public void setRetrospective(String retrospective) {
            this.retrospective = retrospective;
        }

        public List<TechNote> getTechNotes() {
            return techNotes;
        }

        public void setTechNotes(List<TechNote> techNotes) {
            this.techNotes = techNotes;
        }

        public List<SubmittedDoc> getSubmittedDocs() {
            return submittedDocs;
        }

        public void setSubmittedDocs(List<SubmittedDoc> submittedDocs) {
            this.submittedDocs = submittedDocs;
        }

        public Integer getTeamSize() {
            return teamSize;
        }

        public void setTeamSize(Integer teamSize) {
            this.teamSize = teamSize;
        }

        public String getMyRole() {
            return myRole;
        }

        public void setMyRole(String myRole) {
            this.myRole = myRole;
        }

        /** "4인 팀 · 백엔드·DB 설계" / "개인 프로젝트" — 둘 다 없으면 null */
        public String getTeamText() {
            String team = teamSize == null ? null : teamSize == 1 ? "개인 프로젝트" : teamSize + "인 팀";
            if (team == null) {
                return myRole;
            }
            return myRole == null ? team : team + " · " + myRole;
        }

        /** 펼쳐 볼 상세가 하나라도 있는지 — 화면에서 "상세 보기"를 보여줄지 정한다. */
        public boolean isHasProjectDetail() {
            return retrospective != null || !techNotes.isEmpty() || !submittedDocs.isEmpty();
        }

        public String getDateText() {
            return dateText;
        }

        public String getTypeLabel() {
            return typeLabel;
        }

        public String getTitle() {
            return title;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public String getDetail() {
            return detail;
        }
    }

    public boolean isScopeBasic() {
        return scopeBasic;
    }

    public void setScopeBasic(boolean scopeBasic) {
        this.scopeBasic = scopeBasic;
    }

    public boolean isScopeSkills() {
        return scopeSkills;
    }

    public void setScopeSkills(boolean scopeSkills) {
        this.scopeSkills = scopeSkills;
    }

    public boolean isScopeGrowth() {
        return scopeGrowth;
    }

    public void setScopeGrowth(boolean scopeGrowth) {
        this.scopeGrowth = scopeGrowth;
    }

    public boolean isScopeResume() {
        return scopeResume;
    }

    public void setScopeResume(boolean scopeResume) {
        this.scopeResume = scopeResume;
    }

    public boolean isScopeCoverLetter() {
        return scopeCoverLetter;
    }

    public void setScopeCoverLetter(boolean scopeCoverLetter) {
        this.scopeCoverLetter = scopeCoverLetter;
    }

    public boolean isScopeAge() {
        return scopeAge;
    }

    public void setScopeAge(boolean scopeAge) {
        this.scopeAge = scopeAge;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getResumeFileName() {
        return resumeFileName;
    }

    public void setResumeFileName(String resumeFileName) {
        this.resumeFileName = resumeFileName;
    }

    public String getCoverLetterFileName() {
        return coverLetterFileName;
    }

    public void setCoverLetterFileName(String coverLetterFileName) {
        this.coverLetterFileName = coverLetterFileName;
    }

    // PDF면 면접관 뷰에서 화면 안에 원본 그대로 보여준다. 다른 형식(DOCX·HWP)은 브라우저가 못 그려서 내려받기만.
    public boolean isResumePdf() {
        return FileStorageUtil.isPdf(resumeFileName);
    }

    public boolean isCoverLetterPdf() {
        return FileStorageUtil.isPdf(coverLetterFileName);
    }

    public boolean isScopeProjectDocs() {
        return scopeProjectDocs;
    }

    public void setScopeProjectDocs(boolean scopeProjectDocs) {
        this.scopeProjectDocs = scopeProjectDocs;
    }

    public boolean isScopeEducation() {
        return scopeEducation;
    }

    public void setScopeEducation(boolean scopeEducation) {
        this.scopeEducation = scopeEducation;
    }

    public UserEducationDto getEducation() {
        return education;
    }

    public void setEducation(UserEducationDto education) {
        this.education = education;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getDesiredJobName() {
        return desiredJobName;
    }

    public void setDesiredJobName(String desiredJobName) {
        this.desiredJobName = desiredJobName;
    }

    public List<TimelineItem> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelineItem> timeline) {
        this.timeline = timeline;
    }

    public List<String> getCertNames() {
        return certNames;
    }

    public void setCertNames(List<String> certNames) {
        this.certNames = certNames;
    }

    public int getProjectCount() {
        return projectCount;
    }

    public void setProjectCount(int projectCount) {
        this.projectCount = projectCount;
    }

    public Set<Long> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(Set<Long> skillIds) {
        this.skillIds = skillIds;
    }

    public Set<String> getSkillNames() {
        return skillNames;
    }

    public void setSkillNames(Set<String> skillNames) {
        this.skillNames = skillNames;
    }

    public List<String> getSkills() {
        return skills;
    }

    public List<SkillItem> getSkillItems() {
        return skillItems;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<SpecScoreHistoryDto> getGrowth() {
        return growth;
    }

    public void setGrowth(List<SpecScoreHistoryDto> growth) {
        this.growth = growth;
    }
}
