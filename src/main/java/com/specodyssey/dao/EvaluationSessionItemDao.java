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
 * 장바구니에 담긴 지원자 항목. 담긴 값(session_id·share_link_id·added_at)은 고정이고,
 * 면접관이 고치는 것은 검토 상태·평점·메모뿐이다(updateEvaluation).
 * 소유자 확인은 user_id가 없으므로 부모 EVALUATION_SESSION의 session_token으로 대신한다.
 */
public class EvaluationSessionItemDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, session_id, share_link_id, added_at, review_status, rating, memo, created_at, updated_at, is_deleted";

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
        String sql = "SELECT " + COLUMNS + " FROM EVALUATION_SESSION_ITEM WHERE session_id = ? AND is_deleted = FALSE " +
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

    // 담기 전 중복 확인용 — 뺀(is_deleted) 행도 돌려준다.
    // (session_id, share_link_id) UNIQUE는 뺀 행에도 걸려서, 뺐다가 다시 담을 때는 새로 넣지 않고 되살려야 한다.
    public EvaluationSessionItemDto findBySessionIdAndShareLinkId(Long sessionId, Long shareLinkId)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM EVALUATION_SESSION_ITEM WHERE session_id = ? AND share_link_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            pstmt.setLong(2, shareLinkId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 뺐던 지원자를 다시 담는다
    public void restore(Connection conn, Long itemId, LocalDateTime addedAt) throws SQLException {
        String sql = "UPDATE EVALUATION_SESSION_ITEM SET is_deleted = FALSE, added_at = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, toTimestamp(addedAt));
            pstmt.setLong(2, itemId);
            pstmt.executeUpdate();
        }
    }

    // 면접관의 검토 상태·평점·메모 — 부모 세션의 토큰을 아는 사람(그 면접관)만 고칠 수 있다
    public boolean updateEvaluation(Connection conn, Long itemId, String sessionToken, String reviewStatus,
                                    Integer rating, String memo) throws SQLException {
        String sql = "UPDATE EVALUATION_SESSION_ITEM SET review_status = ?, rating = ?, memo = ? " +
                "WHERE id = ? AND is_deleted = FALSE " +
                "AND session_id IN (SELECT id FROM EVALUATION_SESSION WHERE session_token = ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, reviewStatus);
            if (rating == null) {
                pstmt.setNull(2, java.sql.Types.TINYINT);
            } else {
                pstmt.setInt(2, rating);
            }
            pstmt.setString(3, memo);
            pstmt.setLong(4, itemId);
            pstmt.setString(5, sessionToken);
            return pstmt.executeUpdate() > 0;
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
        item.setReviewStatus(rs.getString("review_status"));
        item.setRating(rs.getObject("rating", Integer.class));
        item.setMemo(rs.getString("memo"));
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
