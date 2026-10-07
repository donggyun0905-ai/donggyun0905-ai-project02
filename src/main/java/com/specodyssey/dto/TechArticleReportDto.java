package com.specodyssey.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** TECH_ARTICLE_REPORT 한 행. articleTitle·reporterName은 조회할 때 JOIN해 채우는 표시용 값이다. */
public class TechArticleReportDto {

    // JSTL의 fmt:formatDate는 java.util.Date만 받아서 LocalDateTime을 넘기면 화면이 터진다 —
    // 화면에 쓸 문구는 여기서 만들어 준다(NotificationDto와 같은 방식).
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_ACTION_TAKEN = "ACTION_TAKEN";
    public static final String STATUS_DISMISSED = "DISMISSED";

    private Long id;
    private Long articleId;
    private Long reporterUserId;
    private String reasonType;
    private String detail;
    private String status;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private boolean deleted;

    private String articleTitle;
    private String reporterName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getReporterUserId() {
        return reporterUserId;
    }

    public void setReporterUserId(Long reporterUserId) {
        this.reporterUserId = reporterUserId;
    }

    public String getReasonType() {
        return reasonType;
    }

    public void setReasonType(String reasonType) {
        this.reasonType = reasonType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getArticleTitle() {
        return articleTitle;
    }

    public void setArticleTitle(String articleTitle) {
        this.articleTitle = articleTitle;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    /** 화면 표시용 — JSP는 이 문자열을 그대로 쓴다. */
    public String getCreatedAtText() {
        return createdAt == null ? "" : createdAt.format(DATE_FORMAT);
    }
}
