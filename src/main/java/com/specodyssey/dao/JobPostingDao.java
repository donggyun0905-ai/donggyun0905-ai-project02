package com.specodyssey.dao;

import com.specodyssey.dto.JobPostingDto;
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
 * JOB_POSTING 테이블 DAO.
 * 관련 요구사항: FR-113 데이터 없는 직무 보완
 * 채용정보 API(고용24) 수집 배치가 채우는 데이터라 읽기 + 등록만 지원한다.
 * source_url이 UNIQUE라 재수집 시에는 existsBySourceUrl로 먼저 확인하고 없을 때만 insert한다.
 */
public class JobPostingDao {

    public Long insert(JobPostingDto posting) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, posting);
        }
    }

    public Long insert(Connection conn, JobPostingDto posting) throws SQLException {
        String sql = "INSERT INTO JOB_POSTING " +
                "(job_id, title, summary, qualifications, preferred, education_level, salary, " +
                " source_url, collected_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, posting.getJobId());
            pstmt.setString(2, posting.getTitle());
            pstmt.setString(3, posting.getSummary());
            pstmt.setString(4, posting.getQualifications());
            pstmt.setString(5, posting.getPreferred());
            pstmt.setString(6, posting.getEducationLevel());
            pstmt.setString(7, posting.getSalary());
            pstmt.setString(8, posting.getSourceUrl());
            pstmt.setTimestamp(9, toTimestamp(posting.getCollectedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 재수집 배치가 같은 공고를 중복 저장하지 않도록 먼저 확인한다 (source_url UNIQUE).
    public boolean existsBySourceUrl(Connection conn, String sourceUrl) throws SQLException {
        String sql = "SELECT 1 FROM JOB_POSTING WHERE source_url = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sourceUrl);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // FR-113: On-demand 조회 결과 공고가 있는지 판단하고, 있으면 보여줄 목록
    public List<JobPostingDto> findByJobId(Long jobId) throws SQLException {
        String sql = "SELECT * FROM JOB_POSTING WHERE job_id = ? AND is_deleted = FALSE ORDER BY collected_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<JobPostingDto> postings = new ArrayList<>();
                while (rs.next()) {
                    postings.add(mapRow(rs));
                }
                return postings;
            }
        }
    }

    private JobPostingDto mapRow(ResultSet rs) throws SQLException {
        JobPostingDto posting = new JobPostingDto();
        posting.setId(rs.getLong("id"));
        posting.setJobId(rs.getLong("job_id"));
        posting.setTitle(rs.getString("title"));
        posting.setSummary(rs.getString("summary"));
        posting.setQualifications(rs.getString("qualifications"));
        posting.setPreferred(rs.getString("preferred"));
        posting.setEducationLevel(rs.getString("education_level"));
        posting.setSalary(rs.getString("salary"));
        posting.setSourceUrl(rs.getString("source_url"));
        posting.setCollectedAt(toLocalDateTime(rs.getTimestamp("collected_at")));
        posting.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        posting.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        posting.setDeleted(rs.getBoolean("is_deleted"));
        return posting;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
