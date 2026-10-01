package com.specodyssey.dao;

import com.specodyssey.dto.JobPostingDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JOB_POSTING 테이블 DAO.
 * 관련 요구사항: FR-113 데이터 없는 직무 보완
 * 채용정보 API(고용24)·크롤링 등 수집 배치가 채우는 데이터라 읽기 + 등록만 지원한다.
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
                "(job_id, source, source_url, title, company_name, summary, tech_stack, " +
                " qualifications, preferred, career_level, education_level, salary, region, " +
                " deadline, posted_at, collected_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, posting.getJobId());
            pstmt.setString(2, posting.getSource());
            pstmt.setString(3, posting.getSourceUrl());
            pstmt.setString(4, posting.getTitle());
            pstmt.setString(5, posting.getCompanyName());
            pstmt.setString(6, posting.getSummary());
            pstmt.setString(7, posting.getTechStack());
            pstmt.setString(8, posting.getQualifications());
            pstmt.setString(9, posting.getPreferred());
            pstmt.setString(10, posting.getCareerLevel());
            pstmt.setString(11, posting.getEducationLevel());
            pstmt.setString(12, posting.getSalary());
            pstmt.setString(13, posting.getRegion());
            pstmt.setString(14, posting.getDeadline());
            pstmt.setDate(15, toSqlDate(posting.getPostedAt()));
            pstmt.setTimestamp(16, toTimestamp(posting.getCollectedAt()));
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

    // JOB_SKILL_TREND 월별 집계용 — tech_stack이 있는 공고 전체를 훑어서 직무·월별로 묶어야 한다.
    public List<JobPostingDto> findAll() throws SQLException {
        String sql = "SELECT * FROM JOB_POSTING WHERE is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<JobPostingDto> postings = new ArrayList<>();
            while (rs.next()) {
                postings.add(mapRow(rs));
            }
            return postings;
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
        posting.setSource(rs.getString("source"));
        posting.setSourceUrl(rs.getString("source_url"));
        posting.setTitle(rs.getString("title"));
        posting.setCompanyName(rs.getString("company_name"));
        posting.setSummary(rs.getString("summary"));
        posting.setTechStack(rs.getString("tech_stack"));
        posting.setQualifications(rs.getString("qualifications"));
        posting.setPreferred(rs.getString("preferred"));
        posting.setCareerLevel(rs.getString("career_level"));
        posting.setEducationLevel(rs.getString("education_level"));
        posting.setSalary(rs.getString("salary"));
        posting.setRegion(rs.getString("region"));
        posting.setDeadline(rs.getString("deadline"));
        posting.setPostedAt(toLocalDate(rs.getDate("posted_at")));
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

    private Date toSqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
