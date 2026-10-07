package com.specodyssey.dao;

import com.specodyssey.dto.AdminAuditLogDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ADMIN_AUDIT_LOG 테이블 DAO — 관리자 행동 기록.
 * 기록은 고치지 않는다(append-only, SCORE_LOG와 같은 취지) — 그래서 insert와 조회만 둔다.
 */
public class AdminAuditLogDao {

    private static final String COLUMNS =
            "id, admin_user_id, admin_login_id, action, target_type, target_id, detail, created_at";

    /** 호출하는 쪽의 트랜잭션에 끼워 넣는다 — 바꾼 내용과 기록이 함께 커밋되거나 함께 취소되도록. */
    public Long insert(Connection conn, AdminAuditLogDto log) throws SQLException {
        String sql = "INSERT INTO ADMIN_AUDIT_LOG "
                + "(admin_user_id, admin_login_id, action, target_type, target_id, detail) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            setNullableLong(pstmt, 1, log.getAdminUserId());
            pstmt.setString(2, log.getAdminLoginId());
            pstmt.setString(3, log.getAction());
            pstmt.setString(4, log.getTargetType());
            setNullableLong(pstmt, 5, log.getTargetId());
            pstmt.setString(6, log.getDetail());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    /** 자체 트랜잭션 — 바꾸는 작업이 트랜잭션을 쓰지 않을 때. */
    public Long insert(AdminAuditLogDto log) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, log);
        }
    }

    /**
     * 최근 기록 — action이 비어 있으면 전체. 최신이 먼저 온다.
     * @param action 걸러낼 행동(정확히 일치), 전체를 보려면 null
     */
    public List<AdminAuditLogDto> findRecent(String action, int offset, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM ADMIN_AUDIT_LOG WHERE is_deleted = FALSE "
                + (action == null ? "" : "AND action = ? ")
                + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int i = 1;
            if (action != null) {
                pstmt.setString(i++, action);
            }
            pstmt.setInt(i++, limit);
            pstmt.setInt(i, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<AdminAuditLogDto> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
                return logs;
            }
        }
    }

    public int count(String action) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ADMIN_AUDIT_LOG WHERE is_deleted = FALSE "
                + (action == null ? "" : "AND action = ?");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (action != null) {
                pstmt.setString(1, action);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** 화면의 행동 필터 선택지 — 실제로 쌓인 것만 보여 준다. */
    public List<String> findUsedActions() throws SQLException {
        String sql = "SELECT DISTINCT action FROM ADMIN_AUDIT_LOG WHERE is_deleted = FALSE ORDER BY action";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<String> actions = new ArrayList<>();
            while (rs.next()) {
                actions.add(rs.getString(1));
            }
            return actions;
        }
    }

    private AdminAuditLogDto mapRow(ResultSet rs) throws SQLException {
        AdminAuditLogDto log = new AdminAuditLogDto();
        log.setId(rs.getLong("id"));
        log.setAdminUserId(rs.getObject("admin_user_id", Long.class));
        log.setAdminLoginId(rs.getString("admin_login_id"));
        log.setAction(rs.getString("action"));
        log.setTargetType(rs.getString("target_type"));
        log.setTargetId(rs.getObject("target_id", Long.class));
        log.setDetail(rs.getString("detail"));
        log.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        return log;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
