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

    // 프로필의 희망 직무 검색창 — 정식 명칭을 그대로 입력했을 때 매칭용.
    // utf8mb4_unicode_ci 콜레이션이라 대소문자 구분 없이 매칭된다.
    public JobDto findByName(String jobName) throws SQLException {
        String sql = "SELECT * FROM JOB WHERE job_name = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, jobName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // JOB_REQUIRED_SKILL 재수집으로 실제 요구 기술 목록이 바뀌었을 때 호출한다(2026-09-30 팀 결정).
    // 내용이 같으면 재수집해도 이 메서드를 호출하지 않아야 한다 — 그래야 트렌드가 실제로 바뀐
    // 직무를 목표로 삼은 사용자에게만 로드맵 화면에 "요구 기술이 바뀌었어요" 배너가 뜬다.
    public void bumpRequirementVersion(Connection conn, Long jobId) throws SQLException {
        String sql = "UPDATE JOB SET requirement_version = requirement_version + 1 WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            pstmt.executeUpdate();
        }
    }

    private JobDto mapRow(ResultSet rs) throws SQLException {
        JobDto job = new JobDto();
        job.setId(rs.getLong("id"));
        job.setJobName(rs.getString("job_name"));
        job.setJobCategory(rs.getString("job_category"));
        job.setPopular(rs.getBoolean("is_popular"));
        job.setLastCollectedAt(toLocalDateTime(rs.getTimestamp("last_collected_at")));
        job.setRequirementVersion(rs.getInt("requirement_version"));
        job.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        job.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        job.setDeleted(rs.getBoolean("is_deleted"));
        return job;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
