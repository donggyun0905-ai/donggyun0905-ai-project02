package com.specodyssey.dao;

import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * EVALUATION_SESSION 테이블 DAO.
 * 관련 요구사항: FR-82
 * 면접관은 계정이 없으므로(FR-14) session_token 자체가 소유 증명이다 — user_id 대신 토큰으로 소유자를 확인한다.
 */
public class EvaluationSessionDao {

    public Long insert(EvaluationSessionDto session) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, session);
        }
    }

    public Long insert(Connection conn, EvaluationSessionDto session) throws SQLException {
        String sql = "INSERT INTO EVALUATION_SESSION (session_token, company_name, expires_at) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, session.getSessionToken());
            pstmt.setString(2, session.getCompanyName());
            pstmt.setTimestamp(3, toTimestamp(session.getExpiresAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public EvaluationSessionDto findByToken(String sessionToken) throws SQLException {
        String sql = "SELECT * FROM EVALUATION_SESSION WHERE session_token = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionToken);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 토큰 소유자만 자기 세션의 회사명·만료시각을 바꿀 수 있다
    public void update(Connection conn, EvaluationSessionDto session) throws SQLException {
        String sql = "UPDATE EVALUATION_SESSION SET company_name = ?, expires_at = ? " +
                "WHERE id = ? AND session_token = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, session.getCompanyName());
            pstmt.setTimestamp(2, toTimestamp(session.getExpiresAt()));
            pstmt.setLong(3, session.getId());
            pstmt.setString(4, session.getSessionToken());
            pstmt.executeUpdate();
        }
    }

    public void delete(Connection conn, Long sessionId, String sessionToken) throws SQLException {
        String sql = "UPDATE EVALUATION_SESSION SET is_deleted = TRUE WHERE id = ? AND session_token = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, sessionId);
            pstmt.setString(2, sessionToken);
            pstmt.executeUpdate();
        }
    }

    private EvaluationSessionDto mapRow(ResultSet rs) throws SQLException {
        EvaluationSessionDto session = new EvaluationSessionDto();
        session.setId(rs.getLong("id"));
        session.setSessionToken(rs.getString("session_token"));
        session.setCompanyName(rs.getString("company_name"));
        session.setExpiresAt(toLocalDateTime(rs.getTimestamp("expires_at")));
        session.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        session.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        session.setDeleted(rs.getBoolean("is_deleted"));
        return session;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
