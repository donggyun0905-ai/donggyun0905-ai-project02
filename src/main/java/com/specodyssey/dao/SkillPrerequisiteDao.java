package com.specodyssey.dao;

import com.specodyssey.dto.SkillPrerequisiteDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SKILL_PREREQUISITE 테이블 DAO — "A를 하기 전에 B".
 * 관련 요구사항: FR-33 로드맵 순서 근거 (2026-10-08 추가)
 *
 * 위상 정렬은 그래프 전체를 한 번에 봐야 하므로 edges()가 전체를 한 번에 읽어 온다.
 * 기술 171개 · 관계 수십 개 규모라 전부 읽어도 가볍고, 조회를 N번 하는 것보다 빠르다.
 */
public class SkillPrerequisiteDao {

    /** 전체 관계 — key = 뒤에 와야 하는 기술, value = 그보다 먼저 와야 하는 기술들 */
    public Map<Long, Set<Long>> edges() throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return edges(conn);
        }
    }

    public Map<Long, Set<Long>> edges(Connection conn) throws SQLException {
        String sql = "SELECT skill_id, prereq_skill_id FROM SKILL_PREREQUISITE WHERE is_deleted = FALSE "
                + "ORDER BY skill_id, prereq_skill_id";
        Map<Long, Set<Long>> prereqs = new LinkedHashMap<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                prereqs.computeIfAbsent(rs.getLong("skill_id"), key -> new LinkedHashSet<>())
                        .add(rs.getLong("prereq_skill_id"));
            }
        }
        return prereqs;
    }

    /** 관리자 화면 목록 — 기술 이름까지 함께 (화면이 id를 보여줄 수는 없다) */
    public List<SkillPrerequisiteDto> findAllWithNames() throws SQLException {
        String sql = "SELECT p.id, p.skill_id, p.prereq_skill_id, "
                + "s.skill_name AS skill_name, r.skill_name AS prereq_skill_name "
                + "FROM SKILL_PREREQUISITE p "
                + "JOIN SKILL s ON s.id = p.skill_id "
                + "JOIN SKILL r ON r.id = p.prereq_skill_id "
                + "WHERE p.is_deleted = FALSE ORDER BY s.skill_name, r.skill_name";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<SkillPrerequisiteDto> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapRow(rs));
            }
            return rows;
        }
    }

    /** 이미 있으면(논리 삭제된 것 포함) 되살리고, 없으면 새로 넣는다 — UNIQUE라 INSERT만 하면 깨진다 */
    public void upsert(Long skillId, Long prereqSkillId) throws SQLException {
        String sql = "INSERT INTO SKILL_PREREQUISITE (skill_id, prereq_skill_id) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, skillId);
            pstmt.setLong(2, prereqSkillId);
            pstmt.executeUpdate();
        }
    }

    public boolean delete(Long id) throws SQLException {
        String sql = "UPDATE SKILL_PREREQUISITE SET is_deleted = TRUE WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private SkillPrerequisiteDto mapRow(ResultSet rs) throws SQLException {
        SkillPrerequisiteDto dto = new SkillPrerequisiteDto();
        dto.setId(rs.getLong("id"));
        dto.setSkillId(rs.getLong("skill_id"));
        dto.setPrereqSkillId(rs.getLong("prereq_skill_id"));
        dto.setSkillName(rs.getString("skill_name"));
        dto.setPrereqSkillName(rs.getString("prereq_skill_name"));
        return dto;
    }
}
