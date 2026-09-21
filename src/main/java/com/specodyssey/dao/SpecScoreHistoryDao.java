package com.specodyssey.dao;

import com.specodyssey.dto.SpecScoreHistoryDto;
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
 * SPEC_SCORE_HISTORY 테이블 DAO.
 * 관련 요구사항: FR-41 · 45 · 84
 * 스냅샷을 스택처럼 쌓아 시계열/또래비교를 계산하는 용도라 수정·삭제는 없다.
 */
public class SpecScoreHistoryDao {

    public Long insert(SpecScoreHistoryDto history) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, history);
        }
    }

    public Long insert(Connection conn, SpecScoreHistoryDto history) throws SQLException {
        String sql = "INSERT INTO SPEC_SCORE_HISTORY " +
                "(user_id, snapshot_date, completeness_score, major, grade, is_seed) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, history.getUserId());
            pstmt.setDate(2, java.sql.Date.valueOf(history.getSnapshotDate()));
            pstmt.setBigDecimal(3, history.getCompletenessScore());
            pstmt.setString(4, history.getMajor());
            pstmt.setString(5, history.getGrade());
            pstmt.setBoolean(6, history.isSeed());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<SpecScoreHistoryDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM SPEC_SCORE_HISTORY WHERE user_id = ? AND is_deleted = FALSE " +
                "ORDER BY snapshot_date";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<SpecScoreHistoryDto> history = new ArrayList<>();
                while (rs.next()) {
                    history.add(mapRow(rs));
                }
                return history;
            }
        }
    }

    // FR-45 또래 비교 — 전공·학년이 같은 시드/실사용자 스냅샷 조회
    public List<SpecScoreHistoryDto> findByMajorAndGrade(String major, String grade) throws SQLException {
        String sql = "SELECT * FROM SPEC_SCORE_HISTORY WHERE major = ? AND grade = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, major);
            pstmt.setString(2, grade);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<SpecScoreHistoryDto> history = new ArrayList<>();
                while (rs.next()) {
                    history.add(mapRow(rs));
                }
                return history;
            }
        }
    }

    private SpecScoreHistoryDto mapRow(ResultSet rs) throws SQLException {
        SpecScoreHistoryDto history = new SpecScoreHistoryDto();
        history.setId(rs.getLong("id"));
        history.setUserId(rs.getLong("user_id"));
        java.sql.Date snapshotDate = rs.getDate("snapshot_date");
        history.setSnapshotDate(snapshotDate == null ? null : snapshotDate.toLocalDate());
        history.setCompletenessScore(rs.getBigDecimal("completeness_score"));
        history.setMajor(rs.getString("major"));
        history.setGrade(rs.getString("grade"));
        history.setSeed(rs.getBoolean("is_seed"));
        history.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        history.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        history.setDeleted(rs.getBoolean("is_deleted"));
        return history;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
