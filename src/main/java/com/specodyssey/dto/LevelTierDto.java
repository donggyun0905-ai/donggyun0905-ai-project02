package com.specodyssey.dto;

import java.time.LocalDateTime;

public class LevelTierDto {

    private Long id;
    private Integer minScore;
    private Integer maxScore;
    private String tierName;
    private String titleName;
    private Integer problemLevelMin;
    private Integer problemLevelMax;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getMinScore() {
        return minScore;
    }

    public void setMinScore(Integer minScore) {
        this.minScore = minScore;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public String getTierName() {
        return tierName;
    }

    public void setTierName(String tierName) {
        this.tierName = tierName;
    }

    public String getTitleName() {
        return titleName;
    }

    public void setTitleName(String titleName) {
        this.titleName = titleName;
    }

    public Integer getProblemLevelMin() {
        return problemLevelMin;
    }

    public void setProblemLevelMin(Integer problemLevelMin) {
        this.problemLevelMin = problemLevelMin;
    }

    public Integer getProblemLevelMax() {
        return problemLevelMax;
    }

    public void setProblemLevelMax(Integer problemLevelMax) {
        this.problemLevelMax = problemLevelMax;
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
