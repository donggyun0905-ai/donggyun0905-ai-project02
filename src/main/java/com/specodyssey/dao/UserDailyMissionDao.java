package com.specodyssey.dao;

import com.specodyssey.dto.UserDailyMissionDto;
import com.specodyssey.util.DBUtil;

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
 * USER_DAILY_MISSION 테이블 DAO.
 * 관련 요구사항: FR-51~53
 * 배정은 시스템(일일 배치)이 하고, 사용자는 완료 체크·정답 여부만 갱신한다.
 */
public class UserDailyMissionDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, user_id, problem_id, assigned_date, is_completed, completed_at, " +
            "is_correct, created_at, updated_at, is_deleted";

    public Long insert(UserDailyMissionDto mission) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, mission);
        }
    }

    public Long insert(Connection conn, UserDailyMissionDto mission) throws SQLException {
        String sql = "INSERT INTO USER_DAILY_MISSION " +
                "(user_id, problem_id, assigned_date, is_completed, completed_at, is_correct) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, mission.getUserId());
            pstmt.setLong(2, mission.getProblemId());
            pstmt.setDate(3, java.sql.Date.valueOf(mission.getAssignedDate()));
            pstmt.setBoolean(4, mission.isCompleted());
            pstmt.setTimestamp(5, toTimestamp(mission.getCompletedAt()));
            setNullableBoolean(pstmt, 6, mission.getCorrect());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // FR-53 스트릭·진행도 계산용 — 배정일 순
    // 미션 새로 불러오기(2026-10-12) — 그날 아직 안 푼 미션만 논리 삭제한다. 푼 미션·실패한 미션은 그대로 둔다.
    public int softDeleteUncompletedByDate(Connection conn, Long userId, java.time.LocalDate date) throws SQLException {
        String sql = "UPDATE USER_DAILY_MISSION SET is_deleted = TRUE " +
                "WHERE user_id = ? AND assigned_date = ? AND is_completed = FALSE AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setDate(2, java.sql.Date.valueOf(date));
            return pstmt.executeUpdate();
        }
    }

    // 미션 완료·실패 취소(2026-10-12) — 본인 미션을 안 푼 상태로 되돌린다. 제출 코드도 비운다. 바뀐 행 수(0이면 남의 미션·없음).
    public int resetCompletion(Connection conn, Long userId, Long missionId) throws SQLException {
        String sql = "UPDATE USER_DAILY_MISSION SET is_completed = FALSE, completed_at = NULL, is_correct = NULL, " +
                "submitted_code = NULL, submitted_language = NULL, submitted_at = NULL " +
                "WHERE id = ? AND user_id = ? AND is_completed = TRUE AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, missionId);
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate();
        }
    }

    public List<UserDailyMissionDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USER_DAILY_MISSION WHERE user_id = ? AND is_deleted = FALSE " +
                "ORDER BY assigned_date DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<UserDailyMissionDto> missions = new ArrayList<>();
                while (rs.next()) {
                    missions.add(mapRow(rs));
                }
                return missions;
            }
        }
    }

    // userId로 소유자를 확인한다 — 없으면 다른 사용자의 미션도 완료 처리할 수 있다.
    public void updateCompleted(Connection conn, Long id, Long userId, boolean completed, LocalDateTime completedAt,
                                 Boolean correct) throws SQLException {
        String sql = "UPDATE USER_DAILY_MISSION SET is_completed = ?, completed_at = ?, is_correct = ? " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, completed);
            pstmt.setTimestamp(2, toTimestamp(completedAt));
            setNullableBoolean(pstmt, 3, correct);
            pstmt.setLong(4, id);
            pstmt.setLong(5, userId);
            pstmt.executeUpdate();
        }
    }

    private UserDailyMissionDto mapRow(ResultSet rs) throws SQLException {
        UserDailyMissionDto mission = new UserDailyMissionDto();
        mission.setId(rs.getLong("id"));
        mission.setUserId(rs.getLong("user_id"));
        mission.setProblemId(rs.getLong("problem_id"));
        java.sql.Date assignedDate = rs.getDate("assigned_date");
        mission.setAssignedDate(assignedDate == null ? null : assignedDate.toLocalDate());
        mission.setCompleted(rs.getBoolean("is_completed"));
        mission.setCompletedAt(toLocalDateTime(rs.getTimestamp("completed_at")));
        mission.setCorrect(rs.getObject("is_correct", Boolean.class));
        mission.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        mission.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        mission.setDeleted(rs.getBoolean("is_deleted"));
        return mission;
    }

    private void setNullableBoolean(PreparedStatement pstmt, int index, Boolean value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BOOLEAN);
        } else {
            pstmt.setBoolean(index, value);
        }
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
