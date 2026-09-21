package com.specodyssey.dao;

import com.specodyssey.dto.JobSkillTrendDto;
import com.specodyssey.util.DBUtil;

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
 * JOB_SKILL_TREND 테이블 DAO.
 * 관련 요구사항: FR-47 시계열 그래프
 * 월별 스냅샷을 쌓아 시간별 변화를 그리는 용도라 수정·삭제는 없다.
 */
public class JobSkillTrendDao {

    public Long insert(JobSkillTrendDto trend) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, trend);
        }
    }

    public Long insert(Connection conn, JobSkillTrendDto trend) throws SQLException {
        String sql = "INSERT INTO JOB_SKILL_TREND (job_id, skill_id, period_ym, mention_count, mention_ratio) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, trend.getJobId());
            pstmt.setLong(2, trend.getSkillId());
            pstmt.setString(3, trend.getPeriodYm());
            pstmt.setInt(4, trend.getMentionCount());
            setNullableBigDecimal(pstmt, 5, trend.getMentionRatio());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // FR-47 특정 직무의 기술 언급 추이 — 월 순 정렬
    public List<JobSkillTrendDto> findByJobId(Long jobId) throws SQLException {
        String sql = "SELECT * FROM JOB_SKILL_TREND WHERE job_id = ? AND is_deleted = FALSE ORDER BY period_ym";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<JobSkillTrendDto> trends = new ArrayList<>();
                while (rs.next()) {
                    trends.add(mapRow(rs));
                }
                return trends;
            }
        }
    }

    private JobSkillTrendDto mapRow(ResultSet rs) throws SQLException {
        JobSkillTrendDto trend = new JobSkillTrendDto();
        trend.setId(rs.getLong("id"));
        trend.setJobId(rs.getLong("job_id"));
        trend.setSkillId(rs.getLong("skill_id"));
        trend.setPeriodYm(rs.getString("period_ym"));
        trend.setMentionCount(rs.getObject("mention_count", Integer.class));
        trend.setMentionRatio(rs.getBigDecimal("mention_ratio"));
        trend.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        trend.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        trend.setDeleted(rs.getBoolean("is_deleted"));
        return trend;
    }

    private void setNullableBigDecimal(PreparedStatement pstmt, int index, java.math.BigDecimal value)
            throws SQLException {
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
