package com.specodyssey.dto;

import java.time.LocalDateTime;

public class DocumentDto {

    private Long id;
    private Long userId;
    private Long projectId;
    private Long roadmapStepId;
    private String originalName;
    private String storedName;
    private String filePath;       // 예전(디스크) 서류만 — DB로 옮긴 뒤에 올린 서류는 null
    private byte[] fileData;       // 저장할 때만 채운다. 조회(mapRow)는 내용을 싣지 않는다 — 목록마다 수 MB를 읽지 않게
    private boolean storedInDb;    // 조회 시 file_data가 있는지
    private Long fileSize;
    private String mimeType;
    private String checksum;
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

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getRoadmapStepId() {
        return roadmapStepId;
    }

    public void setRoadmapStepId(Long roadmapStepId) {
        this.roadmapStepId = roadmapStepId;
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

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
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

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }

    public boolean isStoredInDb() {
        return storedInDb;
    }

    public void setStoredInDb(boolean storedInDb) {
        this.storedInDb = storedInDb;
    }
}
