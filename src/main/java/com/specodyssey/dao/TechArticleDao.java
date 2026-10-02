package com.specodyssey.dao;

import com.specodyssey.dto.TechArticleDto;
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
 * TECH_ARTICLE 테이블 DAO — 지금은 스펙 아카이브(source_type = ARCHIVE_TIP) 조회·작성만 다룬다.
 * 목록·상세는 공개(PUBLISHED)·미삭제 글만 돌려준다. 작성자 이름과 티어 칭호를 함께 읽는다.
 */
public class TechArticleDao {

    public static final String SOURCE_ARCHIVE_TIP = "ARCHIVE_TIP";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 목록 정렬 */
    public enum Sort {
        LATEST("a.published_at DESC, a.id DESC"),
        POPULAR("a.like_count DESC, a.published_at DESC, a.id DESC");

        private final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    /** 집계 컬럼 — 화이트리스트라 SQL에 이름을 그대로 넣어도 안전하다 */
    public enum CountColumn {
        VIEW("view_count"), LIKE("like_count"), COMMENT("comment_count"), BOOKMARK("bookmark_count");

        private final String column;

        CountColumn(String column) {
            this.column = column;
        }
    }

    // 작성자 이름(없으면 아이디)과 현재 티어 칭호(개척자 등).
    // 티어는 총점 구간으로 찾는다 — 헤더 배지(ScoreService.getTierForScore)와 같은 기준. current_tier_id는 비어 있을 수 있다.
    private static final String A_COLUMNS =
            "a.id, a.user_id, a.skill_id, a.roadmap_step_id, a.source_type, a.title, a.content, a.status, a.published_at, " +
            "a.hidden_reason, a.hidden_at, a.view_count, a.like_count, a.comment_count, a.bookmark_count, " +
            "a.created_at, a.updated_at, a.is_deleted";

    private static final String SELECT_WITH_AUTHOR =
            "SELECT " + A_COLUMNS + ", COALESCE(NULLIF(u.name, ''), u.login_id) AS author_name, t.title_name AS author_title " +
            "FROM TECH_ARTICLE a " +
            "JOIN USERS u ON u.id = a.user_id " +
            "LEFT JOIN USER_SCORE_SUMMARY s ON s.user_id = a.user_id AND s.is_deleted = FALSE " +
            "LEFT JOIN LEVEL_TIER t ON t.is_deleted = FALSE AND COALESCE(s.total_score, 0) >= t.min_score " +
            "AND (t.max_score IS NULL OR COALESCE(s.total_score, 0) <= t.max_score) ";

    private static final String ARCHIVE_VISIBLE =
            "a.source_type = '" + SOURCE_ARCHIVE_TIP + "' AND a.status = '" + STATUS_PUBLISHED + "' AND a.is_deleted = FALSE ";

    public Long insert(Connection conn, TechArticleDto article) throws SQLException {
        String sql = "INSERT INTO TECH_ARTICLE (user_id, skill_id, roadmap_step_id, source_type, title, content, status, published_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, article.getUserId());
            setNullableLong(pstmt, 2, article.getSkillId());
            setNullableLong(pstmt, 3, article.getRoadmapStepId());
            pstmt.setString(4, article.getSourceType());
            pstmt.setString(5, article.getTitle());
            pstmt.setString(6, article.getContent());
            pstmt.setString(7, article.getStatus());
            pstmt.setTimestamp(8, toTimestamp(article.getPublishedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    /** 공개된 아카이브 글 하나. 없거나 지워졌거나 내려졌으면 null. */
    public TechArticleDto findArchiveById(Long id) throws SQLException {
        String sql = SELECT_WITH_AUTHOR + "WHERE a.id = ? AND " + ARCHIVE_VISIBLE;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<TechArticleDto> findArchivePage(Sort sort, int offset, int limit) throws SQLException {
        String sql = SELECT_WITH_AUTHOR + "WHERE " + ARCHIVE_VISIBLE + "ORDER BY " + sort.orderBy + " LIMIT ? OFFSET ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);
            return mapRows(pstmt);
        }
    }

    public int countArchive() throws SQLException {
        String sql = "SELECT COUNT(*) FROM TECH_ARTICLE a WHERE " + ARCHIVE_VISIBLE;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 내가 북마크한 아카이브 글 — 북마크한 순서(최근 먼저) */
    public List<TechArticleDto> findBookmarkedArchivePage(Long userId, int offset, int limit) throws SQLException {
        String sql = SELECT_WITH_AUTHOR +
                "JOIN TECH_ARTICLE_BOOKMARK b ON b.article_id = a.id AND b.user_id = ? AND b.is_deleted = FALSE " +
                "WHERE " + ARCHIVE_VISIBLE + "ORDER BY b.created_at DESC, b.id DESC LIMIT ? OFFSET ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, limit);
            pstmt.setInt(3, offset);
            return mapRows(pstmt);
        }
    }

    public int countBookmarkedArchive(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM TECH_ARTICLE a " +
                "JOIN TECH_ARTICLE_BOOKMARK b ON b.article_id = a.id AND b.user_id = ? AND b.is_deleted = FALSE " +
                "WHERE " + ARCHIVE_VISIBLE;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 하트·댓글·북마크·조회 수를 delta만큼 바꾼다. 0 아래로는 내려가지 않는다. 같은 트랜잭션에서 부른다. */
    public void adjustCount(Connection conn, Long articleId, CountColumn column, int delta) throws SQLException {
        String sql = "UPDATE TECH_ARTICLE SET " + column.column + " = GREATEST(" + column.column + " + ?, 0) WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, delta);
            pstmt.setLong(2, articleId);
            pstmt.executeUpdate();
        }
    }

    /** 작성자 본인 글만 논리 삭제한다. 지웠으면 true. */
    public boolean softDeleteByOwner(Connection conn, Long articleId, Long userId) throws SQLException {
        String sql = "UPDATE TECH_ARTICLE SET is_deleted = TRUE WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, articleId);
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private List<TechArticleDto> mapRows(PreparedStatement pstmt) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            List<TechArticleDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        }
    }

    private TechArticleDto mapRow(ResultSet rs) throws SQLException {
        TechArticleDto a = new TechArticleDto();
        a.setId(rs.getLong("id"));
        a.setUserId(rs.getLong("user_id"));
        long skillId = rs.getLong("skill_id");
        a.setSkillId(rs.wasNull() ? null : skillId);
        long stepId = rs.getLong("roadmap_step_id");
        a.setRoadmapStepId(rs.wasNull() ? null : stepId);
        a.setSourceType(rs.getString("source_type"));
        a.setTitle(rs.getString("title"));
        a.setContent(rs.getString("content"));
        a.setStatus(rs.getString("status"));
        a.setPublishedAt(toLocalDateTime(rs.getTimestamp("published_at")));
        a.setHiddenReason(rs.getString("hidden_reason"));
        a.setHiddenAt(toLocalDateTime(rs.getTimestamp("hidden_at")));
        a.setViewCount(rs.getInt("view_count"));
        a.setLikeCount(rs.getInt("like_count"));
        a.setCommentCount(rs.getInt("comment_count"));
        a.setBookmarkCount(rs.getInt("bookmark_count"));
        a.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        a.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        a.setDeleted(rs.getBoolean("is_deleted"));
        a.setAuthorName(rs.getString("author_name"));
        a.setAuthorTitle(rs.getString("author_title"));
        return a;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, java.sql.Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
