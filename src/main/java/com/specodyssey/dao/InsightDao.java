package com.specodyssey.dao;

import com.specodyssey.util.DBUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 데이터 인사이트 화면용 집계 조회 전용 DAO (읽기만, 쓰기 없음).
 * 관련 요구사항: FR-45 · 47 · 48
 * 여러 테이블을 묶어 세는 쿼리라 테이블별 DAO에 넣지 않고 여기에 모았다.
 */
public class InsightDao {

    /** 또래 비교 대상 1명의 최신 완성도 점수. */
    public record PeerScoreRow(long userId, BigDecimal score) {
    }

    /** 직무·기술·월별 언급 비율. */
    public record TrendRow(long skillId, String skillName, String periodYm, BigDecimal mentionRatio) {
    }

    /** 격차 분석 항목 1개 — 기술 분야·요구 수준·충족 여부. */
    public record GapCellRow(String category, String requiredLevel, boolean missing) {
    }

    // FR-45 같은 전공·학년 사용자들의 "가장 최근" 스냅샷 점수 (사용자당 1행)
    public List<PeerScoreRow> findLatestPeerScores(String major, String grade) throws SQLException {
        String sql = "SELECT h.user_id, h.completeness_score FROM SPEC_SCORE_HISTORY h " +
                "JOIN (SELECT user_id, MAX(snapshot_date) AS last_date FROM SPEC_SCORE_HISTORY " +
                "      WHERE major = ? AND grade = ? AND is_deleted = FALSE GROUP BY user_id) latest " +
                "  ON latest.user_id = h.user_id AND latest.last_date = h.snapshot_date " +
                "WHERE h.major = ? AND h.grade = ? AND h.is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, major);
            pstmt.setString(2, grade);
            pstmt.setString(3, major);
            pstmt.setString(4, grade);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<PeerScoreRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new PeerScoreRow(rs.getLong("user_id"), rs.getBigDecimal("completeness_score")));
                }
                return rows;
            }
        }
    }

    // FR-45 내 가장 최근 완성도 점수 — 스냅샷이 없으면 null
    public BigDecimal findLatestScore(Long userId) throws SQLException {
        String sql = "SELECT completeness_score FROM SPEC_SCORE_HISTORY " +
                "WHERE user_id = ? AND is_deleted = FALSE ORDER BY snapshot_date DESC, id DESC LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("completeness_score") : null;
            }
        }
    }

    // FR-47 목표 직무의 최근 N개월 기술별 언급 비율 (오래된 달 → 최근 달)
    public List<TrendRow> findRecentTrend(Long jobId, int months) throws SQLException {
        String sql = "SELECT t.skill_id, s.skill_name, t.period_ym, t.mention_ratio FROM JOB_SKILL_TREND t " +
                "JOIN SKILL s ON s.id = t.skill_id AND s.is_deleted = FALSE " +
                "JOIN (SELECT DISTINCT period_ym FROM JOB_SKILL_TREND " +
                "      WHERE job_id = ? AND is_deleted = FALSE ORDER BY period_ym DESC LIMIT ?) p " +
                "  ON p.period_ym = t.period_ym " +
                "WHERE t.job_id = ? AND t.is_deleted = FALSE " +
                "ORDER BY t.period_ym, t.mention_ratio DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            pstmt.setInt(2, months);
            pstmt.setLong(3, jobId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TrendRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new TrendRow(rs.getLong("skill_id"), rs.getString("skill_name"),
                            rs.getString("period_ym"), rs.getBigDecimal("mention_ratio")));
                }
                return rows;
            }
        }
    }

    // FR-48 해당 직무에 대한 나의 최신 격차 분석 항목 — 분석 기록이 없으면 빈 목록
    public List<GapCellRow> findLatestGapCells(Long userId, Long jobId) throws SQLException {
        String sql = "SELECT s.category, jrs.required_level, gi.status FROM GAP_ANALYSIS_ITEM gi " +
                "JOIN SKILL s ON s.id = gi.skill_id " +
                "LEFT JOIN JOB_REQUIRED_SKILL jrs ON jrs.job_id = ? AND jrs.skill_id = gi.skill_id " +
                "  AND jrs.is_deleted = FALSE " +
                "WHERE gi.is_deleted = FALSE AND gi.gap_analysis_id = (" +
                "  SELECT id FROM GAP_ANALYSIS WHERE user_id = ? AND job_id = ? AND is_deleted = FALSE " +
                "  ORDER BY analyzed_at DESC, id DESC LIMIT 1)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            pstmt.setLong(2, userId);
            pstmt.setLong(3, jobId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<GapCellRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new GapCellRow(rs.getString("category"), rs.getString("required_level"),
                            "MISSING".equals(rs.getString("status"))));
                }
                return rows;
            }
        }
    }
}
