package com.specodyssey.dao;

import com.specodyssey.dto.TrendTechDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 트렌드 기술 수집 배치 전용 조회 DAO.
 * 관련 요구사항: FR-54 트렌드 기술 노출, FR-55 직무 연관 필터링
 * 기존 TrendTechDao/JobRequiredSkillDao는 건드리지 않고, 배치에만 필요한 조회를 여기에 모았다.
 */
public class TrendCollectDao {

    // 오늘 이미 수집했는지 — 서버 재기동 시 중복 실행 방지 (기준은 게시 시각이 아니라 수집 시각 created_at)
    public boolean existsCollectedSince(Connection conn, LocalDateTime since) throws SQLException {
        String sql = "SELECT 1 FROM TREND_TECH WHERE created_at >= ? AND is_deleted = FALSE LIMIT 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(since));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // TREND_TECH에는 UNIQUE가 없어서 같은 출처가 다시 쌓이지 않도록 저장 전에 확인한다
    public boolean existsBySourceUrl(Connection conn, String sourceUrl) throws SQLException {
        String sql = "SELECT 1 FROM TREND_TECH WHERE source_url = ? AND is_deleted = FALSE LIMIT 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sourceUrl);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 매일 같은 기술이 반복 노출되지 않도록, 최근에 저장한 기술명인지 확인한다 (대소문자 무시는 collation이 처리)
    public boolean existsByTechNameSince(Connection conn, String techName, LocalDateTime since) throws SQLException {
        String sql = "SELECT 1 FROM TREND_TECH WHERE tech_name = ? AND created_at >= ? AND is_deleted = FALSE LIMIT 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, techName);
            pstmt.setTimestamp(2, Timestamp.valueOf(since));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 이 기술을 요구하는 직무 목록 (job_id → importance: REQUIRED / PREFERRED)
    public Map<Long, String> findJobImportanceBySkillId(Connection conn, Long skillId) throws SQLException {
        String sql = "SELECT job_id, importance FROM JOB_REQUIRED_SKILL WHERE skill_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, skillId);
            try (ResultSet rs = pstmt.executeQuery()) {
                Map<Long, String> result = new LinkedHashMap<>();
                while (rs.next()) {
                    result.put(rs.getLong("job_id"), rs.getString("importance"));
                }
                return result;
            }
        }
    }

    // FR-55 사이드바: 사용자의 목표 직무와 연결된 기술. 가장 최근 수집분을 먼저, 그 안에서는 연관도 순.
    // (오늘 수집이 실패해도 직전 수집분이 그대로 보이도록 날짜로 자르지 않고 정렬만 한다)
    public List<TrendTechDto> findTopByJobId(Long jobId, int limit) throws SQLException {
        String sql = "SELECT t.* FROM TREND_TECH t " +
                "JOIN TREND_TECH_JOB j ON j.trend_tech_id = t.id " +
                "WHERE j.job_id = ? AND j.is_deleted = FALSE AND t.is_deleted = FALSE " +
                "ORDER BY DATE(t.created_at) DESC, j.relevance_score DESC, t.published_at DESC " +
                "LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TrendTechDto> items = new ArrayList<>();
                while (rs.next()) {
                    TrendTechDto tech = new TrendTechDto();
                    tech.setId(rs.getLong("id"));
                    tech.setTechName(rs.getString("tech_name"));
                    tech.setSummary(rs.getString("summary"));
                    tech.setSourceUrl(rs.getString("source_url"));
                    Timestamp published = rs.getTimestamp("published_at");
                    tech.setPublishedAt(published == null ? null : published.toLocalDateTime());
                    items.add(tech);
                }
                return items;
            }
        }
    }

