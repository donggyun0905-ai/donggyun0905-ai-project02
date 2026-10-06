package com.specodyssey.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** SIMULATION_STATE 한 행 — 테스트 계정 시뮬레이션 진행 상태 (목표 점수에 닿을 때까지, 최대 total_days일). */
public class SimulationStateDto {

    public static final String RUNNING = "RUNNING";
    public static final String PAUSED = "PAUSED";
    public static final String DONE = "DONE";

    private Long id;
    private Long userId;
    private String status;
    private String persona;
    private Integer targetScore;
    private LocalDate startDate;
    private int totalDays;
    private int daysDone;
    private LocalDateTime startedAt;
    private String lastError;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public Integer getTargetScore() {
        return targetScore;
    }

    public void setTargetScore(Integer targetScore) {
        this.targetScore = targetScore;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    public int getDaysDone() {
        return daysDone;
    }

    public void setDaysDone(int daysDone) {
        this.daysDone = daysDone;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public boolean isRunning() {
        return RUNNING.equals(status);
    }

    public boolean isDone() {
        return DONE.equals(status) || daysDone >= totalDays;
    }

    /** 다음에 돌릴 날 (days_done번째 날, 0부터) */
    public LocalDate nextDate() {
        return startDate.plusDays(daysDone);
    }
}
