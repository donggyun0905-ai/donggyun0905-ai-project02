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
 * 직무 발굴은 재응답을 허용하기로 해서 upsert를 추가했다(JobDiscoveryService).
 */
public class UserSurveyAnswerDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, user_id, question_id, answer_value, answered_at, created_at, updated_at, " +
            "is_deleted";

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
        String sql = "SELECT " + COLUMNS + " FROM USER_SURVEY_ANSWER WHERE user_id = ? AND is_deleted = FALSE";
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

    // 직무 발굴 설문(JOB_DISCOVERY)에 한 문항이라도 답했는지 — 처음 설문을 했는지 가르는 기준
    public boolean hasJobDiscoveryAnswer(Long userId) throws SQLException {
        String sql = "SELECT 1 FROM USER_SURVEY_ANSWER a JOIN SURVEY_QUESTION q ON q.id = a.question_id " +
                "WHERE a.user_id = ? AND a.is_deleted = FALSE AND q.survey_type = 'JOB_DISCOVERY' LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // FR-37 직무 발굴 설문을 마지막으로 제출한 시각 — 제출할 때마다 전 문항 answered_at이 갱신된다. 설문 전이면 null
    public LocalDateTime findLastJobDiscoveryAnsweredAt(Long userId) throws SQLException {
        String sql = "SELECT MAX(a.answered_at) FROM USER_SURVEY_ANSWER a JOIN SURVEY_QUESTION q ON q.id = a.question_id " +
                "WHERE a.user_id = ? AND a.is_deleted = FALSE AND q.survey_type = 'JOB_DISCOVERY'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? toLocalDateTime(rs.getTimestamp(1)) : null;
            }
        }
    }

    // 설문을 한 번이라도 한 사람이 아직 답하지 않은 살아 있는 직무 발굴 문항 수 — 문항이 늘거나 바뀌면 1 이상이 된다.
    // 설문을 아예 안 한 사람은 0(안내 대상이 아니다 — 그 사람은 처음부터 설문으로 보내진다).
    public int countUnansweredJobDiscoveryQuestions(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM SURVEY_QUESTION q " +
                "WHERE q.survey_type = 'JOB_DISCOVERY' AND q.is_deleted = FALSE " +
                "AND EXISTS (SELECT 1 FROM USER_SURVEY_ANSWER a0 JOIN SURVEY_QUESTION q0 ON q0.id = a0.question_id " +
                "            WHERE a0.user_id = ? AND a0.is_deleted = FALSE AND q0.survey_type = 'JOB_DISCOVERY') " +
                "AND NOT EXISTS (SELECT 1 FROM USER_SURVEY_ANSWER a " +
                "                WHERE a.user_id = ? AND a.question_id = q.id AND a.is_deleted = FALSE)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * 직무 발굴 설문 재응답(FR-38). 같은 (user_id, question_id)가 있으면 값만 덮어쓰고, 논리 삭제된 행도 되살린다.
     * JOB_DISCOVERY 문항 전용 — 자가진단(SELF_CHECK)은 초기 1회만 인정하는 정책이라 이 메서드를 쓰면 안 된다.
     */
    public void upsert(Connection conn, UserSurveyAnswerDto answer) throws SQLException {
        String sql = "INSERT INTO USER_SURVEY_ANSWER (user_id, question_id, answer_value, answered_at) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE answer_value = VALUES(answer_value), " +
                "answered_at = VALUES(answered_at), is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, answer.getUserId());
            pstmt.setLong(2, answer.getQuestionId());
            pstmt.setInt(3, answer.getAnswerValue());
            pstmt.setTimestamp(4, toTimestamp(answer.getAnsweredAt()));
            pstmt.executeUpdate();
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
