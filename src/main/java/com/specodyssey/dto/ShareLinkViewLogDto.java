package com.specodyssey.dto;

import java.time.LocalDateTime;

public class ShareLinkViewLogDto {

    private Long id;
    private Long shareLinkId;
    private LocalDateTime viewedAt;
    private String viewerIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShareLinkId() {
        return shareLinkId;
    }

    public void setShareLinkId(Long shareLinkId) {
        this.shareLinkId = shareLinkId;
    }

    public LocalDateTime getViewedAt() {
        return viewedAt;
    }

    public void setViewedAt(LocalDateTime viewedAt) {
        this.viewedAt = viewedAt;
    }

    public String getViewerIp() {
        return viewerIp;
    }

    public void setViewerIp(String viewerIp) {
        this.viewerIp = viewerIp;
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
