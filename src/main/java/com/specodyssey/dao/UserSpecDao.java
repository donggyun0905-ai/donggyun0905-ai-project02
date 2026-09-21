package com.specodyssey.dao;

import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * USER_SPECS 테이블 DAO.
 * 관련 요구사항: FR-23 · 81(면접관 타임라인)
 */
public class UserSpecDao {

    public Long insert(UserSpecDto spec) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, spec);
        }
    }

    // 프로필 변경 시 USERS.profile_updated_at과 같은 트랜잭션으로 묶기 위한 오버로드
    public Long insert(Connection conn, UserSpecDto spec) throws SQLException {
        String sql = "INSERT INTO USER_SPECS (user_id, spec_type, title, issuer, score, acquired_date) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, spec.getUserId());
            pstmt.setString(2, spec.getSpecType());
            pstmt.setString(3, spec.getTitle());
            pstmt.setString(4, spec.getIssuer());
            pstmt.setString(5, spec.getScore());
            setNullableDate(pstmt, 6, spec.getAcquiredDate());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // FR-81 취득일 순 타임라인 정렬
    public List<UserSpecDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM USER_SPECS WHERE user_id = ? AND is_deleted = FALSE ORDER BY acquired_date";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<UserSpecDto> specs = new ArrayList<>();
                while (rs.next()) {
                    specs.add(mapRow(rs));
                }
                return specs;
            }
        }
    }

    // 본인 소유가 아닌 id는 WHERE 조건에서 자연히 걸러진다 (0행 갱신)
    public void update(Connection conn, UserSpecDto spec, Long userId) throws SQLException {
        String sql = "UPDATE USER_SPECS SET spec_type = ?, title = ?, issuer = ?, score = ?, acquired_date = ? " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, spec.getSpecType());
            pstmt.setString(2, spec.getTitle());
            pstmt.setString(3, spec.getIssuer());
            pstmt.setString(4, spec.getScore());
            setNullableDate(pstmt, 5, spec.getAcquiredDate());
            pstmt.setLong(6, spec.getId());
            pstmt.setLong(7, userId);
            pstmt.executeUpdate();
        }
    }

    // 본인 소유가 아닌 id는 WHERE 조건에서 자연히 걸러진다 (0행 갱신)
    public void delete(Connection conn, Long specId, Long userId) throws SQLException {
        String sql = "UPDATE USER_SPECS SET is_deleted = TRUE WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, specId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    private UserSpecDto mapRow(ResultSet rs) throws SQLException {
        UserSpecDto spec = new UserSpecDto();
        spec.setId(rs.getLong("id"));
        spec.setUserId(rs.getLong("user_id"));
        spec.setSpecType(rs.getString("spec_type"));
        spec.setTitle(rs.getString("title"));
        spec.setIssuer(rs.getString("issuer"));
        spec.setScore(rs.getString("score"));
        java.sql.Date acquiredDate = rs.getDate("acquired_date");
        spec.setAcquiredDate(acquiredDate == null ? null : acquiredDate.toLocalDate());
        spec.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        spec.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        spec.setDeleted(rs.getBoolean("is_deleted"));
        return spec;
    }

    private void setNullableDate(PreparedStatement pstmt, int index, LocalDate date) throws SQLException {
        if (date == null) {
            pstmt.setNull(index, Types.DATE);
        } else {
            pstmt.setDate(index, java.sql.Date.valueOf(date));
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
