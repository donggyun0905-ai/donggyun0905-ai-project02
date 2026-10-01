package com.specodyssey.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserProjectDto {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String techStack;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long upgradedFromProjectId;
    private String repoUrl;        // 코드 저장소 링크 (선택)
    private String deployUrl;      // 배포 주소 (선택)
    private String retrospective;  // 완료 회고 (선택)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTechStack() {
        return techStack;
    }

    public void setTechStack(String techStack) {
        this.techStack = techStack;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Long getUpgradedFromProjectId() {
        return upgradedFromProjectId;
    }

    public void setUpgradedFromProjectId(Long upgradedFromProjectId) {
        this.upgradedFromProjectId = upgradedFromProjectId;
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

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getDeployUrl() {
        return deployUrl;
    }

    public void setDeployUrl(String deployUrl) {
        this.deployUrl = deployUrl;
    }

    public String getRetrospective() {
        return retrospective;
    }

    public void setRetrospective(String retrospective) {
        this.retrospective = retrospective;
    }
}
