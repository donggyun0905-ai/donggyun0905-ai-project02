package com.specodyssey.dto;

/**
 * "내 공유 링크" 목록 한 줄 — SHARE_LINK + 열람 요약을 합친 읽기 전용 뷰.
 * 관련 요구사항: FR-85 · 86
 */
public class ShareLinkItemDto {

    private Long id;
    private String token;
    private String label;
    private String status;        // ACTIVE(공유 중) / STOPPED(공유 중단됨) / EXPIRED(만료됨)
    private String expiresDate;   // yyyy-MM-dd. 만료 없음이면 null
    private String scopeText;     // "기본 이력, 보유 기술 스택"
    private int viewCount;
    private String lastViewedDate; // yyyy-MM-dd. 열람 기록이 없으면 null

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExpiresDate() {
        return expiresDate;
    }

    public void setExpiresDate(String expiresDate) {
        this.expiresDate = expiresDate;
    }

    public String getScopeText() {
        return scopeText;
    }

    public void setScopeText(String scopeText) {
        this.scopeText = scopeText;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public String getLastViewedDate() {
        return lastViewedDate;
    }

    public void setLastViewedDate(String lastViewedDate) {
        this.lastViewedDate = lastViewedDate;
    }
}
