package com.specodyssey.dto;

import java.time.LocalDateTime;

public class UserDto {

    private Long id;
    private String userType;
    private String loginId;
    private String passwordHash;
    private String name;
    private Integer age;
    private String careerStatus; // STUDENT(학생) / JOB_SEEKER(취준생) / EMPLOYED(직장인)
    private String email;
    private String major;
    private String grade;
    private String interestField;
    private Long desiredJobId;
    private String desiredJobStatus;
    private Long resumeDocumentId; // 이력서 파일 → DOCUMENTS. 지정하지 않았으면 null
    private Long coverLetterDocumentId; // 자소서 파일 → DOCUMENTS (선택). 지정하지 않았으면 null
    private LocalDateTime privacyConsentAt;
    private String recoveryCodeHash; // 복구 코드의 해시 — 원문은 저장하지 않는다
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getCareerStatus() {
        return careerStatus;
    }

    public void setCareerStatus(String careerStatus) {
        this.careerStatus = careerStatus;
    }

    public Long getResumeDocumentId() {
        return resumeDocumentId;
    }

    public void setResumeDocumentId(Long resumeDocumentId) {
        this.resumeDocumentId = resumeDocumentId;
    }

    public Long getCoverLetterDocumentId() {
        return coverLetterDocumentId;
    }

    public void setCoverLetterDocumentId(Long coverLetterDocumentId) {
        this.coverLetterDocumentId = coverLetterDocumentId;
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

    public String getRecoveryCodeHash() {
        return recoveryCodeHash;
    }

    public void setRecoveryCodeHash(String recoveryCodeHash) {
        this.recoveryCodeHash = recoveryCodeHash;
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
