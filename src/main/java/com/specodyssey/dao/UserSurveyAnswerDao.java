package com.specodyssey.dao;

import com.specodyssey.dto.UserSurveyAnswerDto;
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
 * USER_SURVEY_ANSWER 테이블 DAO.
 * 관련 요구사항: FR-38 직무 발굴 + 자가진단(TD-5 d)
 * 자가진단은 초기 1회만 인정하는 정책(docs/db-design.md)이라 수정·삭제는 두지 않는다.
 * 복합 UNIQUE(user_id, question_id) 위반(재응답)은 이 응답을 다루는 서비스가 판단한다.
 */
public class UserSurveyAnswerDao {

    public Long insert(UserSurveyAnswerDto answer) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, answer);
        }
    }

    public Long insert(Connection conn, UserSurveyAnswerDto answer) throws SQLException {
        String sql = "INSERT INTO USER_SURVEY_ANSWER (user_id, question_id, answer_value, answered_at) " +
                "VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, answer.getUserId());
            pstmt.setLong(2, answer.getQuestionId());
            pstmt.setInt(3, answer.getAnswerValue());
            pstmt.setTimestamp(4, toTimestamp(answer.getAnsweredAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<UserSurveyAnswerDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM USER_SURVEY_ANSWER WHERE user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<UserSurveyAnswerDto> answers = new ArrayList<>();
                while (rs.next()) {
                    answers.add(mapRow(rs));
                }
                return answers;
            }
        }
    }

    private UserSurveyAnswerDto mapRow(ResultSet rs) throws SQLException {
        UserSurveyAnswerDto answer = new UserSurveyAnswerDto();
        answer.setId(rs.getLong("id"));
        answer.setUserId(rs.getLong("user_id"));
        answer.setQuestionId(rs.getLong("question_id"));
        answer.setAnswerValue(rs.getObject("answer_value", Integer.class));
        answer.setAnsweredAt(toLocalDateTime(rs.getTimestamp("answered_at")));
        answer.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        answer.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        answer.setDeleted(rs.getBoolean("is_deleted"));
        return answer;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
