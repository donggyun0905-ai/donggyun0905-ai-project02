package com.specodyssey.dao;

import com.specodyssey.dto.GapAnalysisItemDto;
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
 * GAP_ANALYSIS_ITEM 테이블 DAO.
 * 관련 요구사항: FR-42 비교표, FR-48 부족 역량 히트맵
 * GAP_ANALYSIS 스냅샷의 자식 항목이라 수정·삭제는 없다.
 */
public class GapAnalysisItemDao {

    public Long insert(GapAnalysisItemDto item) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, item);
        }
    }

    public Long insert(Connection conn, GapAnalysisItemDto item) throws SQLException {
        String sql = "INSERT INTO GAP_ANALYSIS_ITEM (gap_analysis_id, skill_id, status, similarity_score) " +
                "VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, item.getGapAnalysisId());
            // skill_id는 컬럼이 NOT NULL이라, null이 오면 DAO에서 NPE로 터지는 대신
            // DB가 SQLException(제약 위반)으로 깨끗하게 거부하게 한다 — TransactionUtil이 이걸 롤백한다.
            setNullableLong(pstmt, 2, item.getSkillId());
            pstmt.setString(3, item.getStatus());
            pstmt.setBigDecimal(4, item.getSimilarityScore());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    public List<GapAnalysisItemDto> findByGapAnalysisId(Long gapAnalysisId) throws SQLException {
        String sql = "SELECT * FROM GAP_ANALYSIS_ITEM WHERE gap_analysis_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, gapAnalysisId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<GapAnalysisItemDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
                return items;
            }
        }
    }

    private GapAnalysisItemDto mapRow(ResultSet rs) throws SQLException {
        GapAnalysisItemDto item = new GapAnalysisItemDto();
        item.setId(rs.getLong("id"));
        item.setGapAnalysisId(rs.getLong("gap_analysis_id"));
        item.setSkillId(rs.getLong("skill_id"));
        item.setStatus(rs.getString("status"));
        item.setSimilarityScore(rs.getBigDecimal("similarity_score"));
        item.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        item.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        item.setDeleted(rs.getBoolean("is_deleted"));
        return item;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
