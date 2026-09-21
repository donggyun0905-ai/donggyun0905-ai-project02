package com.specodyssey.dao;

import com.specodyssey.dto.TrendTechJobDto;
import com.specodyssey.util.DBUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * TREND_TECH_JOB 테이블 DAO.
 * 관련 요구사항: FR-55 사이드바 직무 연관 필터링
 * 임베딩 매칭 배치가 채우는 N:M 연결 데이터라 읽기 + 등록만 지원한다.
 */
public class TrendTechJobDao {

    public Long insert(TrendTechJobDto link) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, link);
        }
    }

    public Long insert(Connection conn, TrendTechJobDto link) throws SQLException {
        String sql = "INSERT INTO TREND_TECH_JOB (trend_tech_id, job_id, relevance_score) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, link.getTrendTechId());
            pstmt.setLong(2, link.getJobId());
            setNullableBigDecimal(pstmt, 3, link.getRelevanceScore());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<TrendTechJobDto> findByJobId(Long jobId) throws SQLException {
        String sql = "SELECT * FROM TREND_TECH_JOB WHERE job_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TrendTechJobDto> links = new ArrayList<>();
                while (rs.next()) {
                    links.add(mapRow(rs));
                }
                return links;
            }
        }
    }

    private TrendTechJobDto mapRow(ResultSet rs) throws SQLException {
        TrendTechJobDto link = new TrendTechJobDto();
        link.setId(rs.getLong("id"));
        link.setTrendTechId(rs.getLong("trend_tech_id"));
        link.setJobId(rs.getLong("job_id"));
        link.setRelevanceScore(rs.getBigDecimal("relevance_score"));
        link.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        link.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        link.setDeleted(rs.getBoolean("is_deleted"));
        return link;
    }

    private void setNullableBigDecimal(PreparedStatement pstmt, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.DECIMAL);
        } else {
            pstmt.setBigDecimal(index, value);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
