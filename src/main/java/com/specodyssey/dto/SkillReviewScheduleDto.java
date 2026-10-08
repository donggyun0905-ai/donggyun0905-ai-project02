package com.specodyssey.dto;

import java.time.LocalDateTime;

/** SKILL_REVIEW_SCHEDULE 한 줄 — 사람·기술별 간격 반복 복습 일정 (SM-2). */
public class SkillReviewScheduleDto {

    private Long id;
    private Long userId;
    private Long skillId;
    private double easeFactor;
    private int intervalDays;
    private int repetitions;
    private Integer lastQuality;
    private LocalDateTime lastReviewedAt;
    private LocalDateTime dueAt;

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

    public Long getSkillId() {
        return skillId;
    }

    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }

    public double getEaseFactor() {
        return easeFactor;
    }

    public void setEaseFactor(double easeFactor) {
        this.easeFactor = easeFactor;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }

    public Integer getLastQuality() {
        return lastQuality;
    }

    public void setLastQuality(Integer lastQuality) {
        this.lastQuality = lastQuality;
    }

    public LocalDateTime getLastReviewedAt() {
        return lastReviewedAt;
    }

    public void setLastReviewedAt(LocalDateTime lastReviewedAt) {
        this.lastReviewedAt = lastReviewedAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }
}
