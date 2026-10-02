package com.specodyssey.dao;

import com.specodyssey.dto.ProjectTechNoteDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * PROJECT_TECH_NOTE 테이블 DAO.
 * 관련 요구사항: FR-24 · NFR-4 · 개발일지 4-4
 * 프로젝트에 쓴 기술마다 "어떻게 활용했는지"를 한 줄씩 둔다. 복합 UNIQUE (project_id, skill_id)라서
 * 같은 기술을 다시 적으면 새 행이 아니라 그 행을 갱신한다.
 */
public class ProjectTechNoteDao {

    /** 만들거나 갱신한다. 이전에 지운(is_deleted) 행이 있으면 되살린다. */
    public void upsert(Connection conn, ProjectTechNoteDto note) throws SQLException {
        String sql = "INSERT INTO PROJECT_TECH_NOTE (project_id, skill_id, description, consent_for_training) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE description = VALUES(description), " +
                "consent_for_training = VALUES(consent_for_training), is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, note.getProjectId());
            pstmt.setLong(2, note.getSkillId());
            pstmt.setString(3, note.getDescription());
            pstmt.setBoolean(4, note.isConsentForTraining());
            pstmt.executeUpdate();
        }
    }

    public List<ProjectTechNoteDto> findByProjectId(Long projectId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return findByProjectId(conn, projectId);
        }
    }

    // 기술 이름까지 같이 읽는다 — 화면에 "Redis: 세션 캐싱에 활용"처럼 보여주려고 SKILL을 조인한다.
    public List<ProjectTechNoteDto> findByProjectId(Connection conn, Long projectId) throws SQLException {
        String sql = "SELECT n.*, s.skill_name FROM PROJECT_TECH_NOTE n JOIN SKILL s ON s.id = n.skill_id " +
                "WHERE n.project_id = ? AND n.is_deleted = FALSE ORDER BY n.id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, projectId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ProjectTechNoteDto> notes = new ArrayList<>();
                while (rs.next()) {
                    notes.add(mapRow(rs));
                }
                return notes;
            }
        }
    }

    private ProjectTechNoteDto mapRow(ResultSet rs) throws SQLException {
        ProjectTechNoteDto note = new ProjectTechNoteDto();
        note.setId(rs.getLong("id"));
        note.setProjectId(rs.getLong("project_id"));
        note.setSkillId(rs.getLong("skill_id"));
        note.setSkillName(rs.getString("skill_name"));
        note.setDescription(rs.getString("description"));
        note.setConsentForTraining(rs.getBoolean("consent_for_training"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        note.setCreatedAt(created == null ? null : created.toLocalDateTime());
        note.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        note.setDeleted(rs.getBoolean("is_deleted"));
        return note;
    }
}
