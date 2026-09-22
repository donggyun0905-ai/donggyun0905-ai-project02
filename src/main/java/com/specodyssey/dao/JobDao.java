package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JOB 테이블 DAO. 1주차에는 희망 직무 선택 드롭다운 조회에만 쓰인다.
 */
public class JobDao {

    public List<JobDto> findAll() throws SQLException {
        String sql = "SELECT * FROM JOB WHERE is_deleted = FALSE ORDER BY job_name";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<JobDto> jobs = new ArrayList<>();
            while (rs.next()) {
                jobs.add(mapRow(rs));
            }
            return jobs;
        }
    }

    // FR-32 로드맵 생성 시 목표 직무의 job_category(자격증 매칭 등)를 조회하는 데 쓰인다.
    public JobDto findById(Long id) throws SQLException {
        String sql = "SELECT * FROM JOB WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private JobDto mapRow(ResultSet rs) throws SQLException {
        JobDto job = new JobDto();
        job.setId(rs.getLong("id"));
        job.setJobName(rs.getString("job_name"));
        job.setJobCategory(rs.getString("job_category"));
        job.setPopular(rs.getBoolean("is_popular"));
        job.setLastCollectedAt(toLocalDateTime(rs.getTimestamp("last_collected_at")));
        job.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        job.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        job.setDeleted(rs.getBoolean("is_deleted"));
        return job;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
