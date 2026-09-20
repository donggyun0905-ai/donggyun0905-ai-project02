package com.specodyssey.dao;

import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * USERS 테이블 DAO.
 * 관련 요구사항: FR-11 · 12 · 13 · 14 · 21 · 22
 */
public class UserDao {

    // FR-11~13 회원가입
    public Long insert(UserDto user) throws SQLException {
        String sql = "INSERT INTO USERS " +
                "(user_type, login_id, password_hash, email, major, grade, interest_field, " +
                " desired_job_id, desired_job_status, privacy_consent_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, user.getUserType());
            pstmt.setString(2, user.getLoginId());
            pstmt.setString(3, user.getPasswordHash());
            pstmt.setString(4, user.getEmail());
            pstmt.setString(5, user.getMajor());
            pstmt.setString(6, user.getGrade());
            pstmt.setString(7, user.getInterestField());
            setNullableLong(pstmt, 8, user.getDesiredJobId());
            pstmt.setString(9, user.getDesiredJobStatus());
            pstmt.setTimestamp(10, toTimestamp(user.getPrivacyConsentAt()));

            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return null;
        }
    }

    // FR-14 아이디 중복 확인
    public boolean existsByLoginId(String loginId) throws SQLException {
        String sql = "SELECT 1 FROM USERS WHERE login_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loginId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // FR-12 로그인
    public UserDto findByLoginId(String loginId) throws SQLException {
        String sql = "SELECT * FROM USERS WHERE login_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loginId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 세션 기반 본인 프로필 조회
    public UserDto findById(Long id) throws SQLException {
        String sql = "SELECT * FROM USERS WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // FR-21 · 22 프로필 수정 (기본정보 + 희망 직무)
    public void updateProfile(UserDto user) throws SQLException {
        String sql = "UPDATE USERS SET " +
                "email = ?, major = ?, grade = ?, interest_field = ?, " +
                "desired_job_id = ?, desired_job_status = ?, profile_updated_at = ? " +
                "WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getEmail());
            pstmt.setString(2, user.getMajor());
            pstmt.setString(3, user.getGrade());
            pstmt.setString(4, user.getInterestField());
            setNullableLong(pstmt, 5, user.getDesiredJobId());
            pstmt.setString(6, user.getDesiredJobStatus());
            pstmt.setTimestamp(7, toTimestamp(user.getProfileUpdatedAt()));
            pstmt.setLong(8, user.getId());

            pstmt.executeUpdate();
        }
    }

    // FR-12 로그인 성공 시 마지막 접속 시각 갱신
    public void updateLastLogin(Long id) throws SQLException {
        String sql = "UPDATE USERS SET last_login_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    // FR-37 재분석 트리거 기준 — 스펙/프로젝트/스킬 변경 시 같은 트랜잭션에서 호출
    public void touchProfileUpdatedAt(Connection conn, Long userId) throws SQLException {
        String sql = "UPDATE USERS SET profile_updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        }
    }

    // FR-13 회원 탈퇴 — 논리 삭제
    public void softDelete(Long id) throws SQLException {
        String sql = "UPDATE USERS SET is_deleted = TRUE WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    private UserDto mapRow(ResultSet rs) throws SQLException {
        UserDto user = new UserDto();
        user.setId(rs.getLong("id"));
        user.setUserType(rs.getString("user_type"));
        user.setLoginId(rs.getString("login_id"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setEmail(rs.getString("email"));
        user.setMajor(rs.getString("major"));
        user.setGrade(rs.getString("grade"));
        user.setInterestField(rs.getString("interest_field"));
        user.setDesiredJobId(rs.getObject("desired_job_id", Long.class));
        user.setDesiredJobStatus(rs.getString("desired_job_status"));
        user.setPrivacyConsentAt(toLocalDateTime(rs.getTimestamp("privacy_consent_at")));
        user.setProfileUpdatedAt(toLocalDateTime(rs.getTimestamp("profile_updated_at")));
        user.setLastLoginAt(toLocalDateTime(rs.getTimestamp("last_login_at")));
        user.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        user.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        user.setDeleted(rs.getBoolean("is_deleted"));
        return user;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private Timestamp toTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
