package com.specodyssey.dao;

import com.specodyssey.dto.SimulationStateDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.TransactionUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 테스트 계정 시뮬레이션 DAO — USERS.is_test 확인, SIMULATION_STATE, 그리고 시뮬레이션이 남긴 행의 날짜 맞추기·초기화.
 *
 * 초기화(resetTestUserData)는 CLAUDE.md "물리 삭제 금지"의 예외다 — 테스트 계정(is_test = TRUE)에 한해서만,
 * 같은 날짜를 다시 돌릴 때 복합 UNIQUE에 걸리지 않도록 행을 실제로 지운다. 테스트 계정이 아니면 아무것도 지우지 않는다.
 */
public class SimulationDao {

    private static final String COLUMNS =
            "id, user_id, status, persona, target_score, start_date, total_days, days_done, started_at, last_error";

    // ---------------------------------------------------------------- 테스트 계정

    public boolean isTestAccount(Long userId) throws SQLException {
        String sql = "SELECT is_test FROM USERS WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    // ---------------------------------------------------------------- 진행 상태

    public SimulationStateDto findByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SIMULATION_STATE WHERE user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public void insert(SimulationStateDto s) throws SQLException {
        String sql = "INSERT INTO SIMULATION_STATE (user_id, status, persona, target_score, start_date, total_days, days_done, started_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, s.getUserId());
            pstmt.setString(2, s.getStatus());
            pstmt.setString(3, s.getPersona());
            pstmt.setObject(4, s.getTargetScore());
            pstmt.setDate(5, Date.valueOf(s.getStartDate()));
            pstmt.setInt(6, s.getTotalDays());
            pstmt.setInt(7, s.getDaysDone());
            pstmt.setTimestamp(8, Timestamp.valueOf(s.getStartedAt()));
            pstmt.executeUpdate();
        }
    }

