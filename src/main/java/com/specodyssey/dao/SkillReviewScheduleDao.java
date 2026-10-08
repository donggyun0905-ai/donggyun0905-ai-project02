package com.specodyssey.dao;

import com.specodyssey.dto.SkillReviewScheduleDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SKILL_REVIEW_SCHEDULE 테이블 DAO — 사람·기술별 간격 반복 복습 일정 (2026-10-08).
 * 관련 요구사항: FR-39 로드맵 복습
 */
public class SkillReviewScheduleDao {

    private static final String COLUMNS =
            "id, user_id, skill_id, ease_factor, interval_days, repetitions, last_quality, "
            + "last_reviewed_at, due_at";

    /** 이 사람의 모든 일정 — key = skill_id. 복습 대상을 고를 때 한 번에 읽는다. */
    public Map<Long, SkillReviewScheduleDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SKILL_REVIEW_SCHEDULE "
                + "WHERE user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                Map<Long, SkillReviewScheduleDto> bySkill = new LinkedHashMap<>();
                while (rs.next()) {
                    SkillReviewScheduleDto row = mapRow(rs);
                    bySkill.put(row.getSkillId(), row);
                }
                return bySkill;
            }
        }
    }

    public SkillReviewScheduleDto find(Connection conn, Long userId, Long skillId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SKILL_REVIEW_SCHEDULE "
                + "WHERE user_id = ? AND skill_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, skillId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * 일정을 넣거나 갱신한다. (user_id, skill_id)가 UNIQUE라 INSERT만 하면 두 번째 복습에서 깨진다.
     * 논리 삭제됐던 행도 되살린다 — 같은 기술을 다시 익히기 시작한 경우다.
     */
    public void upsert(Connection conn, SkillReviewScheduleDto schedule) throws SQLException {
        String sql = "INSERT INTO SKILL_REVIEW_SCHEDULE "
                + "(user_id, skill_id, ease_factor, interval_days, repetitions, last_quality, last_reviewed_at, due_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE ease_factor = VALUES(ease_factor), "
                + "interval_days = VALUES(interval_days), repetitions = VALUES(repetitions), "
                + "last_quality = VALUES(last_quality), last_reviewed_at = VALUES(last_reviewed_at), "
                + "due_at = VALUES(due_at), is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, schedule.getUserId());
            pstmt.setLong(2, schedule.getSkillId());
            pstmt.setBigDecimal(3, java.math.BigDecimal.valueOf(schedule.getEaseFactor()));
            pstmt.setInt(4, schedule.getIntervalDays());
            pstmt.setInt(5, schedule.getRepetitions());
            if (schedule.getLastQuality() == null) {
                pstmt.setNull(6, java.sql.Types.TINYINT);
            } else {
                pstmt.setInt(6, schedule.getLastQuality());
            }
            pstmt.setTimestamp(7, toTimestamp(schedule.getLastReviewedAt()));
            pstmt.setTimestamp(8, toTimestamp(schedule.getDueAt()));
            pstmt.executeUpdate();
        }
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private SkillReviewScheduleDto mapRow(ResultSet rs) throws SQLException {
        SkillReviewScheduleDto dto = new SkillReviewScheduleDto();
        dto.setId(rs.getLong("id"));
        dto.setUserId(rs.getLong("user_id"));
        dto.setSkillId(rs.getLong("skill_id"));
        dto.setEaseFactor(rs.getBigDecimal("ease_factor").doubleValue());
        dto.setIntervalDays(rs.getInt("interval_days"));
        dto.setRepetitions(rs.getInt("repetitions"));
        int quality = rs.getInt("last_quality");
        dto.setLastQuality(rs.wasNull() ? null : quality);
        Timestamp reviewed = rs.getTimestamp("last_reviewed_at");
        dto.setLastReviewedAt(reviewed == null ? null : reviewed.toLocalDateTime());
        Timestamp due = rs.getTimestamp("due_at");
        dto.setDueAt(due == null ? null : due.toLocalDateTime());
        return dto;
    }
}
