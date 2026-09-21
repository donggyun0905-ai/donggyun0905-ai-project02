package com.specodyssey.dao;

import com.specodyssey.dto.RoadmapStepDto;
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
 * ROADMAP_STEP 테이블 DAO.
 * 관련 요구사항: FR-32 · 33 · 36
 * 단계 자체는 시스템(분석 결과)이 생성하고, 사용자는 완료 체크만 한다.
 */
public class RoadmapStepDao {

    public Long insert(RoadmapStepDto step) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, step);
        }
    }

    public Long insert(Connection conn, RoadmapStepDto step) throws SQLException {
        String sql = "INSERT INTO ROADMAP_STEP " +
                "(roadmap_id, step_order, step_type, tier, certification_id, related_skill_id, reason, " +
                " is_completed, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, step.getRoadmapId());
            pstmt.setInt(2, step.getStepOrder());
            pstmt.setString(3, step.getStepType());
            pstmt.setString(4, step.getTier());
            setNullableLong(pstmt, 5, step.getCertificationId());
            setNullableLong(pstmt, 6, step.getRelatedSkillId());
            pstmt.setString(7, step.getReason());
            pstmt.setBoolean(8, step.isCompleted());
            pstmt.setTimestamp(9, toTimestamp(step.getCompletedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // FR-32 순서 있는 로드맵 — step_order 순
    public List<RoadmapStepDto> findByRoadmapId(Long roadmapId) throws SQLException {
        String sql = "SELECT * FROM ROADMAP_STEP WHERE roadmap_id = ? AND is_deleted = FALSE ORDER BY step_order";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, roadmapId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<RoadmapStepDto> steps = new ArrayList<>();
                while (rs.next()) {
                    steps.add(mapRow(rs));
                }
                return steps;
            }
        }
    }

    // FR-36 완료 체크 → 여정 진행도·스코어 적립 근거
    // ROADMAP_STEP에는 user_id가 없어(부모 ROADMAP에만 있음) JOIN으로 소유자를 확인한다.
    // 없으면 다른 사용자의 로드맵 단계도 완료 처리할 수 있고, 점수(+100)가 걸려있어 조작 경로가 된다.
    public void updateCompleted(Connection conn, Long stepId, Long userId, boolean completed, LocalDateTime completedAt)
            throws SQLException {
        String sql = "UPDATE ROADMAP_STEP rs JOIN ROADMAP r ON rs.roadmap_id = r.id " +
                "SET rs.is_completed = ?, rs.completed_at = ? " +
                "WHERE rs.id = ? AND r.user_id = ? AND rs.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, completed);
            pstmt.setTimestamp(2, toTimestamp(completedAt));
            pstmt.setLong(3, stepId);
            pstmt.setLong(4, userId);
            pstmt.executeUpdate();
        }
    }

    private RoadmapStepDto mapRow(ResultSet rs) throws SQLException {
        RoadmapStepDto step = new RoadmapStepDto();
        step.setId(rs.getLong("id"));
        step.setRoadmapId(rs.getLong("roadmap_id"));
        step.setStepOrder(rs.getObject("step_order", Integer.class));
        step.setStepType(rs.getString("step_type"));
        step.setTier(rs.getString("tier"));
        step.setCertificationId(rs.getObject("certification_id", Long.class));
        step.setRelatedSkillId(rs.getObject("related_skill_id", Long.class));
        step.setReason(rs.getString("reason"));
        step.setCompleted(rs.getBoolean("is_completed"));
        step.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
        step.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        step.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        step.setDeleted(rs.getBoolean("is_deleted"));
        return step;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