    // 사이드바 — 이 직무에 연결된 트렌드 중 관련도가 기준 이상인 것만. 최근 수집분 먼저, 같은 날 안에서는 관련도 순.
    // (날짜로 자르지 않아 오늘 수집이 비어도 직전 수집분이 보인다)
    public List<TrendTechDto> findRelevantByJobId(Long jobId, double minRelevance, int limit) throws SQLException {
        String sql = "SELECT t.id, t.tech_name, t.summary, t.source_url, t.published_at FROM TREND_TECH t " +
                "JOIN TREND_TECH_JOB j ON j.trend_tech_id = t.id " +
                "WHERE j.job_id = ? AND j.relevance_score >= ? AND j.is_deleted = FALSE AND t.is_deleted = FALSE " +
                "ORDER BY DATE(t.created_at) DESC, j.relevance_score DESC, t.published_at DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            pstmt.setDouble(2, minRelevance);
            pstmt.setInt(3, limit);
            return readTrends(pstmt);
        }
    }

    // 사이드바 보충 — 같은 직무 계열(JOB.job_category)의 다른 직무에 연결된 트렌드. 여러 직무에 함께 연결된 기술은
    // 한 번만 나오도록 가장 높은 관련도로 묶는다. 예) 웹퍼블리셔 ← 프론트엔드 개발자·UI 개발자의 트렌드
    public List<TrendTechDto> findRelevantBySiblingJobs(Long jobId, double minRelevance, int limit) throws SQLException {
        String sql = "SELECT t.id, t.tech_name, t.summary, t.source_url, t.published_at FROM TREND_TECH t " +
                "JOIN TREND_TECH_JOB j ON j.trend_tech_id = t.id " +
                "JOIN JOB jb ON jb.id = j.job_id " +
                "WHERE jb.job_category = (SELECT job_category FROM JOB WHERE id = ?) AND j.job_id <> ? " +
                "AND j.relevance_score >= ? AND j.is_deleted = FALSE AND t.is_deleted = FALSE AND jb.is_deleted = FALSE " +
                "GROUP BY t.id, t.tech_name, t.summary, t.source_url, t.published_at, t.created_at " +
                "ORDER BY DATE(t.created_at) DESC, MAX(j.relevance_score) DESC, t.published_at DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            pstmt.setLong(2, jobId);
            pstmt.setDouble(3, minRelevance);
            pstmt.setInt(4, limit);
            return readTrends(pstmt);
        }
    }

    private List<TrendTechDto> readTrends(PreparedStatement pstmt) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            List<TrendTechDto> items = new ArrayList<>();
            while (rs.next()) {
                TrendTechDto tech = new TrendTechDto();
                tech.setId(rs.getLong("id"));
                tech.setTechName(rs.getString("tech_name"));
                tech.setSummary(rs.getString("summary"));
                tech.setSourceUrl(rs.getString("source_url"));
                Timestamp published = rs.getTimestamp("published_at");
                tech.setPublishedAt(published == null ? null : published.toLocalDateTime());
                items.add(tech);
            }
            return items;
        }
    }

    // 사이드바 대체 목록 — 목표 직무가 아직 없거나 그 직무에 연결된 트렌드가 하나도 없을 때, 직무와 상관없이
    // 가장 최근에 모은 트렌드를 보여준다(빈 위젯 대신 이전 트렌드라도 보이게, 2026-10-03 사용자 요청).
    public List<TrendTechDto> findRecent(int limit) throws SQLException {
        String sql = "SELECT id, tech_name, summary, source_url, published_at FROM TREND_TECH " +
                "WHERE is_deleted = FALSE ORDER BY created_at DESC, published_at DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TrendTechDto> items = new ArrayList<>();
                while (rs.next()) {
                    TrendTechDto tech = new TrendTechDto();
                    tech.setId(rs.getLong("id"));
                    tech.setTechName(rs.getString("tech_name"));
                    tech.setSummary(rs.getString("summary"));
                    tech.setSourceUrl(rs.getString("source_url"));
                    Timestamp published = rs.getTimestamp("published_at");
                    tech.setPublishedAt(published == null ? null : published.toLocalDateTime());
                    items.add(tech);
                }
                return items;
            }
        }
    }
}
