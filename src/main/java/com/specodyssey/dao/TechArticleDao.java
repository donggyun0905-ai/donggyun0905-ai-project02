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
    public static final String STATUS_HIDDEN = "HIDDEN";

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

    /** 검색 결과를 관련도 순으로 줄 세우려면 점수를 SELECT에도 넣어야 한다 */
    private static final String SELECT_WITH_AUTHOR_RELEVANCE =
            SELECT_WITH_AUTHOR.replace("SELECT " + A_COLUMNS,
                    "SELECT " + A_COLUMNS + ", MATCH(a.title, a.content) AGAINST(? IN BOOLEAN MODE) AS relevance");

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

    // ---------------------------------------------------------------- 검색 (2026-10-08)

    /**
     * ngram_token_size. 이보다 짧은 검색어는 FULLTEXT 인덱스에 토큰이 없어 한 건도 안 걸린다 —
     * 그때만 LIKE로 떨어진다. 공유 DB 확인값(2026-10-08) = 2.
     */
    static final int NGRAM_TOKEN_SIZE = 2;

    /**
     * 스펙 아카이브 글 검색. 제목·본문을 FULLTEXT(ngram)로 찾고 관련도 순으로 돌려준다.
     *
     * 왜 LIKE가 아닌가: LIKE '%키워드%'는 앞에 와일드카드가 있어 인덱스를 전혀 타지 못하고, 본문이
     * TEXT라 글이 쌓일수록 전수 조회가 된다. ngram 파서를 쓰면 한국어 부분 일치도 인덱스로 찾는다.
     *
     * 한 글자 검색만 LIKE로 떨어진다 — ngram 토큰이 2글자라 인덱스에 한 글자 토큰이 없다.
     * 그 경우도 "못 찾습니다"로 끝내지 않으려고 폴백을 둔다(검색은 되는데 한 글자만 안 되면 더 이상하다).
     */
    public List<TechArticleDto> searchArchive(String keyword, Sort sort, int offset, int limit) throws SQLException {
        String query = toBooleanQuery(keyword);
        if (query == null) {
            return searchArchiveWithLike(keyword, sort, offset, limit);
        }
        // 관련도 순이 기본이지만, 사용자가 정렬을 고르면 그쪽을 먼저 본다
        String order = sort == null ? "relevance DESC, a.id DESC" : sort.orderBy + ", relevance DESC";
        String sql = SELECT_WITH_AUTHOR_RELEVANCE
                + "WHERE " + ARCHIVE_VISIBLE + "AND MATCH(a.title, a.content) AGAINST(? IN BOOLEAN MODE) "
                + "ORDER BY " + order + " LIMIT ? OFFSET ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, query);
            pstmt.setString(2, query);
            pstmt.setInt(3, limit);
            pstmt.setInt(4, offset);
            return mapRows(pstmt);
        }
    }

    public int countSearchArchive(String keyword) throws SQLException {
        String query = toBooleanQuery(keyword);
        String sql = query == null
                ? "SELECT COUNT(*) FROM TECH_ARTICLE a WHERE " + ARCHIVE_VISIBLE
                        + "AND (a.title LIKE ? OR a.content LIKE ?)"
                : "SELECT COUNT(*) FROM TECH_ARTICLE a WHERE " + ARCHIVE_VISIBLE
                        + "AND MATCH(a.title, a.content) AGAINST(? IN BOOLEAN MODE)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (query == null) {
                String like = "%" + keyword.trim() + "%";
                pstmt.setString(1, like);
                pstmt.setString(2, like);
            } else {
                pstmt.setString(1, query);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 한 글자 검색 전용 폴백 — 인덱스를 못 타지만 "한 글자는 검색이 안 된다"보다는 낫다 */
    private List<TechArticleDto> searchArchiveWithLike(String keyword, Sort sort, int offset, int limit)
            throws SQLException {
        String order = sort == null ? "a.id DESC" : sort.orderBy;
        String sql = SELECT_WITH_AUTHOR + "WHERE " + ARCHIVE_VISIBLE
                + "AND (a.title LIKE ? OR a.content LIKE ?) ORDER BY " + order + " LIMIT ? OFFSET ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String like = "%" + keyword.trim() + "%";
            pstmt.setString(1, like);
            pstmt.setString(2, like);
            pstmt.setInt(3, limit);
            pstmt.setInt(4, offset);
            return mapRows(pstmt);
        }
    }

    /**
     * 사용자 입력을 BOOLEAN MODE 검색식으로 바꾼다. 모든 낱말이 들어 있어야 하도록 각 낱말에 +를 붙이고
     * 따옴표로 감싼다 — ngram에서 따옴표는 그 글자 순서를 그대로 찾으라는 뜻이다.
     *
     * 입력에 든 연산자(+ - * ~ &lt; &gt; ( ) " @)는 전부 지운다. 안 지우면 사용자가 "C++"를 검색할 때
     * +가 연산자로 해석돼 엉뚱한 결과가 나오거나 구문 오류가 난다.
     *
     * @return 검색식. 쓸 수 있는 낱말이 없거나(연산자만 입력) 전부 한 글자면 null — 호출부가 LIKE로 떨어진다
     */
    static String toBooleanQuery(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        StringBuilder query = new StringBuilder();
        for (String word : keyword.trim().split("\\s+")) {
            String cleaned = word.replaceAll("[+\\-*~<>()\"@]", "").trim();
            if (cleaned.length() < NGRAM_TOKEN_SIZE) {
                continue; // 한 글자는 ngram 인덱스에 토큰이 없다
            }
            query.append("+\"").append(cleaned).append("\" ");
        }
        return query.isEmpty() ? null : query.toString().trim();
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

    // --- 관리자 전용(2026-10-06) — status 상관없이 보고, 직접 숨기거나 되살린다 ---

    /** 관리자용 — status 상관없이 글 하나(지워졌어도 안 지운 것처럼 조회할 때는 쓰지 않는다). */
    public TechArticleDto findByIdForAdmin(Long id) throws SQLException {
        String sql = SELECT_WITH_AUTHOR + "WHERE a.id = ? AND a.is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** 관리자용 목록 — statusFilter가 null이면 전체, 아니면 그 상태만. 최신 글 먼저. */
    public List<TechArticleDto> findAllPageForAdmin(String statusFilter, int offset, int limit) throws SQLException {
        String sql = SELECT_WITH_AUTHOR + "WHERE a.is_deleted = FALSE "
                + (statusFilter != null ? "AND a.status = ? " : "")
                + "ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int i = 1;
            if (statusFilter != null) {
                pstmt.setString(i++, statusFilter);
            }
            pstmt.setInt(i++, limit);
            pstmt.setInt(i, offset);
            return mapRows(pstmt);
        }
    }

    public int countAllForAdmin(String statusFilter) throws SQLException {
        String sql = "SELECT COUNT(*) FROM TECH_ARTICLE a WHERE a.is_deleted = FALSE "
                + (statusFilter != null ? "AND a.status = ?" : "");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (statusFilter != null) {
                pstmt.setString(1, statusFilter);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 관리자가 글을 숨기거나(PUBLISHED→HIDDEN) 되살린다(HIDDEN→PUBLISHED). 되살리면 hidden_reason/at은 비운다. */
    public void updateStatus(Connection conn, Long articleId, String status, String hiddenReason,
            LocalDateTime hiddenAt) throws SQLException {
        String sql = "UPDATE TECH_ARTICLE SET status = ?, hidden_reason = ?, hidden_at = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, hiddenReason);
            pstmt.setTimestamp(3, toTimestamp(hiddenAt));
            pstmt.setLong(4, articleId);
            pstmt.executeUpdate();
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
