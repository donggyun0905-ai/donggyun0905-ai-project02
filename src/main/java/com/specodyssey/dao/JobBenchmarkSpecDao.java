package com.specodyssey.dao;

import com.specodyssey.dto.JobBenchmarkSpecDto;
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
 * JOB_BENCHMARK_SPEC 테이블 DAO.
 * 관련 요구사항: FR-46 데이터 인사이트 (합격자 스펙 역산, LLM 생성)
 * LLM이 생성해 채우는 데이터라 읽기 + 등록만 지원한다.
 */
public class JobBenchmarkSpecDao {

    public Long insert(JobBenchmarkSpecDto spec) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, spec);
        }
    }

    public Long insert(Connection conn, JobBenchmarkSpecDto spec) throws SQLException {
        String sql = "INSERT INTO JOB_BENCHMARK_SPEC (job_id, tier, spec_type, content, is_estimated, generated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, spec.getJobId());
            pstmt.setString(2, spec.getTier());
            pstmt.setString(3, spec.getSpecType());
            pstmt.setString(4, spec.getContent());
            pstmt.setBoolean(5, spec.isEstimated());
            pstmt.setTimestamp(6, toTimestamp(spec.getGeneratedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<JobBenchmarkSpecDto> findByJobId(Long jobId) throws SQLException {
        String sql = "SELECT * FROM JOB_BENCHMARK_SPEC WHERE job_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<JobBenchmarkSpecDto> specs = new ArrayList<>();
                while (rs.next()) {
                    specs.add(mapRow(rs));
                }
                return specs;
            }
        }
    }

    private JobBenchmarkSpecDto mapRow(ResultSet rs) throws SQLException {
        JobBenchmarkSpecDto spec = new JobBenchmarkSpecDto();
        spec.setId(rs.getLong("id"));
        spec.setJobId(rs.getLong("job_id"));
        spec.setTier(rs.getString("tier"));
        spec.setSpecType(rs.getString("spec_type"));
        spec.setContent(rs.getString("content"));
        spec.setEstimated(rs.getBoolean("is_estimated"));
        spec.setGeneratedAt(toLocalDateTime(rs.getTimestamp("generated_at")));
        spec.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        spec.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        spec.setDeleted(rs.getBoolean("is_deleted"));
        return spec;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
