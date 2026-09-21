package com.specodyssey.dto;

import java.time.LocalDateTime;

public class SurveyQuestionDto {

    private Long id;
    private String surveyType;
    private String content;
    private String jobCategoryHint;
    private Integer scoreWeight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSurveyType() {
        return surveyType;
    }

    public void setSurveyType(String surveyType) {
        this.surveyType = surveyType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getJobCategoryHint() {
        return jobCategoryHint;
    }

    public void setJobCategoryHint(String jobCategoryHint) {
        this.jobCategoryHint = jobCategoryHint;
    }

    public Integer getScoreWeight() {
        return scoreWeight;
    }

    public void setScoreWeight(Integer scoreWeight) {
        this.scoreWeight = scoreWeight;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
