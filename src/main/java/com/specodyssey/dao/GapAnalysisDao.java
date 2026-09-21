package com.specodyssey.dao;

import com.specodyssey.dto.GapAnalysisDto;
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
 * GAP_ANALYSIS 테이블 DAO.
 * 관련 요구사항: FR-31 · 37 · 42
 * 사용자가 분석을 실행할 때마다 새 스냅샷이 쌓이는 구조라 수정·삭제는 없다(재분석 = 새 행 추가).
 */
public class GapAnalysisDao {

    public Long insert(GapAnalysisDto analysis) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, analysis);
        }
    }

    public Long insert(Connection conn, GapAnalysisDto analysis) throws SQLException {
        String sql = "INSERT INTO GAP_ANALYSIS (user_id, job_id, match_rate, analyzed_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, analysis.getUserId());
            pstmt.setLong(2, analysis.getJobId());
            pstmt.setBigDecimal(3, analysis.getMatchRate());
            pstmt.setTimestamp(4, toTimestamp(analysis.getAnalyzedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 최신 분석순 — 대시보드는 보통 가장 최근 것을 보여준다
    public List<GapAnalysisDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM GAP_ANALYSIS WHERE user_id = ? AND is_deleted = FALSE ORDER BY analyzed_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<GapAnalysisDto> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    public GapAnalysisDto findById(Long id) throws SQLException {
        String sql = "SELECT * FROM GAP_ANALYSIS WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private GapAnalysisDto mapRow(ResultSet rs) throws SQLException {
        GapAnalysisDto analysis = new GapAnalysisDto();
        analysis.setId(rs.getLong("id"));
        analysis.setUserId(rs.getLong("user_id"));
        analysis.setJobId(rs.getLong("job_id"));
        analysis.setMatchRate(rs.getBigDecimal("match_rate"));
        analysis.setAnalyzedAt(toLocalDateTime(rs.getTimestamp("analyzed_at")));
        analysis.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        analysis.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        analysis.setDeleted(rs.getBoolean("is_deleted"));
        return analysis;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
