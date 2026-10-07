package com.specodyssey.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * USER_EDUCATION — 최종 학력 한 줄(계정당 하나). 면접관 뷰에서는 "학력"을 공개한 링크에서만 보인다.
 */
public class UserEducationDto {

    /** 화면 표시용 — 재학 / 휴학 / 졸업 예정 / 졸업 */
    public static final Map<String, String> STATUS_LABELS = Map.of(
            "ENROLLED", "재학", "LEAVE", "휴학", "EXPECTED", "졸업 예정", "GRADUATED", "졸업");

    private Long id;
    private Long userId;
    private String schoolName;
    private String graduationStatus; // ENROLLED / LEAVE / EXPECTED / GRADUATED
    private LocalDate graduationDate; // 졸업일 또는 졸업 예정일. 재학·휴학이면 비워도 된다
    private BigDecimal gpa;
    private BigDecimal gpaMax;        // 4.5 / 4.3 / 4.0
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public String getStatusLabel() {
        return graduationStatus == null ? null : STATUS_LABELS.getOrDefault(graduationStatus, graduationStatus);
    }

    /** "3.82 / 4.5" — 학점을 안 넣었으면 null */
    public String getGpaText() {
        if (gpa == null) {
            return null;
        }
        String value = gpa.stripTrailingZeros().toPlainString();
        return gpaMax == null ? value : value + " / " + gpaMax.stripTrailingZeros().toPlainString();
    }

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

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getGraduationStatus() {
        return graduationStatus;
    }

    public void setGraduationStatus(String graduationStatus) {
        this.graduationStatus = graduationStatus;
    }

    public LocalDate getGraduationDate() {
        return graduationDate;
    }

    public void setGraduationDate(LocalDate graduationDate) {
        this.graduationDate = graduationDate;
    }

    public BigDecimal getGpa() {
        return gpa;
    }

    public void setGpa(BigDecimal gpa) {
        this.gpa = gpa;
    }

    public BigDecimal getGpaMax() {
        return gpaMax;
    }

    public void setGpaMax(BigDecimal gpaMax) {
        this.gpaMax = gpaMax;
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
