package com.specodyssey.dto;

import java.time.LocalDateTime;

/** COMPANION_RELEASE 한 행 — 데스크톱 캐릭터 설치 파일 한 버전 (내용은 COMPANION_RELEASE_CHUNK에 나눠 있다) */
public class CompanionReleaseDto {

    private Long id;
    private String version;
    private String notes;
    private String fileName;
    private long fileSize;
    private String sha256;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 화면 표시용 — "46.7 MB" */
    public String getSizeText() {
        return String.format("%.1f MB", fileSize / 1024.0 / 1024.0);
    }
}
