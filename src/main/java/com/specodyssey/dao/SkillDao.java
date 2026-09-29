package com.specodyssey.dao;

import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SKILL 테이블 DAO.
 * 1주차에는 마스터 데이터만 시드로 채워두고 조회 전용으로만 쓴다.
 * 임베딩 매칭에 필요한 저장·갱신 메서드는 2주차에 그 기능을 만드는 담당자가 추가한다.
 */
public class SkillDao {

    public List<SkillDto> findAll() throws SQLException {
        String sql = "SELECT * FROM SKILL WHERE is_deleted = FALSE ORDER BY skill_name";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<SkillDto> skills = new ArrayList<>();
            while (rs.next()) {
                skills.add(mapRow(rs));
            }
            return skills;
        }
    }

    public SkillDto findByName(String skillName) throws SQLException {
        String sql = "SELECT * FROM SKILL WHERE skill_name = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, skillName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // FR-32 로드맵 단계 표시용 — related_skill_id로 기술명을 조회한다.
    public SkillDto findById(Long id) throws SQLException {
        String sql = "SELECT * FROM SKILL WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private SkillDto mapRow(ResultSet rs) throws SQLException {
        SkillDto skill = new SkillDto();
        skill.setId(rs.getLong("id"));
        skill.setSkillName(rs.getString("skill_name"));
        skill.setCategory(rs.getString("category"));
        skill.setEmbeddingVector(rs.getString("embedding_vector"));
        skill.setEmbeddingModel(rs.getString("embedding_model"));
        skill.setEmbeddedAt(toLocalDateTime(rs.getTimestamp("embedded_at")));
        skill.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        skill.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        skill.setDeleted(rs.getBoolean("is_deleted"));
        return skill;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