    /** 상태만 바꾼다 (실행·일시정지). lastError가 null이면 지난 오류도 지운다. */
    public int updateStatus(Long userId, String status, String lastError) throws SQLException {
        String sql = "UPDATE SIMULATION_STATE SET status = ?, last_error = ? WHERE user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, truncate(lastError));
            pstmt.setLong(3, userId);
            return pstmt.executeUpdate();
        }
    }

    /** 끝난 뒤 더 높은 목표로 이어 갈 때 — 목표와 최대 날 수를 바꾸고 다시 RUNNING */
    public int updateTarget(Long userId, int targetScore, int totalDays) throws SQLException {
        String sql = "UPDATE SIMULATION_STATE SET target_score = ?, total_days = ?, status = ?, last_error = NULL "
                + "WHERE user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, targetScore);
            pstmt.setInt(2, totalDays);
            pstmt.setString(3, SimulationStateDto.RUNNING);
            pstmt.setLong(4, userId);
            return pstmt.executeUpdate();
        }
    }

    /** 하루를 끝냈을 때 — 끝낸 날 수를 올리고, 다 끝났으면 DONE. */
    public int updateProgress(Long userId, int daysDone, String status) throws SQLException {
        String sql = "UPDATE SIMULATION_STATE SET days_done = ?, status = ? WHERE user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, daysDone);
            pstmt.setString(2, status);
            pstmt.setLong(3, userId);
            return pstmt.executeUpdate();
        }
    }

    /** 서버가 다시 켜졌을 때 — 돌던 시뮬레이션은 작업 스레드가 사라졌으니 일시정지로 바꾼다. */
    public int pauseAllRunning(String reason) throws SQLException {
        String sql = "UPDATE SIMULATION_STATE SET status = ?, last_error = ? WHERE status = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, SimulationStateDto.PAUSED);
            pstmt.setString(2, truncate(reason));
            pstmt.setString(3, SimulationStateDto.RUNNING);
            return pstmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- 하루 마무리: 날짜 맞추기

    /**
     * 시뮬레이션 하루를 끝낸 뒤, 그날 생긴 행의 created_at·updated_at을 그날 날짜로 맞춘다.
     * 서비스는 업무 날짜(배정일·적립 시각·완료 시각)를 AppClock으로 이미 그날로 남기지만, 공통 컬럼은 DB 기본값(실제 지금)이라
     * "로드맵을 만든 지 며칠 지났는지"처럼 created_at을 보는 규칙(트렌딩 학습 주기 등)이 어긋나지 않게 한다.
     * 업무 날짜로 범위를 좁혀, 같은 계정이 지금 실제로 쓴 행(오늘 미션 등)은 건드리지 않는다.
     */
    public void backdateDay(Long userId, LocalDate date) throws SQLException {
        Timestamp dayStart = Timestamp.valueOf(date.atStartOfDay());
        Timestamp nextDay = Timestamp.valueOf(date.plusDays(1).atStartOfDay());
        Timestamp noon = Timestamp.valueOf(date.atTime(LocalTime.NOON));
        TransactionUtil.runInTransaction(conn -> {
            update(conn, "UPDATE USER_DAILY_MISSION SET created_at = ?, updated_at = COALESCE(completed_at, ?) "
                    + "WHERE user_id = ? AND assigned_date = ?", noon, noon, userId, Date.valueOf(date));
            update(conn, "UPDATE SCORE_LOG SET created_at = earned_at, updated_at = earned_at "
                    + "WHERE user_id = ? AND earned_at >= ? AND earned_at < ?", userId, dayStart, nextDay);
            update(conn, "UPDATE SPEC_SCORE_HISTORY SET created_at = ?, updated_at = ? "
                    + "WHERE user_id = ? AND snapshot_date = ?", noon, noon, userId, Date.valueOf(date));
            update(conn, "UPDATE ROADMAP r JOIN GAP_ANALYSIS g ON g.id = r.gap_analysis_id "
                    + "SET r.created_at = g.analyzed_at, r.updated_at = g.analyzed_at "
                    + "WHERE r.user_id = ? AND g.analyzed_at >= ? AND g.analyzed_at < ? AND r.created_at >= ?",
                    userId, dayStart, nextDay, nextDay);
            update(conn, "UPDATE GAP_ANALYSIS SET created_at = analyzed_at, updated_at = analyzed_at "
                    + "WHERE user_id = ? AND analyzed_at >= ? AND analyzed_at < ?", userId, dayStart, nextDay);
            // 그날 새로 생긴 단계(복습·트렌딩 학습 등) — 실제 시각으로 들어가 있으니 그날로
            update(conn, "UPDATE ROADMAP_STEP s JOIN ROADMAP r ON r.id = s.roadmap_id "
                    + "SET s.created_at = ?, s.updated_at = ? WHERE r.user_id = ? AND s.created_at >= ?",
                    noon, noon, userId, nextDay);
            // 그날 끝낸 단계
            update(conn, "UPDATE ROADMAP_STEP s JOIN ROADMAP r ON r.id = s.roadmap_id "
                    + "SET s.updated_at = s.completed_at WHERE r.user_id = ? AND s.completed_at >= ? AND s.completed_at < ?",
                    userId, dayStart, nextDay);
            return null;
        });
    }

    // ---------------------------------------------------------------- 초기화

    /**
     * 테스트 계정의 활동 데이터를 지운다 — 미션·점수·연속 기록·스펙 점수 기록·격차 분석·로드맵, 시뮬레이션 시작 뒤에 프로필에
     * 생긴 기술·자격증, 진행 상태. 계정과 시작 전 프로필은 남긴다. 한 트랜잭션.
     * @param since 시뮬레이션을 처음 시작한 시각 (없으면 프로필 행은 지우지 않는다)
     * @return 테이블별 지운 행 수 (확인·테스트용)
     * @throws SecurityException 테스트 계정이 아님 — 아무것도 지우지 않는다
     */
    public Map<String, Integer> resetTestUserData(Long userId, LocalDateTime since) throws SQLException {
        return TransactionUtil.runInTransaction(conn -> {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT is_test FROM USERS WHERE id = ? FOR UPDATE")) {
                pstmt.setLong(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next() || !rs.getBoolean(1)) {
                        throw new SecurityException("테스트 계정만 초기화할 수 있습니다.");
                    }
                }
            }
            Map<String, Integer> deleted = new LinkedHashMap<>();
            // 로드맵 단계를 가리키는 서류·글은 지우지 않고 연결만 끊는다 (FK)
            update(conn, "UPDATE DOCUMENTS d JOIN ROADMAP_STEP s ON s.id = d.roadmap_step_id JOIN ROADMAP r ON r.id = s.roadmap_id "
                    + "SET d.roadmap_step_id = NULL WHERE r.user_id = ?", userId);
            update(conn, "UPDATE TECH_ARTICLE t JOIN ROADMAP_STEP s ON s.id = t.roadmap_step_id JOIN ROADMAP r ON r.id = s.roadmap_id "
                    + "SET t.roadmap_step_id = NULL WHERE r.user_id = ?", userId);
            deleted.put("ROADMAP_STEP", update(conn,
                    "DELETE s FROM ROADMAP_STEP s JOIN ROADMAP r ON r.id = s.roadmap_id WHERE r.user_id = ?", userId));
            deleted.put("ROADMAP", update(conn, "DELETE FROM ROADMAP WHERE user_id = ?", userId));
            deleted.put("GAP_ANALYSIS_ITEM", update(conn,
                    "DELETE i FROM GAP_ANALYSIS_ITEM i JOIN GAP_ANALYSIS g ON g.id = i.gap_analysis_id WHERE g.user_id = ?", userId));
            deleted.put("GAP_ANALYSIS", update(conn, "DELETE FROM GAP_ANALYSIS WHERE user_id = ?", userId));
            deleted.put("USER_DAILY_MISSION", update(conn, "DELETE FROM USER_DAILY_MISSION WHERE user_id = ?", userId));
            deleted.put("SCORE_LOG", update(conn, "DELETE FROM SCORE_LOG WHERE user_id = ?", userId));
            deleted.put("USER_SCORE_SUMMARY", update(conn, "DELETE FROM USER_SCORE_SUMMARY WHERE user_id = ?", userId));
            deleted.put("SPEC_SCORE_HISTORY", update(conn, "DELETE FROM SPEC_SCORE_HISTORY WHERE user_id = ?", userId));
            if (since != null) {
                Timestamp from = Timestamp.valueOf(since);
                deleted.put("USER_SKILLS", update(conn, "DELETE FROM USER_SKILLS WHERE user_id = ? AND created_at >= ?", userId, from));
                deleted.put("USER_SPECS", update(conn, "DELETE FROM USER_SPECS WHERE user_id = ? AND created_at >= ?", userId, from));
            }
            deleted.put("SIMULATION_STATE", update(conn, "DELETE FROM SIMULATION_STATE WHERE user_id = ?", userId));
            return deleted;
        });
    }

    // ----------------------------------------------------------------

    private static int update(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            return pstmt.executeUpdate();
        }
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }

    private SimulationStateDto mapRow(ResultSet rs) throws SQLException {
        SimulationStateDto s = new SimulationStateDto();
        s.setId(rs.getLong("id"));
        s.setUserId(rs.getLong("user_id"));
        s.setStatus(rs.getString("status"));
        s.setPersona(rs.getString("persona"));
        int target = rs.getInt("target_score");
        s.setTargetScore(rs.wasNull() ? null : target);
        s.setStartDate(rs.getDate("start_date").toLocalDate());
        s.setTotalDays(rs.getInt("total_days"));
        s.setDaysDone(rs.getInt("days_done"));
        Timestamp startedAt = rs.getTimestamp("started_at");
        s.setStartedAt(startedAt == null ? null : startedAt.toLocalDateTime());
        s.setLastError(rs.getString("last_error"));
        return s;
    }
}
