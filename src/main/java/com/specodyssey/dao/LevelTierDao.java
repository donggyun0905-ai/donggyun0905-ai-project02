package com.specodyssey.dao;

import com.specodyssey.dto.LevelTierDto;
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
 * LEVEL_TIER 테이블 DAO.
 * 관련 요구사항: TD-5 레벨/스코어링
 * 등급 구간·배점은 데이터로 관리하는 마스터성 데이터라 읽기 + 등록만 지원한다.
 */
public class LevelTierDao {

    public Long insert(LevelTierDto tier) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, tier);
        }
    }

    public Long insert(Connection conn, LevelTierDto tier) throws SQLException {
        String sql = "INSERT INTO LEVEL_TIER " +
                "(min_score, max_score, tier_name, title_name, problem_level_min, problem_level_max) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, tier.getMinScore());
            setNullableInt(pstmt, 2, tier.getMaxScore());
            pstmt.setString(3, tier.getTierName());
            pstmt.setString(4, tier.getTitleName());
            pstmt.setInt(5, tier.getProblemLevelMin());
            pstmt.setInt(6, tier.getProblemLevelMax());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 점수 구간 오름차순 — 대시보드에서 현재 등급 판정에 그대로 순회 사용 가능
    public List<LevelTierDto> findAll() throws SQLException {
        String sql = "SELECT * FROM LEVEL_TIER WHERE is_deleted = FALSE ORDER BY min_score";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<LevelTierDto> tiers = new ArrayList<>();
            while (rs.next()) {
                tiers.add(mapRow(rs));
            }
            return tiers;
        }
    }

    private LevelTierDto mapRow(ResultSet rs) throws SQLException {
        LevelTierDto tier = new LevelTierDto();
        tier.setId(rs.getLong("id"));
        tier.setMinScore(rs.getObject("min_score", Integer.class));
        tier.setMaxScore(rs.getObject("max_score", Integer.class));
        tier.setTierName(rs.getString("tier_name"));
        tier.setTitleName(rs.getString("title_name"));
        tier.setProblemLevelMin(rs.getObject("problem_level_min", Integer.class));
        tier.setProblemLevelMax(rs.getObject("problem_level_max", Integer.class));
        tier.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        tier.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        tier.setDeleted(rs.getBoolean("is_deleted"));
        return tier;
    }

    private void setNullableInt(PreparedStatement pstmt, int index, Integer value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.INTEGER);
        } else {
            pstmt.setInt(index, value);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
