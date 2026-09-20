package com.specodyssey.dao;

import com.specodyssey.dto.UserProjectDto;
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
 * USER_PROJECTS 테이블 DAO.
 * 관련 요구사항: FR-24
 */
public class UserProjectDao {

    public Long insert(UserProjectDto project) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, project);
        }
    }

    public Long insert(Connection conn, UserProjectDto project) throws SQLException {
        String sql = "INSERT INTO USER_PROJECTS (user_id, title, description, tech_stack, start_date, end_date) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, project.getUserId());
            pstmt.setString(2, project.getTitle());
            pstmt.setString(3, project.getDescription());
            pstmt.setString(4, project.getTechStack());
            setNullableDate(pstmt, 5, project.getStartDate());
            setNullableDate(pstmt, 6, project.getEndDate());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<UserProjectDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM USER_PROJECTS WHERE user_id = ? AND is_deleted = FALSE ORDER BY start_date DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<UserProjectDto> projects = new ArrayList<>();
                while (rs.next()) {
                    projects.add(mapRow(rs));
                }
                return projects;
            }
        }
    }

    public void delete(Connection conn, Long projectId, Long userId) throws SQLException {
        String sql = "UPDATE USER_PROJECTS SET is_deleted = TRUE WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, projectId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    private UserProjectDto mapRow(ResultSet rs) throws SQLException {
        UserProjectDto project = new UserProjectDto();
        project.setId(rs.getLong("id"));
        project.setUserId(rs.getLong("user_id"));
        project.setTitle(rs.getString("title"));
        project.setDescription(rs.getString("description"));
        project.setTechStack(rs.getString("tech_stack"));
        java.sql.Date startDate = rs.getDate("start_date");
        project.setStartDate(startDate == null ? null : startDate.toLocalDate());
        java.sql.Date endDate = rs.getDate("end_date");
        project.setEndDate(endDate == null ? null : endDate.toLocalDate());
        project.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        project.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        project.setDeleted(rs.getBoolean("is_deleted"));
        return project;
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
