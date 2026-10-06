package com.specodyssey.dao;

import com.specodyssey.dto.JobRequiredSkillDto;
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
 * JOB_REQUIRED_SKILL 테이블 DAO.
 * 관련 요구사항: FR-31 격차 분석 (규칙기반 대조)
 * 워크넷/LLM 수집 배치가 채우는 데이터라 읽기 + 등록만 지원한다.
 * 복합 UNIQUE(job_id, skill_id) 때문에 재수집 시에는 insert 전에 기존 행을 갱신하거나
 * 지우고 다시 넣는 판단은 배치를 만드는 담당자가 결정한다.
 */
public class JobRequiredSkillDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, job_id, skill_id, importance, required_level, source, is_estimated, " +
            "collected_at, created_at, updated_at, is_deleted";

    public Long insert(JobRequiredSkillDto item) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, item);
        }
    }

    public Long insert(Connection conn, JobRequiredSkillDto item) throws SQLException {
        String sql = "INSERT INTO JOB_REQUIRED_SKILL " +
                "(job_id, skill_id, importance, required_level, source, is_estimated, collected_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, item.getJobId());
            pstmt.setLong(2, item.getSkillId());
            pstmt.setString(3, item.getImportance());
            pstmt.setString(4, item.getRequiredLevel());
            pstmt.setString(5, item.getSource());
            pstmt.setBoolean(6, item.isEstimated());
            pstmt.setTimestamp(7, toTimestamp(item.getCollectedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 재수집 전 정리 — 이 직무의 해당 출처(예: NCS) 행만 논리 삭제한다. 다른 출처(MANUAL·WORKNET) 행은 건드리지 않는다.
    public int softDeleteByJobAndSource(Connection conn, Long jobId, String source) throws SQLException {
        String sql = "UPDATE JOB_REQUIRED_SKILL SET is_deleted = TRUE WHERE job_id = ? AND source = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            pstmt.setString(2, source);
            return pstmt.executeUpdate();
        }
    }

    /**
     * 같은 출처 행은 되살리고 갱신, 다른 출처가 이미 가진 (job_id, skill_id)는 그대로 둔다.
     * 복합 UNIQUE(job_id, skill_id) 때문에 한 기술에 출처는 하나 — 먼저 들어온 MANUAL·WORKNET 값을 NCS가 덮어쓰지 않는다.
     */
    public void upsertForSource(Connection conn, JobRequiredSkillDto item) throws SQLException {
        String sql = "INSERT INTO JOB_REQUIRED_SKILL " +
                "(job_id, skill_id, importance, required_level, source, is_estimated, collected_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "importance = IF(source = ?, ?, importance), " +
                "is_estimated = IF(source = ?, ?, is_estimated), " +
                "collected_at = IF(source = ?, ?, collected_at), " +
                "is_deleted = IF(source = ?, FALSE, is_deleted)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            Timestamp collectedAt = toTimestamp(item.getCollectedAt());
            pstmt.setLong(1, item.getJobId());
            pstmt.setLong(2, item.getSkillId());
            pstmt.setString(3, item.getImportance());
            pstmt.setString(4, item.getRequiredLevel());
            pstmt.setString(5, item.getSource());
            pstmt.setBoolean(6, item.isEstimated());
            pstmt.setTimestamp(7, collectedAt);
            pstmt.setString(8, item.getSource());
            pstmt.setString(9, item.getImportance());
            pstmt.setString(10, item.getSource());
            pstmt.setBoolean(11, item.isEstimated());
            pstmt.setString(12, item.getSource());
            pstmt.setTimestamp(13, collectedAt);
            pstmt.setString(14, item.getSource());
            pstmt.executeUpdate();
        }
    }

    // FR-31 격차 분석의 기준 — 직무별 요구 기술 전체 조회
    public List<JobRequiredSkillDto> findByJobId(Long jobId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return findByJobId(conn, jobId);
        }
    }

    public List<JobRequiredSkillDto> findByJobId(Connection conn, Long jobId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM JOB_REQUIRED_SKILL WHERE job_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<JobRequiredSkillDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
                return items;
            }
        }
    }

    // 관리자 기준 데이터 관리(2026-10-06)
    public void update(Long id, String importance, String requiredLevel) throws SQLException {
        String sql = "UPDATE JOB_REQUIRED_SKILL SET importance = ?, required_level = ? WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, importance);
            pstmt.setString(2, requiredLevel);
            pstmt.setLong(3, id);
            pstmt.executeUpdate();
        }
    }

    public void softDelete(Long id) throws SQLException {
        String sql = "UPDATE JOB_REQUIRED_SKILL SET is_deleted = TRUE WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    private JobRequiredSkillDto mapRow(ResultSet rs) throws SQLException {
        JobRequiredSkillDto item = new JobRequiredSkillDto();
        item.setId(rs.getLong("id"));
        item.setJobId(rs.getLong("job_id"));
        item.setSkillId(rs.getLong("skill_id"));
        item.setImportance(rs.getString("importance"));
        item.setRequiredLevel(rs.getString("required_level"));
        item.setSource(rs.getString("source"));
        item.setEstimated(rs.getBoolean("is_estimated"));
        item.setCollectedAt(toLocalDateTime(rs.getTimestamp("collected_at")));
        item.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        item.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        item.setDeleted(rs.getBoolean("is_deleted"));
        return item;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
