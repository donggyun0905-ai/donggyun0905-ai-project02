package com.specodyssey.dao;

import com.specodyssey.dto.ProblemDto;
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
 * PROBLEM 테이블 DAO.
 * 관련 요구사항: FR-51~53 일일 미션 + TD-3 코테 문제 소스
 * 문제 풀은 AI 생성/링크 추천/관리자 등록으로 채워지는 공용 풀이라 읽기 + 등록만 지원한다.
 * 사용자별 배정·완료 상태는 USER_DAILY_MISSION(UserDailyMissionDao)이 담당한다.
 */
public class ProblemDao {

    public Long insert(ProblemDto problem) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, problem);
        }
    }

    public Long insert(Connection conn, ProblemDto problem) throws SQLException {
        String sql = "INSERT INTO PROBLEM (title, description, difficulty_level, source_type, external_url, answer_key) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, problem.getTitle());
            pstmt.setString(2, problem.getDescription());
            pstmt.setInt(3, problem.getDifficultyLevel());
            pstmt.setString(4, problem.getSourceType());
            pstmt.setString(5, problem.getExternalUrl());
            pstmt.setString(6, problem.getAnswerKey());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<ProblemDto> findAll() throws SQLException {
        String sql = "SELECT * FROM PROBLEM WHERE is_deleted = FALSE ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<ProblemDto> problems = new ArrayList<>();
            while (rs.next()) {
                problems.add(mapRow(rs));
            }
            return problems;
        }
    }

    public List<ProblemDto> findByDifficultyLevel(int difficultyLevel) throws SQLException {
        String sql = "SELECT * FROM PROBLEM WHERE difficulty_level = ? AND is_deleted = FALSE ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, difficultyLevel);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ProblemDto> problems = new ArrayList<>();
                while (rs.next()) {
                    problems.add(mapRow(rs));
                }
                return problems;
            }
        }
    }

    private ProblemDto mapRow(ResultSet rs) throws SQLException {
        ProblemDto problem = new ProblemDto();
        problem.setId(rs.getLong("id"));
        problem.setTitle(rs.getString("title"));
        problem.setDescription(rs.getString("description"));
        problem.setDifficultyLevel(rs.getObject("difficulty_level", Integer.class));
        problem.setSourceType(rs.getString("source_type"));
        problem.setExternalUrl(rs.getString("external_url"));
        problem.setAnswerKey(rs.getString("answer_key"));
        problem.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        problem.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        problem.setDeleted(rs.getBoolean("is_deleted"));
        return problem;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
