package com.specodyssey.dto;

/**
 * 미션 화면에 보여줄 "오늘의 문제 1건" — USER_DAILY_MISSION + PROBLEM을 합친 읽기 전용 뷰.
 * 관련 요구사항: FR-51 일일 미션
 * 문제 지문은 담지 않는다(링크 추천형 — TD-3). 제목·난이도·출처 링크만 보여준다.
 */
public class DailyMissionViewDto {

    private Long missionId;
    private Long problemId;
    private String title;
    private Integer difficultyLevel;
    private String category;
    private String externalUrl;
    private String sourceLabel;
    private boolean completed;
    private Boolean correct;
    private String submittedCode;
    private String submittedLanguage;

    public String getSubmittedCode() {
        return submittedCode;
    }

    public void setSubmittedCode(String submittedCode) {
        this.submittedCode = submittedCode;
    }

    public String getSubmittedLanguage() {
        return submittedLanguage;
    }

    public void setSubmittedLanguage(String submittedLanguage) {
        this.submittedLanguage = submittedLanguage;
    }

    public Long getMissionId() {
        return missionId;
    }

    public void setMissionId(Long missionId) {
        this.missionId = missionId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(Integer difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }
}
