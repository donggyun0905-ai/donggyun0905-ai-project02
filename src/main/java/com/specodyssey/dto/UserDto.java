package com.specodyssey.dto;

import java.time.LocalDateTime;

public class UserDto {

    private Long id;
    private String userType;
    private String loginId;
    private String passwordHash;
    private String email;
    private String major;
    private String grade;
    private String interestField;
    private Long desiredJobId;
    private String desiredJobStatus;
    private LocalDateTime privacyConsentAt;
    private LocalDateTime profileUpdatedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getInterestField() {
        return interestField;
    }

    public void setInterestField(String interestField) {
        this.interestField = interestField;
    }

    public Long getDesiredJobId() {
        return desiredJobId;
    }

    public void setDesiredJobId(Long desiredJobId) {
        this.desiredJobId = desiredJobId;
    }

    public String getDesiredJobStatus() {
        return desiredJobStatus;
    }

    public void setDesiredJobStatus(String desiredJobStatus) {
        this.desiredJobStatus = desiredJobStatus;
    }

    public LocalDateTime getPrivacyConsentAt() {
        return privacyConsentAt;
    }

    public void setPrivacyConsentAt(LocalDateTime privacyConsentAt) {
        this.privacyConsentAt = privacyConsentAt;
    }

    public LocalDateTime getProfileUpdatedAt() {
        return profileUpdatedAt;
    }

    public void setProfileUpdatedAt(LocalDateTime profileUpdatedAt) {
        this.profileUpdatedAt = profileUpdatedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
