package com.specodyssey.dao;

import com.specodyssey.dto.TechArticleCommentDto;
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
 * TECH_ARTICLE_COMMENT 테이블 DAO. 작성자 이름과 답글 대상(@이름)을 함께 읽는다.
 * 지운 댓글도 목록에는 넣는다 — 답글이 달린 댓글은 "삭제된 댓글입니다"로 남겨야 해서, 보여줄지는 서비스가 정한다.
 */
public class TechArticleCommentDao {

    private static final String SELECT_WITH_NAMES =
            "SELECT c.*, COALESCE(NULLIF(u.name, ''), u.login_id) AS author_name, " +
            "COALESCE(NULLIF(r.name, ''), r.login_id) AS reply_to_name " +
            "FROM TECH_ARTICLE_COMMENT c " +
            "JOIN USERS u ON u.id = c.user_id " +
            "LEFT JOIN USERS r ON r.id = c.reply_to_user_id ";

    public Long insert(Connection conn, TechArticleCommentDto comment) throws SQLException {
        String sql = "INSERT INTO TECH_ARTICLE_COMMENT (article_id, user_id, parent_comment_id, reply_to_user_id, content) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, comment.getArticleId());
            pstmt.setLong(2, comment.getUserId());
            setNullableLong(pstmt, 3, comment.getParentCommentId());
            setNullableLong(pstmt, 4, comment.getReplyToUserId());
            pstmt.setString(5, comment.getContent());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    /** 지우지 않은 댓글 하나. 없으면 null. */
    public TechArticleCommentDto findActiveById(Long id) throws SQLException {
        String sql = SELECT_WITH_NAMES + "WHERE c.id = ? AND c.is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** 글의 댓글 전체(지운 것 포함), 오래된 순 */
    public List<TechArticleCommentDto> findByArticleId(Long articleId) throws SQLException {
        String sql = SELECT_WITH_NAMES + "WHERE c.article_id = ? ORDER BY c.created_at, c.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, articleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TechArticleCommentDto> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    /** 작성자 본인 댓글만 논리 삭제한다. 지웠으면 true. */
    public boolean softDeleteByOwner(Connection conn, Long commentId, Long userId) throws SQLException {
        String sql = "UPDATE TECH_ARTICLE_COMMENT SET is_deleted = TRUE WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, commentId);
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private TechArticleCommentDto mapRow(ResultSet rs) throws SQLException {
        TechArticleCommentDto c = new TechArticleCommentDto();
        c.setId(rs.getLong("id"));
        c.setArticleId(rs.getLong("article_id"));
        c.setUserId(rs.getLong("user_id"));
        long parent = rs.getLong("parent_comment_id");
        c.setParentCommentId(rs.wasNull() ? null : parent);
        long replyTo = rs.getLong("reply_to_user_id");
        c.setReplyToUserId(rs.wasNull() ? null : replyTo);
        c.setContent(rs.getString("content"));
        c.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        c.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        c.setDeleted(rs.getBoolean("is_deleted"));
        c.setAuthorName(rs.getString("author_name"));
        c.setReplyToName(rs.getString("reply_to_name"));
        return c;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, java.sql.Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
