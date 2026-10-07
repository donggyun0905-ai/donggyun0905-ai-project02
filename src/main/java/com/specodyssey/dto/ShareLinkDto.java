package com.specodyssey.dto;

import java.time.LocalDateTime;

public class ShareLinkDto {

    private Long id;
    private Long userId;
    private String token;
    private boolean active;
    private LocalDateTime expiresAt;
    private boolean scopeBasic;
    private boolean scopeSkills;
    private boolean scopeGrowth;
    private boolean scopeResume;
    private boolean scopeCoverLetter;
    private boolean scopeAge; // 나이 공개 — 지원자가 링크마다 고른다(기본 비공개)
    private boolean scopeProjectDocs; // 프로젝트 제출 서류(README·실행 화면 등) 파일 공개 — 기본 비공개
    private boolean scopeEducation;   // 학력(학교·졸업·학점) 공개 — 기본 비공개(블라인드 채용)
    private String label;
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
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

    public void setScopeAge(boolean scopeAge) {
        this.scopeAge = scopeAge;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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
