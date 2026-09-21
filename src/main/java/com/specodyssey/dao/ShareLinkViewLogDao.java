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
import java.util.List;

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
