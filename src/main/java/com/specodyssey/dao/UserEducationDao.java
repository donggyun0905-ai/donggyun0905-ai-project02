package com.specodyssey.dao;

import com.specodyssey.dto.UserEducationDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * USER_EDUCATION 테이블 DAO — 계정당 한 줄(UNIQUE user_id). 저장은 upsert 하나로 처리한다.
 * 관련 요구사항: FR-81 이력(학력) · NFR-4 공개 범위
 */
public class UserEducationDao {

    private static final String COLUMNS = "id, user_id, school_name, graduation_status, graduation_date, gpa, gpa_max, " +
            "created_at, updated_at, is_deleted";

    public UserEducationDto findByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USER_EDUCATION WHERE user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 지웠던 줄(is_deleted)이 있으면 되살려서 덮어쓴다 — UNIQUE(user_id)라 새 줄을 넣을 수 없다
    public void upsert(Connection conn, UserEducationDto education) throws SQLException {
        String sql = "INSERT INTO USER_EDUCATION (user_id, school_name, graduation_status, graduation_date, gpa, gpa_max) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE school_name = VALUES(school_name), graduation_status = VALUES(graduation_status), " +
                "graduation_date = VALUES(graduation_date), gpa = VALUES(gpa), gpa_max = VALUES(gpa_max), is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, education.getUserId());
            pstmt.setString(2, education.getSchoolName());
            pstmt.setString(3, education.getGraduationStatus());
            pstmt.setDate(4, education.getGraduationDate() == null ? null : Date.valueOf(education.getGraduationDate()));
            pstmt.setBigDecimal(5, education.getGpa());
            pstmt.setBigDecimal(6, education.getGpaMax());
            pstmt.executeUpdate();
        }
    }

    public void softDeleteByUserId(Connection conn, Long userId) throws SQLException {
        String sql = "UPDATE USER_EDUCATION SET is_deleted = TRUE WHERE user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        }
    }

    // 탈퇴 유예가 끝난 계정의 학력 — 학교·학점은 사람을 특정할 수 있어 USERS 개인정보와 같이 지운다(WithdrawalPurgeScheduler)
    public int purgeExpiredWithdrawals(java.time.LocalDateTime graceCutoff) throws SQLException {
        String sql = "UPDATE USER_EDUCATION e JOIN USERS u ON u.id = e.user_id " +
                "SET e.school_name = '', e.graduation_date = NULL, e.gpa = NULL, e.gpa_max = NULL, e.is_deleted = TRUE " +
                "WHERE u.is_deleted = TRUE AND u.withdraw_requested_at < ? AND e.school_name <> ''";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(graceCutoff));
            return pstmt.executeUpdate();
        }
    }

    private UserEducationDto mapRow(ResultSet rs) throws SQLException {
        UserEducationDto e = new UserEducationDto();
        e.setId(rs.getLong("id"));
        e.setUserId(rs.getLong("user_id"));
        e.setSchoolName(rs.getString("school_name"));
        e.setGraduationStatus(rs.getString("graduation_status"));
        Date graduationDate = rs.getDate("graduation_date");
        e.setGraduationDate(graduationDate == null ? null : graduationDate.toLocalDate());
        e.setGpa(rs.getBigDecimal("gpa"));
        e.setGpaMax(rs.getBigDecimal("gpa_max"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        e.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        e.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        e.setDeleted(rs.getBoolean("is_deleted"));
        return e;
    }
}
