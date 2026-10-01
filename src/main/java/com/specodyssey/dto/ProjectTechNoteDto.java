package com.specodyssey.dto;

import java.time.LocalDateTime;

/** PROJECT_TECH_NOTE — 프로젝트에 쓴 기술을 "어떻게 활용했는지" 적은 한 줄. */
public class ProjectTechNoteDto {

    private Long id;
    private Long projectId;
    private Long skillId;
    private String skillName;            // 조회 때 SKILL을 조인해서 채운다(저장 컬럼 아님)
    private String description;
    private boolean consentForTraining;  // 학습 데이터 활용 동의 — 기본 false
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isConsentForTraining() {
        return consentForTraining;
    }

    public void setConsentForTraining(boolean consentForTraining) {
        this.consentForTraining = consentForTraining;
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
