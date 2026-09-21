package com.specodyssey.dao;

import com.specodyssey.dto.CertScheduleDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
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
 * CERT_SCHEDULE 테이블 DAO.
 * 관련 요구사항: FR-71 D-day 자동 생성
 * 자격증 시험 일정은 관리자/수집 배치가 채우는 데이터라 읽기 + 등록만 지원한다.
 */
public class CertScheduleDao {

    public Long insert(CertScheduleDto schedule) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, schedule);
        }
    }

    public Long insert(Connection conn, CertScheduleDto schedule) throws SQLException {
        String sql = "INSERT INTO CERT_SCHEDULE (certification_id, round_name, apply_start, apply_end, exam_date) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, schedule.getCertificationId());
            pstmt.setString(2, schedule.getRoundName());
            pstmt.setDate(3, java.sql.Date.valueOf(schedule.getApplyStart()));
            pstmt.setDate(4, java.sql.Date.valueOf(schedule.getApplyEnd()));
            pstmt.setDate(5, java.sql.Date.valueOf(schedule.getExamDate()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<CertScheduleDto> findByCertificationId(Long certificationId) throws SQLException {
        String sql = "SELECT * FROM CERT_SCHEDULE WHERE certification_id = ? AND is_deleted = FALSE " +
                "ORDER BY exam_date";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, certificationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<CertScheduleDto> schedules = new ArrayList<>();
                while (rs.next()) {
                    schedules.add(mapRow(rs));
                }
                return schedules;
            }
        }
    }

    private CertScheduleDto mapRow(ResultSet rs) throws SQLException {
        CertScheduleDto schedule = new CertScheduleDto();
        schedule.setId(rs.getLong("id"));
        schedule.setCertificationId(rs.getLong("certification_id"));
        schedule.setRoundName(rs.getString("round_name"));
        schedule.setApplyStart(toLocalDate(rs.getDate("apply_start")));
        schedule.setApplyEnd(toLocalDate(rs.getDate("apply_end")));
        schedule.setExamDate(toLocalDate(rs.getDate("exam_date")));
        schedule.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        schedule.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        schedule.setDeleted(rs.getBoolean("is_deleted"));
        return schedule;
    }

    private LocalDate toLocalDate(java.sql.Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
