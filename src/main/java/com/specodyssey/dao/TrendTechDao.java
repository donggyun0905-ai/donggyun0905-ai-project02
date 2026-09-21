package com.specodyssey.dao;

import com.specodyssey.dto.TrendTechDto;
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
 * TREND_TECH 테이블 DAO.
 * 관련 요구사항: FR-54 트렌드 기술 사이드바
 * 외부 수집 배치가 채우는 뉴스성 데이터라 읽기 + 등록만 지원한다.
 */
public class TrendTechDao {

    public Long insert(TrendTechDto trendTech) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, trendTech);
        }
    }

    public Long insert(Connection conn, TrendTechDto trendTech) throws SQLException {
        String sql = "INSERT INTO TREND_TECH (tech_name, summary, source_url, published_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, trendTech.getTechName());
            pstmt.setString(2, trendTech.getSummary());
            pstmt.setString(3, trendTech.getSourceUrl());
            pstmt.setTimestamp(4, toTimestamp(trendTech.getPublishedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // FR-54 최신순 노출
    public List<TrendTechDto> findAll() throws SQLException {
        String sql = "SELECT * FROM TREND_TECH WHERE is_deleted = FALSE ORDER BY published_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<TrendTechDto> items = new ArrayList<>();
            while (rs.next()) {
                items.add(mapRow(rs));
            }
            return items;
        }
    }

    private TrendTechDto mapRow(ResultSet rs) throws SQLException {
        TrendTechDto trendTech = new TrendTechDto();
        trendTech.setId(rs.getLong("id"));
        trendTech.setTechName(rs.getString("tech_name"));
        trendTech.setSummary(rs.getString("summary"));
        trendTech.setSourceUrl(rs.getString("source_url"));
        trendTech.setPublishedAt(toLocalDateTime(rs.getTimestamp("published_at")));
        trendTech.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        trendTech.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        trendTech.setDeleted(rs.getBoolean("is_deleted"));
        return trendTech;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
