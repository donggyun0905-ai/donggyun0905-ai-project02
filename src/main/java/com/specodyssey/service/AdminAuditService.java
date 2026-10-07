package com.specodyssey.service;

import com.specodyssey.dao.AdminAuditLogDao;
import com.specodyssey.dto.AdminAuditLogDto;
import com.specodyssey.dto.UserDto;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 관리자 감사 로그 (2026-10-07) — 관리자 화면에서 남의 데이터를 바꾼 기록을 남기고 보여 준다.
 *
 * 관리자 기능을 만들 때 "누가 언제 무엇을 했는지" 기록이 없었다 — 내려간 글을 누가 내렸는지,
 * 누가 비밀번호를 재설정했는지 추적할 길이 없었다.
 *
 * 기록은 바꾸는 작업과 같은 트랜잭션에 끼워 넣는다(record(conn, ...)) — 작업이 롤백되면 기록도
 * 함께 사라져야 "하지 않은 일"이 기록에 남지 않는다.
 */
public class AdminAuditService {

    // 행동 이름 — 화면 필터에 그대로 쓰이므로 새로 만들 때 여기 모아 둔다
    public static final String ARTICLE_HIDE = "ARTICLE_HIDE";
    public static final String ARTICLE_RESTORE = "ARTICLE_RESTORE";
    public static final String REPORT_DISMISS = "REPORT_DISMISS";
    public static final String USER_PROFILE_UPDATE = "USER_PROFILE_UPDATE";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String USER_SOFT_DELETE = "USER_SOFT_DELETE";
    public static final String USER_WITHDRAWAL_CANCEL = "USER_WITHDRAWAL_CANCEL";
    public static final String ROADMAP_STEP_COMPLETE = "ROADMAP_STEP_COMPLETE";
    public static final String ROADMAP_STEP_UNCOMPLETE = "ROADMAP_STEP_UNCOMPLETE";
    public static final String ROADMAP_STEP_DELETE = "ROADMAP_STEP_DELETE";
    public static final String REFERENCE_SAVE = "REFERENCE_SAVE";
    public static final String REFERENCE_DELETE = "REFERENCE_DELETE";

    /** 화면에 보여 줄 한글 이름. 목록에 없는 행동은 코드 그대로 보여 준다. */
    public static String actionLabel(String action) {
        return switch (action == null ? "" : action) {
            case ARTICLE_HIDE -> "글 내림";
            case ARTICLE_RESTORE -> "글 복구";
            case REPORT_DISMISS -> "신고 반려";
            case USER_PROFILE_UPDATE -> "회원 프로필 수정";
            case USER_PASSWORD_RESET -> "비밀번호 재설정";
            case USER_SOFT_DELETE -> "탈퇴 처리";
            case USER_WITHDRAWAL_CANCEL -> "탈퇴 유예 취소";
            case ROADMAP_STEP_COMPLETE -> "로드맵 단계 완료";
            case ROADMAP_STEP_UNCOMPLETE -> "로드맵 단계 완료 취소";
            case ROADMAP_STEP_DELETE -> "로드맵 단계 삭제";
            case REFERENCE_SAVE -> "기준 데이터 저장";
            case REFERENCE_DELETE -> "기준 데이터 삭제";
            default -> action;
        };
    }

    static final int PAGE_SIZE = 30;

    private final AdminAuditLogDao dao = new AdminAuditLogDao();

    /** 바꾸는 작업과 같은 트랜잭션에 기록을 남긴다 — 롤백되면 기록도 함께 사라진다. */
    public void record(Connection conn, UserDto admin, String action, String targetType, Long targetId,
                       String detail) throws SQLException {
        dao.insert(conn, build(admin, action, targetType, targetId, detail));
    }

    /** 바꾸는 작업이 트랜잭션을 쓰지 않을 때 — 작업이 끝난 뒤 부른다. */
    public void record(UserDto admin, String action, String targetType, Long targetId, String detail)
            throws SQLException {
        dao.insert(build(admin, action, targetType, targetId, detail));
    }

    private AdminAuditLogDto build(UserDto admin, String action, String targetType, Long targetId, String detail) {
        AdminAuditLogDto log = new AdminAuditLogDto();
        log.setAdminUserId(admin == null ? null : admin.getId());
        log.setAdminLoginId(admin == null || admin.getLoginId() == null ? "(알 수 없음)" : admin.getLoginId());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(trimDetail(detail));
        return log;
    }

    // detail은 VARCHAR(500) — 사유를 길게 적어도 저장이 실패하지 않게 자른다
    static String trimDetail(String detail) {
        if (detail == null) {
            return null;
        }
        String trimmed = detail.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 499) + "…";
    }

    public List<AdminAuditLogDto> list(String action, int page) throws SQLException {
        List<AdminAuditLogDto> logs = dao.findRecent(blankToNull(action), Math.max(0, page - 1) * PAGE_SIZE, PAGE_SIZE);
        // 화면은 출력만 한다(claude.md) — 한글 이름은 여기서 채워 넘긴다
        logs.forEach(log -> log.setActionLabel(actionLabel(log.getAction())));
        return logs;
    }

    public int countPages(String action) throws SQLException {
        int total = dao.count(blankToNull(action));
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public int count(String action) throws SQLException {
        return dao.count(blankToNull(action));
    }

    public List<String> usedActions() throws SQLException {
        return dao.findUsedActions();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
