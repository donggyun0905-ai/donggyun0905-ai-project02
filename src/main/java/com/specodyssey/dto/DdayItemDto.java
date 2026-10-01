package com.specodyssey.dto;

import java.time.LocalDate;

/**
 * D-day 화면에 보여줄 일정 한 줄 — DDAY_ALERT에 남은 일수 계산을 더한 읽기 전용 뷰.
 * 관련 요구사항: FR-71 · 72
 */
public class DdayItemDto {

    private Long id;
    private String title;
    private LocalDate targetDate;
    private String alertType;   // CERT / RECRUIT / CUSTOM — 수정 폼의 종류 선택에 쓴다
    private String typeLabel;   // 자격증 / 공채 / 기타
    private long daysLeft;      // 음수면 지난 일정
    private String ddayText;    // D-3 / D-DAY / 지남
    private String urgency;     // URGENT(마감 임박) / UPCOMING / PAST

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public long getDaysLeft() {
        return daysLeft;
    }

    public void setDaysLeft(long daysLeft) {
        this.daysLeft = daysLeft;
    }

    public String getDdayText() {
        return ddayText;
    }

    public void setDdayText(String ddayText) {
        this.ddayText = ddayText;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }
}
