package com.specodyssey.dao;

import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 활동 내역 조회 (FR-81 확장, 2026-10-07) — 면접관 화면의 "활동 내역"(날짜별 잔디 + 타임라인)이 읽는다.
 *
 * SCORE_LOG를 재료로 쓴다. 점수를 적립한 기록이 곧 "그날 무엇을 했는지"이기 때문이다
 * (ROADMAP 단계 완료 · PROBLEM 문제 풀이 · STREAK 연속 기록). 쓰기는 없는 읽기 전용 DAO다.
 *
 * ROADMAP 신호는 ref_id가 ROADMAP_STEP.id라서 무엇을 했는지(단계 종류·설명)까지 함께 읽는다.
 * 그 외 신호는 종류와 점수만 보여 준다 — 문제 지문·정답 같은 내용은 공유 대상이 아니다.
 */
public class ActivityDao {

    /** 하루치 활동량 — 잔디 한 칸. */
    public record DayCount(LocalDate date, int events, int points) {
        public LocalDate getDate() {
            return date;
        }

        public int getEvents() {
            return events;
        }

        public int getPoints() {
            return points;
        }
    }

    /** 타임라인 한 줄. stepType·reason은 ROADMAP 신호일 때만 채워진다(아니면 null). */
    public record ActivityRow(java.time.LocalDateTime earnedAt, String signalType, int points,
                              String stepType, String reason) {
        public java.time.LocalDateTime getEarnedAt() {
            return earnedAt;
        }

        public String getSignalType() {
            return signalType;
        }

        public int getPoints() {
            return points;
        }

        public String getStepType() {
            return stepType;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 날짜별 활동량 — since 이후로, 날짜 오름차순.
     * 하루에 여러 번 적립해도 한 칸이 되도록 날짜로 묶는다.
     */
    public List<DayCount> findDailyCounts(Long userId, LocalDate since) throws SQLException {
        String sql = "SELECT DATE(earned_at) AS day, COUNT(*) AS events, COALESCE(SUM(points), 0) AS points "
                + "FROM SCORE_LOG WHERE user_id = ? AND earned_at >= ? "
                + "GROUP BY DATE(earned_at) ORDER BY day";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setObject(2, since.atStartOfDay());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<DayCount> days = new ArrayList<>();
                while (rs.next()) {
                    days.add(new DayCount(rs.getObject("day", LocalDate.class),
                            rs.getInt("events"), rs.getInt("points")));
                }
                return days;
            }
        }
    }

    /** 최근 활동 — 최신이 먼저. ROADMAP 신호는 어떤 단계였는지 함께 읽는다. */
    public List<ActivityRow> findRecent(Long userId, int limit) throws SQLException {
        String sql = "SELECT l.earned_at, l.signal_type, l.points, s.step_type, s.reason "
                + "FROM SCORE_LOG l "
                + "LEFT JOIN ROADMAP_STEP s ON l.signal_type = 'ROADMAP' AND s.id = l.ref_id "
                + "WHERE l.user_id = ? ORDER BY l.earned_at DESC, l.id DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ActivityRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new ActivityRow(
                            rs.getObject("earned_at", java.time.LocalDateTime.class),
                            rs.getString("signal_type"),
                            rs.getInt("points"),
                            rs.getString("step_type"),
                            rs.getString("reason")));
                }
                return rows;
            }
        }
    }

    /** 활동한 날의 수 — "꾸준히 했는지"를 한 숫자로. */
    public int countActiveDays(Long userId, LocalDate since) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT DATE(earned_at)) FROM SCORE_LOG WHERE user_id = ? AND earned_at >= ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setObject(2, since.atStartOfDay());
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
