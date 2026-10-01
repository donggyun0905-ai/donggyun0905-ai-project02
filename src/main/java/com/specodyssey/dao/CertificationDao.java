package com.specodyssey.dao;

import com.specodyssey.dto.CertificationDto;
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
 * CERTIFICATION 테이블 DAO.
 * 1주차에는 마스터 데이터만 시드로 채워두고 조회 전용으로만 쓴다.
 * 로드맵 자격증 단계·D-day 연동에 필요한 메서드는 2주차 담당자가 추가한다.
 */
public class CertificationDao {

    public List<CertificationDto> findAll() throws SQLException {
        String sql = "SELECT * FROM CERTIFICATION WHERE is_deleted = FALSE ORDER BY cert_name";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<CertificationDto> certifications = new ArrayList<>();
            while (rs.next()) {
                certifications.add(mapRow(rs));
            }
            return certifications;
        }
    }

    public CertificationDto findById(Long id) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return findById(conn, id);
        }
    }

    // 로드맵 CERT 단계 완료 시 USER_SPECS 자동 반영에 필요 (ROADMAP_STEP.certification_id로 조회)
    public CertificationDto findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT * FROM CERTIFICATION WHERE id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public CertificationDto findByName(String certName) throws SQLException {
        String sql = "SELECT * FROM CERTIFICATION WHERE cert_name = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, certName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // FR-32 로드맵 자격증 단계 후보 조회 — 난이도 낮은 순(ENTRY 티어에 맞는 것부터).
    // job_category = 'COMMON'(컴활·정보처리기능사 등 특정 직무에 안 묶이는 범용 자격증)도 항상 같이
    // 조회해야 한다 — db-design.md·role-plan.md에 명시된 원칙인데 누락돼 있었다(2026-09-30 수정).
    // 대상 직무 카테고리를 COMMON보다 먼저 보여주도록 정렬한다.
    public List<CertificationDto> findByJobCategory(String jobCategory) throws SQLException {
        String sql = "SELECT * FROM CERTIFICATION WHERE (job_category = ? OR job_category = 'COMMON') " +
                "AND is_deleted = FALSE ORDER BY (job_category = ?) DESC, difficulty_level ASC, cert_name ASC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, jobCategory);
            pstmt.setString(2, jobCategory);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<CertificationDto> certifications = new ArrayList<>();
                while (rs.next()) {
                    certifications.add(mapRow(rs));
                }
                return certifications;
            }
        }
    }

    private CertificationDto mapRow(ResultSet rs) throws SQLException {
        CertificationDto cert = new CertificationDto();
        cert.setId(rs.getLong("id"));
        cert.setCertName(rs.getString("cert_name"));
        cert.setIssuer(rs.getString("issuer"));
        cert.setJobCategory(rs.getString("job_category"));
        cert.setDifficultyLevel(rs.getObject("difficulty_level", Integer.class));
        cert.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        cert.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        cert.setDeleted(rs.getBoolean("is_deleted"));
        return cert;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
