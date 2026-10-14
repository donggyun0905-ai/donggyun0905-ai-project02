package com.specodyssey.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;

/**
 * TECH_ARTICLE 한 행. authorName·authorTitle은 조회할 때 USERS·LEVEL_TIER에서 JOIN해 채우는 표시용 값이다.
 * authorExpertSkills도 같은 표시용 값으로, 그 사람이 로드맵에서 전문가 단계까지 끝낸 기술 이름들이다
 * (2026-10-14). 글쓰기를 등급으로 막는 대신 "무엇을 증명했는지"를 보여 주기로 했다.
 */
public class TechArticleDto {

    // JSTL의 fmt:formatDate는 java.util.Date만 받는다 — 화면에 쓸 문구는 여기서 만든다
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Long id;
    private Long userId;
    private Long skillId;
    private Long roadmapStepId;
    private String sourceType;
    private String title;
    private String content;
    private String status;
    private LocalDateTime publishedAt;
    private String hiddenReason;
    private LocalDateTime hiddenAt;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private int bookmarkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    private String authorName;
    private String authorTitle;
    /** 로드맵 전문가 단계를 끝낸 기술 이름 — 화면에 "Java 전문가"처럼 붙는다. 비어 있을 수 있다. */
    private List<String> authorExpertSkills = new ArrayList<>();
    /** 무슨 기술에 대한 글인지 — SKILL 조인으로 채우는 표시용 값. 기술을 안 고른 글은 null */
    private String skillName;
    /** 그 기술의 유형(SKILL.category) — 목록의 유형 칩과 유형별 거르기에 쓴다 */
    private String skillCategory;
    private String previewText; // 목록용 — 첨부 표시를 지운 본문 앞부분 (서비스가 채운다)

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

    public Long getSkillId() {
        return skillId;
    }

    public void setSkillId(Long skillId) {
        this.skillId = skillId;
    }

    public Long getRoadmapStepId() {
        return roadmapStepId;
    }

    public void setRoadmapStepId(Long roadmapStepId) {
        this.roadmapStepId = roadmapStepId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public void setHiddenReason(String hiddenReason) {
        this.hiddenReason = hiddenReason;
    }

    public LocalDateTime getHiddenAt() {
        return hiddenAt;
    }

    public void setHiddenAt(LocalDateTime hiddenAt) {
        this.hiddenAt = hiddenAt;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public int getBookmarkCount() {
        return bookmarkCount;
    }

    public void setBookmarkCount(int bookmarkCount) {
        this.bookmarkCount = bookmarkCount;
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

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorTitle() {
        return authorTitle;
    }

    public void setAuthorTitle(String authorTitle) {
        this.authorTitle = authorTitle;
    }

    public List<String> getAuthorExpertSkills() {
        return authorExpertSkills;
    }

    public void setAuthorExpertSkills(List<String> authorExpertSkills) {
        this.authorExpertSkills = authorExpertSkills == null ? new ArrayList<>() : authorExpertSkills;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillCategory() {
        return skillCategory;
    }

    public void setSkillCategory(String skillCategory) {
        this.skillCategory = skillCategory;
    }

    public String getPreviewText() {
        return previewText;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    /** 화면 표시용 — JSP는 이 문자열을 그대로 쓴다. */
    public String getCreatedAtText() {
        return createdAt == null ? "" : createdAt.format(DATE_FORMAT);
    }
}
