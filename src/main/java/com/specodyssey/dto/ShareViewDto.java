package com.specodyssey.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 면접관 뷰에 보여줄 지원자 이력 — 공유 링크의 공개 범위(scope_*)로 걸러낸 읽기 전용 뷰.
 * 관련 요구사항: FR-81 · 84 · 85, NFR-4
 * 공개하지 않은 범위의 값은 아예 채우지 않는다. 지원자를 식별하는 id·로그인 아이디·이메일은 담지 않는다.
 */
public class ShareViewDto {

    private boolean scopeBasic;
    private boolean scopeSkills;
    private boolean scopeGrowth;

    // scope_basic
    private String major;
    private String grade;
    private String desiredJobName;
    private List<TimelineItem> timeline = new ArrayList<>();

    // scope_skills — "Spring Boot · 중급"처럼 바로 출력할 문구
    private List<String> skills = new ArrayList<>();

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
