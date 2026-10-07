package com.specodyssey.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ADMIN_AUDIT_LOG 한 행 — 관리자가 남의 데이터를 바꾼 기록.
 * adminLoginId는 계정이 지워져도 누구였는지 남기려고 함께 적어 둔 값이다(adminUserId는 그때 NULL이 된다).
 */
public class AdminAuditLogDto {

    // JSTL의 fmt:formatDate는 java.util.Date만 받아서 LocalDateTime을 넘기면 화면이 터진다 —
    // 화면에 쓸 문구는 여기서 만들어 준다(NotificationDto와 같은 방식).
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long id;
    private Long adminUserId;
    private String adminLoginId;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private LocalDateTime createdAt;
    /** 화면에 보여 줄 행동 한글 이름 — 조회할 때 서비스가 채운다(DB 컬럼 아님). */
    private String actionLabel;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getAdminLoginId() {
        return adminLoginId;
    }

    public void setAdminLoginId(String adminLoginId) {
        this.adminLoginId = adminLoginId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 화면 표시용 — JSP는 이 문자열을 그대로 쓴다. */
    public String getCreatedAtText() {
        return createdAt == null ? "" : createdAt.format(DATE_FORMAT);
    }

    public String getActionLabel() {
        return actionLabel == null ? action : actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }
}
