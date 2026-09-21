package com.specodyssey.dao;

import com.specodyssey.dto.EvaluationCriteriaDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * EVALUATION_CRITERIA 테이블 DAO.
 * 관련 요구사항: FR-83
 * 면접관이 회사 요구 역량과 가중치를 직접 구성·수정·삭제하는 리소스.
 * 소유자 확인은 user_id가 없으므로 부모 EVALUATION_SESSION의 session_token으로 대신한다.
 */
public class EvaluationCriteriaDao {

    public Long insert(EvaluationCriteriaDto criteria) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, criteria);
        }
    }

    public Long insert(Connection conn, EvaluationCriteriaDto criteria) throws SQLException {
        String sql = "INSERT INTO EVALUATION_CRITERIA (session_id, skill_id, weight) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, criteria.getSessionId());
            pstmt.setLong(2, criteria.getSkillId());
            pstmt.setInt(3, criteria.getWeight());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<EvaluationCriteriaDto> findBySessionId(Long sessionId) throws SQLException {
        String sql = "SELECT * FROM EVALUATION_CRITERIA WHERE session_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<EvaluationCriteriaDto> criteriaList = new ArrayList<>();
                while (rs.next()) {
                    criteriaList.add(mapRow(rs));
                }
                return criteriaList;
            }
        }
    }

    public void update(Connection conn, Long id, int weight, String sessionToken) throws SQLException {
        String sql = "UPDATE EVALUATION_CRITERIA SET weight = ? " +
                "WHERE id = ? AND session_id IN (SELECT id FROM EVALUATION_SESSION WHERE session_token = ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, weight);
            pstmt.setLong(2, id);
            pstmt.setString(3, sessionToken);
            pstmt.executeUpdate();
        }
    }

    public void delete(Connection conn, Long id, String sessionToken) throws SQLException {
        String sql = "UPDATE EVALUATION_CRITERIA SET is_deleted = TRUE " +
                "WHERE id = ? AND session_id IN (SELECT id FROM EVALUATION_SESSION WHERE session_token = ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.setString(2, sessionToken);
            pstmt.executeUpdate();
        }
    }

    private EvaluationCriteriaDto mapRow(ResultSet rs) throws SQLException {
        EvaluationCriteriaDto criteria = new EvaluationCriteriaDto();
        criteria.setId(rs.getLong("id"));
        criteria.setSessionId(rs.getLong("session_id"));
        criteria.setSkillId(rs.getLong("skill_id"));
        criteria.setWeight(rs.getObject("weight", Integer.class));
        criteria.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        criteria.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        criteria.setDeleted(rs.getBoolean("is_deleted"));
        return criteria;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
