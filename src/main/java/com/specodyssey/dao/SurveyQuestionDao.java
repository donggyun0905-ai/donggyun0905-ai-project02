package com.specodyssey.dao;

import com.specodyssey.dto.SurveyQuestionDto;
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
 * SURVEY_QUESTION 테이블 DAO.
 * 관련 요구사항: FR-38 직무 발굴 + TD-5(d) 자가진단
 * 문항은 관리자/시드 데이터로 채워지는 마스터성 데이터라 읽기 + 등록만 지원한다.
 */
public class SurveyQuestionDao {

    public Long insert(SurveyQuestionDto question) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, question);
        }
    }

    public Long insert(Connection conn, SurveyQuestionDto question) throws SQLException {
        String sql = "INSERT INTO SURVEY_QUESTION (survey_type, content, job_category_hint, score_weight) " +
                "VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, question.getSurveyType());
            pstmt.setString(2, question.getContent());
            pstmt.setString(3, question.getJobCategoryHint());
            setNullableInt(pstmt, 4, question.getScoreWeight());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<SurveyQuestionDto> findAll() throws SQLException {
        String sql = "SELECT * FROM SURVEY_QUESTION WHERE is_deleted = FALSE ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<SurveyQuestionDto> questions = new ArrayList<>();
            while (rs.next()) {
                questions.add(mapRow(rs));
            }
            return questions;
        }
    }

    public List<SurveyQuestionDto> findBySurveyType(String surveyType) throws SQLException {
        String sql = "SELECT * FROM SURVEY_QUESTION WHERE survey_type = ? AND is_deleted = FALSE ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, surveyType);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<SurveyQuestionDto> questions = new ArrayList<>();
                while (rs.next()) {
                    questions.add(mapRow(rs));
                }
                return questions;
            }
        }
    }

    private SurveyQuestionDto mapRow(ResultSet rs) throws SQLException {
        SurveyQuestionDto question = new SurveyQuestionDto();
        question.setId(rs.getLong("id"));
        question.setSurveyType(rs.getString("survey_type"));
        question.setContent(rs.getString("content"));
        question.setJobCategoryHint(rs.getString("job_category_hint"));
        question.setScoreWeight(rs.getObject("score_weight", Integer.class));
        question.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        question.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        question.setDeleted(rs.getBoolean("is_deleted"));
        return question;
    }

    private void setNullableInt(PreparedStatement pstmt, int index, Integer value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, java.sql.Types.INTEGER);
        } else {
            pstmt.setInt(index, value);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
