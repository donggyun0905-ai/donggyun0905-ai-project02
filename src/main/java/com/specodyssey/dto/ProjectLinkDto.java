package com.specodyssey.dto;

import java.time.LocalDateTime;

/** PROJECT_LINK — 프로젝트의 기타 링크 한 줄(블로그 글, 발표 영상, 노션 등). */
public class ProjectLinkDto {

    private Long id;
    private Long projectId;
    private String label;       // 비어 있을 수 있다 — 화면에서는 주소의 도메인을 대신 보여준다
    private String url;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public ProjectLinkDto() {
    }

    public ProjectLinkDto(String label, String url) {
        this.label = label;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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

    /** 화면에 보일 이름 — label이 없으면 주소의 호스트 */
    public String getDisplayName() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}
