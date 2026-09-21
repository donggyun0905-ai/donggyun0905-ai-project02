package com.specodyssey.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * USER_SCORE_SUMMARY — user_id가 PK이자 FK(1:1)라 별도 id 필드가 없다.
 */
public class UserScoreSummaryDto {

    private Long userId;
    private Integer totalScore;
    private Long currentTierId;
    private Integer streakCount;
    private LocalDate lastMissionDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Long getCurrentTierId() {
        return currentTierId;
    }

    public void setCurrentTierId(Long currentTierId) {
        this.currentTierId = currentTierId;
    }

    public Integer getStreakCount() {
        return streakCount;
    }

    public void setStreakCount(Integer streakCount) {
        this.streakCount = streakCount;
    }

    public LocalDate getLastMissionDate() {
        return lastMissionDate;
    }

    public void setLastMissionDate(LocalDate lastMissionDate) {
        this.lastMissionDate = lastMissionDate;
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
