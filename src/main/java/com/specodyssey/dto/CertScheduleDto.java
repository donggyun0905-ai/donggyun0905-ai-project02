package com.specodyssey.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CertScheduleDto {

    private Long id;
    private Long certificationId;
    private String roundName;
    private LocalDate applyStart;
    private LocalDate applyEnd;
    private LocalDate examDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCertificationId() {
        return certificationId;
    }

    public void setCertificationId(Long certificationId) {
        this.certificationId = certificationId;
    }

    public String getRoundName() {
        return roundName;
    }

    public void setRoundName(String roundName) {
        this.roundName = roundName;
    }

    public LocalDate getApplyStart() {
        return applyStart;
    }

    public void setApplyStart(LocalDate applyStart) {
        this.applyStart = applyStart;
    }

    public LocalDate getApplyEnd() {
        return applyEnd;
    }

    public void setApplyEnd(LocalDate applyEnd) {
        this.applyEnd = applyEnd;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
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
