package com.specodyssey.dao;

import com.specodyssey.dto.EvaluationSessionItemDto;
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
 * EVALUATION_SESSION_ITEM 테이블 DAO.
 * 관련 요구사항: FR-82
 * 장바구니에 담긴 지원자 항목 — 담긴 값(session_id·share_link_id·added_at)이 고정이라 수정 대상 필드가 없다.
 * 소유자 확인은 user_id가 없으므로 부모 EVALUATION_SESSION의 session_token으로 대신한다.
 */
public class EvaluationSessionItemDao {

    public Long insert(EvaluationSessionItemDto item) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, item);
        }
    }

    public Long insert(Connection conn, EvaluationSessionItemDto item) throws SQLException {
        String sql = "INSERT INTO EVALUATION_SESSION_ITEM (session_id, share_link_id, added_at) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, item.getSessionId());
            pstmt.setLong(2, item.getShareLinkId());
            pstmt.setTimestamp(3, toTimestamp(item.getAddedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<EvaluationSessionItemDto> findBySessionId(Long sessionId) throws SQLException {
        String sql = "SELECT * FROM EVALUATION_SESSION_ITEM WHERE session_id = ? AND is_deleted = FALSE " +
                "ORDER BY added_at";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<EvaluationSessionItemDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
                return items;
            }
        }
    }

    // 장바구니에서 지원자 제거 — 부모 세션의 토큰을 아는 사람만 지울 수 있다
    public void delete(Connection conn, Long itemId, String sessionToken) throws SQLException {
        String sql = "UPDATE EVALUATION_SESSION_ITEM SET is_deleted = TRUE " +
                "WHERE id = ? AND session_id IN (SELECT id FROM EVALUATION_SESSION WHERE session_token = ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, itemId);
            pstmt.setString(2, sessionToken);
            pstmt.executeUpdate();
        }
    }

    private EvaluationSessionItemDto mapRow(ResultSet rs) throws SQLException {
        EvaluationSessionItemDto item = new EvaluationSessionItemDto();
        item.setId(rs.getLong("id"));
        item.setSessionId(rs.getLong("session_id"));
        item.setShareLinkId(rs.getLong("share_link_id"));
        item.setAddedAt(toLocalDateTime(rs.getTimestamp("added_at")));
        item.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        item.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        item.setDeleted(rs.getBoolean("is_deleted"));
        return item;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
