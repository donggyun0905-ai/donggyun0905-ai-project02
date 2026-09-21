package com.specodyssey.dao;

import com.specodyssey.dto.JobRecommendationDto;
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
 * JOB_RECOMMENDATION 테이블 DAO.
 * 관련 요구사항: FR-34 · 35 · 38 · 39
 * 추천 후보는 시스템이 생성하고, 사용자는 그중 하나를 선택(is_selected)만 한다.
 */
public class JobRecommendationDao {

    public Long insert(JobRecommendationDto recommendation) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, recommendation);
        }
    }

    public Long insert(Connection conn, JobRecommendationDto recommendation) throws SQLException {
        String sql = "INSERT INTO JOB_RECOMMENDATION " +
                "(user_id, job_id, rank_order, match_reason, summary_json, is_selected) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, recommendation.getUserId());
            pstmt.setLong(2, recommendation.getJobId());
            pstmt.setInt(3, recommendation.getRankOrder());
            pstmt.setString(4, recommendation.getMatchReason());
            pstmt.setString(5, recommendation.getSummaryJson());
            pstmt.setBoolean(6, recommendation.isSelected());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // FR-34 추천 순위대로
    public List<JobRecommendationDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM JOB_RECOMMENDATION WHERE user_id = ? AND is_deleted = FALSE ORDER BY rank_order";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<JobRecommendationDto> recommendations = new ArrayList<>();
                while (rs.next()) {
                    recommendations.add(mapRow(rs));
                }
                return recommendations;
            }
        }
    }

    // FR-39 후보 선택 → 곧바로 격차 분석·로드맵 흐름으로 연결
    public void updateSelected(Connection conn, Long id, boolean selected) throws SQLException {
        String sql = "UPDATE JOB_RECOMMENDATION SET is_selected = ? WHERE id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, selected);
            pstmt.setLong(2, id);
            pstmt.executeUpdate();
        }
    }

    private JobRecommendationDto mapRow(ResultSet rs) throws SQLException {
        JobRecommendationDto recommendation = new JobRecommendationDto();
        recommendation.setId(rs.getLong("id"));
        recommendation.setUserId(rs.getLong("user_id"));
        recommendation.setJobId(rs.getLong("job_id"));
        recommendation.setRankOrder(rs.getObject("rank_order", Integer.class));
        recommendation.setMatchReason(rs.getString("match_reason"));
        recommendation.setSummaryJson(rs.getString("summary_json"));
        recommendation.setSelected(rs.getBoolean("is_selected"));
        recommendation.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        recommendation.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        recommendation.setDeleted(rs.getBoolean("is_deleted"));
        return recommendation;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
