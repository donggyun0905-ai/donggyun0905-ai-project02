package com.specodyssey.dao;

import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * 글에 대한 사용자 반응 — 하트(TECH_ARTICLE_LIKE) · 북마크(TECH_ARTICLE_BOOKMARK) · 조회 기록(TECH_ARTICLE_VIEW_LOG).
 * 하트·북마크는 (article_id, user_id) 복합 UNIQUE라, 취소는 is_deleted = TRUE, 다시 누르면 같은 행을 되살린다.
 */
public class TechArticleReactionDao {

    /** 하트·북마크 — 테이블 이름 화이트리스트 */
    public enum Kind {
        LIKE("TECH_ARTICLE_LIKE"), BOOKMARK("TECH_ARTICLE_BOOKMARK");

        private final String table;

        Kind(String table) {
            this.table = table;
        }
    }

    public boolean isActive(Kind kind, Long articleId, Long userId) throws SQLException {
        String sql = "SELECT 1 FROM " + kind.table + " WHERE article_id = ? AND user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, articleId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 켜고 끈다. 트랜잭션 안에서 행을 잠그고(FOR UPDATE) 지금 상태를 본 뒤 뒤집는다 — 빠르게 두 번 눌러도 숫자가 어긋나지 않게.
     * @return 바뀐 뒤 상태 (true = 켜짐)
     */
    public boolean toggle(Connection conn, Kind kind, Long articleId, Long userId) throws SQLException {
        Boolean deleted = null;
        String select = "SELECT is_deleted FROM " + kind.table + " WHERE article_id = ? AND user_id = ? FOR UPDATE";
        try (PreparedStatement pstmt = conn.prepareStatement(select)) {
            pstmt.setLong(1, articleId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    deleted = rs.getBoolean(1);
                }
            }
        }
        if (deleted == null) {
            String insert = "INSERT INTO " + kind.table + " (article_id, user_id) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
                pstmt.setLong(1, articleId);
                pstmt.setLong(2, userId);
                pstmt.executeUpdate();
            }
            return true;
        }
        boolean nowActive = deleted; // 지워진 상태였으면 되살린다
        String update = "UPDATE " + kind.table + " SET is_deleted = ? WHERE article_id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(update)) {
            pstmt.setBoolean(1, !nowActive);
            pstmt.setLong(2, articleId);
            pstmt.setLong(3, userId);
            pstmt.executeUpdate();
        }
        return nowActive;
    }

    /**
     * 조회 기록 — 사용자 1명 × 글 1개 × 하루 1회. 오늘 처음 본 것이면 true (그때만 조회수를 올린다).
     * INSERT IGNORE는 복합 UNIQUE에 걸린 중복만 건너뛰고 영향받은 행 0을 돌려준다.
     */
    public boolean recordView(Connection conn, Long articleId, Long viewerUserId, LocalDate date) throws SQLException {
        String sql = "INSERT IGNORE INTO TECH_ARTICLE_VIEW_LOG (article_id, viewer_user_id, viewed_date) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, articleId);
            pstmt.setLong(2, viewerUserId);
            pstmt.setDate(3, Date.valueOf(date));
            return pstmt.executeUpdate() > 0;
        }
    }
}
