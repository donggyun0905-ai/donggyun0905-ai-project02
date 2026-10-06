package com.specodyssey.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** NOTIFICATION 한 줄. */
public class NotificationDto {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private Long id;
    private Long userId;
    private String notiType;
    private String message;
    private String linkUrl;
    private String refKey;
    private boolean read;
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

    public String getNotiType() {
        return notiType;
    }

    public void setNotiType(String notiType) {
        this.notiType = notiType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getRefKey() {
        return refKey;
    }

    public void setRefKey(String refKey) {
        this.refKey = refKey;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 화면 표시용 시각 (예: 10.06 21:05) — JSTL fmt는 LocalDateTime을 못 다뤄서 여기서 만든다 */
    public String getCreatedAtText() {
        return createdAt == null ? "" : createdAt.format(TIME_FORMAT);
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
