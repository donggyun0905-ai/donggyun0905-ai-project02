package com.specodyssey.dto;

import java.time.LocalDateTime;

/** TECH_ARTICLE_ATTACHMENT 한 행 — 업로드 이미지 · 이미지 링크 · 유튜브 영상. */
public class TechArticleAttachmentDto {

    public static final String IMAGE_UPLOAD = "IMAGE_UPLOAD";
    public static final String IMAGE_URL = "IMAGE_URL";
    public static final String YOUTUBE = "YOUTUBE";

    private Long id;
    private Long articleId;
    private String attachmentType;
    private int sortOrder;
    private String url;
    private String embedKey;
    private String originalName;
    private String storedName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private byte[] fileData; // IMAGE_UPLOAD 사진 내용 — 저장할 때와 이미지 내보낼 때만 채운다 (목록 조회는 읽지 않음)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

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

    public String getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(String attachmentType) {
        this.attachmentType = attachmentType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEmbedKey() {
        return embedKey;
    }

    public void setEmbedKey(String embedKey) {
        this.embedKey = embedKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
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

    public boolean isUploadedImage() {
        return IMAGE_UPLOAD.equals(attachmentType);
    }

    public boolean isLinkedImage() {
        return IMAGE_URL.equals(attachmentType);
    }

    public boolean isYoutube() {
        return YOUTUBE.equals(attachmentType);
    }
}
