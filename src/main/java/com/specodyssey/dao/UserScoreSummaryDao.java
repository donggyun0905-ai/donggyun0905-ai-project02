package com.specodyssey.dao;

import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * USER_SCORE_SUMMARY 테이블 DAO.
 * 관련 요구사항: TD-5 스코어링
 * user_id가 PK이자 FK(1:1)라 회원가입 시 1행 생성(insert) 후, 점수·티어·스트릭만 갱신(update)한다.
 * 삭제는 없다(회원 탈퇴는 USERS.is_deleted로 처리, 이 테이블은 이력 보존).
 */
public class UserScoreSummaryDao {

    public void insert(UserScoreSummaryDto summary) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            insert(conn, summary);
        }
    }

    public void insert(Connection conn, UserScoreSummaryDto summary) throws SQLException {
        String sql = "INSERT INTO USER_SCORE_SUMMARY " +
                "(user_id, total_score, current_tier_id, streak_count, last_mission_date) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, summary.getUserId());
            pstmt.setInt(2, summary.getTotalScore());
            setNullableLong(pstmt, 3, summary.getCurrentTierId());
            pstmt.setInt(4, summary.getStreakCount());
            setNullableDate(pstmt, 5, summary.getLastMissionDate());
            pstmt.executeUpdate();
        }
    }

    public UserScoreSummaryDto findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM USER_SCORE_SUMMARY WHERE user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 점수 적립·미션 완료 때마다 갱신
    public void update(Connection conn, UserScoreSummaryDto summary) throws SQLException {
        String sql = "UPDATE USER_SCORE_SUMMARY SET total_score = ?, current_tier_id = ?, streak_count = ?, " +
                "last_mission_date = ? WHERE user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, summary.getTotalScore());
            setNullableLong(pstmt, 2, summary.getCurrentTierId());
            pstmt.setInt(3, summary.getStreakCount());
            setNullableDate(pstmt, 4, summary.getLastMissionDate());
            pstmt.setLong(5, summary.getUserId());
            pstmt.executeUpdate();
        }
    }

    private UserScoreSummaryDto mapRow(ResultSet rs) throws SQLException {
        UserScoreSummaryDto summary = new UserScoreSummaryDto();
        summary.setUserId(rs.getLong("user_id"));
        summary.setTotalScore(rs.getObject("total_score", Integer.class));
        summary.setCurrentTierId(rs.getObject("current_tier_id", Long.class));
        summary.setStreakCount(rs.getObject("streak_count", Integer.class));
        java.sql.Date lastMissionDate = rs.getDate("last_mission_date");
        summary.setLastMissionDate(lastMissionDate == null ? null : lastMissionDate.toLocalDate());
        summary.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        summary.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        summary.setDeleted(rs.getBoolean("is_deleted"));
        return summary;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private void setNullableDate(PreparedStatement pstmt, int index, java.time.LocalDate date) throws SQLException {
        if (date == null) {
            pstmt.setNull(index, Types.DATE);
        } else {
            pstmt.setDate(index, java.sql.Date.valueOf(date));
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
