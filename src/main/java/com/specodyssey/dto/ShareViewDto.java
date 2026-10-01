package com.specodyssey.dto;

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

    // scope_basic
    private String name;
    private String major;
    private String grade;
    private String desiredJobName;
    private List<TimelineItem> timeline = new ArrayList<>();
    private List<String> certNames = new ArrayList<>(); // 비교 뷰용 — 자격증 이름만
    private int projectCount;

    // scope_skills — "Spring Boot · 중급"처럼 바로 출력할 문구
    private List<String> skills = new ArrayList<>();
    // 비교 뷰의 적합도 계산용. 기술은 격차 분석을 돌려야 SKILL 마스터에 매칭(skill_id)되므로,
    // 아직 매칭되지 않은 기술은 입력한 이름(소문자)으로 비교한다.
    private Set<Long> skillIds = new HashSet<>();
    private Set<String> skillNames = new HashSet<>();

    // scope_resume — 이력서 파일 이름. 공개했지만 올린 이력서가 없으면 null
    private String resumeFileName;

    // scope_growth — 날짜 오름차순
    private List<SpecScoreHistoryDto> growth = new ArrayList<>();

    /** 타임라인 한 줄 (FR-81). */
    public static class TimelineItem {
        private final String dateText;
        private final String typeLabel;
        private final String title;
        private final String detail;

        public TimelineItem(String dateText, String typeLabel, String title, String detail) {
            this.dateText = dateText;
            this.typeLabel = typeLabel;
            this.title = title;
            this.detail = detail;
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

    public String getResumeFileName() {
        return resumeFileName;
    }

    public void setResumeFileName(String resumeFileName) {
        this.resumeFileName = resumeFileName;
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
