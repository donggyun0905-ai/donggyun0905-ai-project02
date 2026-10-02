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

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, roadmap_id, step_order, step_type, tier, certification_id, " +
            "related_skill_id, reason, proof_type, proof_content, evidence_project_id, " +
            "review_status, review_note, is_completed, completed_at, created_at, " +
            "updated_at, is_deleted";
    private static final String RS_COLUMNS = "rs." + COLUMNS.replace(", ", ", rs.");

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

    // SKILL 단계 학습 검증(2026-09-30 팀 결정, 규칙 기반) — 증빙 제출 결과를 저장한다.
    // completed=true는 규칙 판정을 통과했을 때만 호출부(RoadmapService)가 넘긴다. NEEDS_REVISION이면
    // completed=false로 호출해 재제출을 받을 수 있게 완료 처리는 하지 않는다.
    // updateCompleted와 마찬가지로 ROADMAP 조인으로 소유자를 확인한다 — 다른 사용자의 단계에 증빙을
    // 남길 수 없다.
    public int updateProof(Connection conn, Long stepId, Long userId, String proofType, String proofContent,
            Long evidenceProjectId, String reviewStatus, String reviewNote, boolean completed,
            LocalDateTime completedAt) throws SQLException {
        String sql = "UPDATE ROADMAP_STEP rs JOIN ROADMAP r ON rs.roadmap_id = r.id " +
                "SET rs.proof_type = ?, rs.proof_content = ?, rs.evidence_project_id = ?, " +
                "    rs.review_status = ?, rs.review_note = ?, rs.is_completed = ?, rs.completed_at = ? " +
                "WHERE rs.id = ? AND r.user_id = ? AND rs.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, proofType);
            pstmt.setString(2, proofContent);
            setNullableLong(pstmt, 3, evidenceProjectId);
            pstmt.setString(4, reviewStatus);
            pstmt.setString(5, reviewNote);
            pstmt.setBoolean(6, completed);
            pstmt.setTimestamp(7, toTimestamp(completedAt));
            pstmt.setLong(8, stepId);
            pstmt.setLong(9, userId);
            return pstmt.executeUpdate();
        }
    }

    // FR-32 순서 있는 로드맵 — step_order 순
    public List<RoadmapStepDto> findByRoadmapId(Long roadmapId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM ROADMAP_STEP WHERE roadmap_id = ? AND is_deleted = FALSE ORDER BY step_order";
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
    // 소유자가 아니면(다른 사용자 id) 0을 반환한다 — 호출부(RoadmapService)가 이 값으로
    // 실제로 갱신됐을 때만 점수를 적립하도록 판단한다.
    // PROJECT 단계는 updateProof를 거치지 않아(완료 표시만 하고 프로젝트를 새로 만든다) 단계와 프로젝트의 연결이 없었다.
    // 완료를 취소했다가 다시 완료할 때 같은 프로젝트를 재사용하려고 evidence_project_id만 따로 채운다.
    public int setEvidenceProject(Connection conn, Long stepId, Long userId, Long evidenceProjectId) throws SQLException {
        String sql = "UPDATE ROADMAP_STEP rs JOIN ROADMAP r ON rs.roadmap_id = r.id " +
                "SET rs.evidence_project_id = ? " +
                "WHERE rs.id = ? AND r.user_id = ? AND rs.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setNullableLong(pstmt, 1, evidenceProjectId);
            pstmt.setLong(2, stepId);
            pstmt.setLong(3, userId);
            return pstmt.executeUpdate();
        }
    }

    public int updateCompleted(Connection conn, Long stepId, Long userId, boolean completed, LocalDateTime completedAt)
            throws SQLException {
        String sql = "UPDATE ROADMAP_STEP rs JOIN ROADMAP r ON rs.roadmap_id = r.id " +
                "SET rs.is_completed = ?, rs.completed_at = ? " +
                "WHERE rs.id = ? AND r.user_id = ? AND rs.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, completed);
            pstmt.setTimestamp(2, toTimestamp(completedAt));
            pstmt.setLong(3, stepId);
            pstmt.setLong(4, userId);
            return pstmt.executeUpdate();
        }
    }

    // 완료 처리 직후 step_type·related_skill_id·certification_id를 확인해 스펙/스킬 자동 반영 여부를
    // 판단하는 데 쓴다 (RoadmapService.completeStep).
    public RoadmapStepDto findById(Connection conn, Long stepId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM ROADMAP_STEP WHERE id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, stepId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // SKILL 단계 증빙 제출(submitSkillNote/submitSkillProjectStep) 진입 시 소유자·타입·완료 여부를
    // 한 번에 확인하기 위한 조회. findById와 달리 ROADMAP과 조인해 user_id를 검증한다 — 증빙 데이터를
    // 만들기 전에 먼저 걸러야 다른 사용자 소유 단계로는 아무 것도 만들어지지 않는다.
    public RoadmapStepDto findByIdForUser(Connection conn, Long stepId, Long userId) throws SQLException {
        String sql = "SELECT " + RS_COLUMNS + " FROM ROADMAP_STEP rs JOIN ROADMAP r ON rs.roadmap_id = r.id " +
                "WHERE rs.id = ? AND r.user_id = ? AND rs.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, stepId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 재생성할 때 "같은 기술의 같은 단계(티어)"를 이전에 이미 끝냈는지 확인한다 — 기술 하나가 입문→핵심→
    // 심화→전문가를 차례로 거치는 구조라서, 입문 노트만 끝낸 기술의 프로젝트 단계까지 완료로 승계하면 안 된다.
    public int countCompletedByUserSkillAndTier(Connection conn, Long userId, Long skillId, String tier)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM ROADMAP_STEP rs JOIN ROADMAP r ON rs.roadmap_id = r.id " +
                "WHERE r.user_id = ? AND rs.related_skill_id = ? AND rs.tier = ? AND rs.step_type = 'SKILL' " +
                "AND rs.is_completed = TRUE AND rs.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, skillId);
            pstmt.setString(3, tier);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // SKILL 단계 완료 횟수 — 숙련도 자동 승급 기준(팀 합의: 10회 INTERMEDIATE, 30회 ADVANCED).
    // 로드맵이 재생성돼도(새 ROADMAP) 같은 스킬이 다시 나오면 계속 누적되도록 roadmap_id로 좁히지 않는다.
    public int countCompletedByUserAndSkill(Connection conn, Long userId, Long skillId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ROADMAP_STEP rs JOIN ROADMAP r ON rs.roadmap_id = r.id " +
                "WHERE r.user_id = ? AND rs.related_skill_id = ? AND rs.step_type = 'SKILL' " +
                "AND rs.is_completed = TRUE AND rs.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, skillId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // 기술 복습 주기 계산용 — 이 사용자가 끝낸 SKILL·REVIEW 단계 전부(로드맵이 재생성돼도 이어지도록 로드맵을 가리지 않는다).
    public List<RoadmapStepDto> findCompletedSkillRowsByUser(Long userId) throws SQLException {
        String sql = "SELECT " + RS_COLUMNS + " FROM ROADMAP_STEP rs JOIN ROADMAP r ON rs.roadmap_id = r.id " +
                "WHERE r.user_id = ? AND r.is_deleted = FALSE AND rs.is_deleted = FALSE " +
                "AND rs.is_completed = TRUE AND rs.related_skill_id IS NOT NULL " +
                "AND rs.step_type IN ('SKILL', 'REVIEW')";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<RoadmapStepDto> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
                return rows;
            }
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
        step.setProofType(rs.getString("proof_type"));
        step.setProofContent(rs.getString("proof_content"));
        step.setEvidenceProjectId(rs.getObject("evidence_project_id", Long.class));
        step.setReviewStatus(rs.getString("review_status"));
        step.setReviewNote(rs.getString("review_note"));
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
