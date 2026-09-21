package com.specodyssey.dao;

import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.DBUtil;

import java.math.BigDecimal;
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
 * USER_SKILLS 테이블 DAO.
 * 관련 요구사항: FR-25
 * 1주차에는 임베딩 매칭 파이프라인이 없으므로 skill_id/similarity_score는 항상 NULL로 들어가고,
 * raw_input만 보존한다 (2주차 매칭 배치의 원본 데이터).
 */
public class UserSkillDao {

    public Long insert(UserSkillDto skill) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, skill);
        }
    }

    public Long insert(Connection conn, UserSkillDto skill) throws SQLException {
        String sql = "INSERT INTO USER_SKILLS (user_id, skill_id, raw_input, similarity_score, proficiency) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, skill.getUserId());
            setNullableLong(pstmt, 2, skill.getSkillId());
            pstmt.setString(3, skill.getRawInput());
            setNullableBigDecimal(pstmt, 4, skill.getSimilarityScore());
            pstmt.setString(5, skill.getProficiency());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public boolean existsActiveRawInput(Long userId, String rawInput) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return existsActiveRawInput(conn, userId, rawInput);
        }
    }

    public boolean existsActiveRawInput(Long userId, String rawInput, Long excludeId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return existsActiveRawInput(conn, userId, rawInput, excludeId);
        }
    }

    // skill_id가 항상 NULL인 1주차 상태에서는 UNIQUE(user_id, skill_id)가 중복을 막지 못하므로
    // raw_input 기준으로 애플리케이션 레벨 중복 체크를 한다 (대소문자·공백 무시).
    public boolean existsActiveRawInput(Connection conn, Long userId, String rawInput) throws SQLException {
        return existsActiveRawInput(conn, userId, rawInput, null);
    }

    // 수정 시에는 자기 자신의 행을 중복으로 걸러내지 않도록 excludeId를 제외한다.
    public boolean existsActiveRawInput(Connection conn, Long userId, String rawInput, Long excludeId)
            throws SQLException {
        String sql = "SELECT 1 FROM USER_SKILLS " +
                "WHERE user_id = ? AND is_deleted = FALSE AND LOWER(TRIM(raw_input)) = LOWER(TRIM(?)) " +
                "AND (? IS NULL OR id <> ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, rawInput);
            setNullableLong(pstmt, 3, excludeId);
            setNullableLong(pstmt, 4, excludeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 기술명을 바꾸면 기존 임베딩 매칭 결과(skill_id·similarity_score)는 더 이상 유효하지 않으므로 초기화한다.
    // 2주차 매칭 배치가 다음 주기에 다시 채운다.
    public void update(Connection conn, UserSkillDto skill, Long userId) throws SQLException {
        String sql = "UPDATE USER_SKILLS SET raw_input = ?, proficiency = ?, skill_id = NULL, similarity_score = NULL " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, skill.getRawInput());
            pstmt.setString(2, skill.getProficiency());
            pstmt.setLong(3, skill.getId());
            pstmt.setLong(4, userId);
            pstmt.executeUpdate();
        }
    }

    public List<UserSkillDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM USER_SKILLS WHERE user_id = ? AND is_deleted = FALSE ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<UserSkillDto> skills = new ArrayList<>();
                while (rs.next()) {
                    skills.add(mapRow(rs));
                }
                return skills;
            }
        }
    }

    public void delete(Connection conn, Long userSkillId, Long userId) throws SQLException {
        String sql = "UPDATE USER_SKILLS SET is_deleted = TRUE WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userSkillId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    private UserSkillDto mapRow(ResultSet rs) throws SQLException {
        UserSkillDto skill = new UserSkillDto();
        skill.setId(rs.getLong("id"));
        skill.setUserId(rs.getLong("user_id"));
        skill.setSkillId(rs.getObject("skill_id", Long.class));
        skill.setRawInput(rs.getString("raw_input"));
        skill.setSimilarityScore(rs.getBigDecimal("similarity_score"));
        skill.setProficiency(rs.getString("proficiency"));
        skill.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        skill.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        skill.setDeleted(rs.getBoolean("is_deleted"));
        return skill;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private void setNullableBigDecimal(PreparedStatement pstmt, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.DECIMAL);
        } else {
            pstmt.setBigDecimal(index, value);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
