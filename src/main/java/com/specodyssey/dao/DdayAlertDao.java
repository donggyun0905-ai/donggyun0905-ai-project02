package com.specodyssey.dao;

import com.specodyssey.dto.DdayAlertDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DDAY_ALERT 테이블 DAO.
 * 관련 요구사항: FR-71 · 72
 * 자격증 일정에서 자동 생성되거나(cert_schedule_id) 사용자가 직접 등록(CUSTOM)하는 본인 소유 리소스.
 */
public class DdayAlertDao {

    public Long insert(DdayAlertDto alert) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, alert);
        }
    }

    public Long insert(Connection conn, DdayAlertDto alert) throws SQLException {
        String sql = "INSERT INTO DDAY_ALERT (user_id, cert_schedule_id, title, target_date, alert_type, is_notified) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, alert.getUserId());
            setNullableLong(pstmt, 2, alert.getCertScheduleId());
            pstmt.setString(3, alert.getTitle());
            pstmt.setDate(4, java.sql.Date.valueOf(alert.getTargetDate()));
            pstmt.setString(5, alert.getAlertType());
            pstmt.setBoolean(6, alert.isNotified());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // FR-72 마감 임박순 노출
    public List<DdayAlertDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM DDAY_ALERT WHERE user_id = ? AND is_deleted = FALSE ORDER BY target_date";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<DdayAlertDto> alerts = new ArrayList<>();
                while (rs.next()) {
                    alerts.add(mapRow(rs));
                }
                return alerts;
            }
        }
    }

    public void update(Connection conn, DdayAlertDto alert, Long userId) throws SQLException {
        String sql = "UPDATE DDAY_ALERT SET title = ?, target_date = ?, alert_type = ? " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, alert.getTitle());
            pstmt.setDate(2, java.sql.Date.valueOf(alert.getTargetDate()));
            pstmt.setString(3, alert.getAlertType());
            pstmt.setLong(4, alert.getId());
            pstmt.setLong(5, userId);
            pstmt.executeUpdate();
        }
    }

    public void delete(Connection conn, Long alertId, Long userId) throws SQLException {
        String sql = "UPDATE DDAY_ALERT SET is_deleted = TRUE WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, alertId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    private DdayAlertDto mapRow(ResultSet rs) throws SQLException {
        DdayAlertDto alert = new DdayAlertDto();
        alert.setId(rs.getLong("id"));
        alert.setUserId(rs.getLong("user_id"));
        alert.setCertScheduleId(rs.getObject("cert_schedule_id", Long.class));
        alert.setTitle(rs.getString("title"));
        java.sql.Date targetDate = rs.getDate("target_date");
        alert.setTargetDate(targetDate == null ? null : targetDate.toLocalDate());
        alert.setAlertType(rs.getString("alert_type"));
        alert.setNotified(rs.getBoolean("is_notified"));
        alert.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        alert.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        alert.setDeleted(rs.getBoolean("is_deleted"));
        return alert;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
