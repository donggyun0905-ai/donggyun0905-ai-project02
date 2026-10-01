package com.specodyssey.dao;

import com.specodyssey.dto.ShareLinkViewLogDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SHARE_LINK_VIEW_LOG 테이블 DAO.
 * 관련 요구사항: NFR-9 비정상 접근 확인 근거
 * 열람 이력은 append-only — 수정·삭제는 없다.
 */
public class ShareLinkViewLogDao {

    public Long insert(ShareLinkViewLogDto log) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, log);
        }
    }

    public Long insert(Connection conn, ShareLinkViewLogDto log) throws SQLException {
        String sql = "INSERT INTO SHARE_LINK_VIEW_LOG (share_link_id, viewed_at, viewer_ip) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, log.getShareLinkId());
            pstmt.setTimestamp(2, toTimestamp(log.getViewedAt()));
            pstmt.setString(3, log.getViewerIp());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 지원자에게 "몇 번 열람됐는지" 보여주기 위한 조회
    public List<ShareLinkViewLogDto> findByShareLinkId(Long shareLinkId) throws SQLException {
        String sql = "SELECT * FROM SHARE_LINK_VIEW_LOG WHERE share_link_id = ? AND is_deleted = FALSE " +
                "ORDER BY viewed_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, shareLinkId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ShareLinkViewLogDto> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
                return logs;
            }
        }
    }

    // 지원자의 링크별 열람 횟수와 마지막 열람 시각 — 목록 화면에서 링크마다 따로 조회하지 않게 한 번에 가져온다.
    // 한 번도 열리지 않은 링크는 맵에 없다.
    public Map<Long, ViewStat> findViewStatsByUserId(Long userId) throws SQLException {
        String sql = "SELECT l.share_link_id, COUNT(*) AS view_count, MAX(l.viewed_at) AS last_viewed_at " +
                "FROM SHARE_LINK_VIEW_LOG l JOIN SHARE_LINK s ON s.id = l.share_link_id " +
                "WHERE s.user_id = ? AND s.is_deleted = FALSE AND l.is_deleted = FALSE " +
                "GROUP BY l.share_link_id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                Map<Long, ViewStat> stats = new HashMap<>();
                while (rs.next()) {
                    stats.put(rs.getLong("share_link_id"), new ViewStat(
                            rs.getInt("view_count"), toLocalDateTime(rs.getTimestamp("last_viewed_at"))));
                }
                return stats;
            }
        }
    }

    /** 링크 하나의 열람 요약. */
    public static class ViewStat {
        private final int viewCount;
        private final LocalDateTime lastViewedAt;

        public ViewStat(int viewCount, LocalDateTime lastViewedAt) {
            this.viewCount = viewCount;
            this.lastViewedAt = lastViewedAt;
        }

        public int getViewCount() {
            return viewCount;
        }

        public LocalDateTime getLastViewedAt() {
            return lastViewedAt;
        }
    }

    private ShareLinkViewLogDto mapRow(ResultSet rs) throws SQLException {
        ShareLinkViewLogDto log = new ShareLinkViewLogDto();
        log.setId(rs.getLong("id"));
        log.setShareLinkId(rs.getLong("share_link_id"));
        log.setViewedAt(toLocalDateTime(rs.getTimestamp("viewed_at")));
        log.setViewerIp(rs.getString("viewer_ip"));
        log.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        log.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        log.setDeleted(rs.getBoolean("is_deleted"));
        return log;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
