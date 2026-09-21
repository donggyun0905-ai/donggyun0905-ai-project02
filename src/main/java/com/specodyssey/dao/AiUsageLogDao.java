package com.specodyssey.dao;

import com.specodyssey.dto.AiUsageLogDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI_USAGE_LOG 테이블 DAO.
 * 관련 요구사항: FR-101 · 102, NFR-4
 * 사용자가 직접 제출·수정·삭제하는 본인 소유 리소스(공유 여부도 본인 선택).
 */
public class AiUsageLogDao {

    public Long insert(AiUsageLogDto log) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, log);
        }
    }

    public Long insert(Connection conn, AiUsageLogDto log) throws SQLException {
        String sql = "INSERT INTO AI_USAGE_LOG (user_id, usage_record_json, is_shared) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, log.getUserId());
            pstmt.setString(2, log.getUsageRecordJson());
            pstmt.setBoolean(3, log.isShared());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<AiUsageLogDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM AI_USAGE_LOG WHERE user_id = ? AND is_deleted = FALSE ORDER BY id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<AiUsageLogDto> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
                return logs;
            }
        }
    }

    public void update(Connection conn, AiUsageLogDto log, Long userId) throws SQLException {
        String sql = "UPDATE AI_USAGE_LOG SET usage_record_json = ?, is_shared = ? " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, log.getUsageRecordJson());
            pstmt.setBoolean(2, log.isShared());
            pstmt.setLong(3, log.getId());
            pstmt.setLong(4, userId);
            pstmt.executeUpdate();
        }
    }

    public void delete(Connection conn, Long logId, Long userId) throws SQLException {
        String sql = "UPDATE AI_USAGE_LOG SET is_deleted = TRUE WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, logId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    private AiUsageLogDto mapRow(ResultSet rs) throws SQLException {
        AiUsageLogDto log = new AiUsageLogDto();
        log.setId(rs.getLong("id"));
        log.setUserId(rs.getLong("user_id"));
        log.setUsageRecordJson(rs.getString("usage_record_json"));
        log.setShared(rs.getBoolean("is_shared"));
        log.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        log.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        log.setDeleted(rs.getBoolean("is_deleted"));
        return log;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
