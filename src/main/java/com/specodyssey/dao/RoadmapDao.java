package com.specodyssey.dao;

import com.specodyssey.dto.RoadmapDto;
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
 * ROADMAP 테이블 DAO.
 * 관련 요구사항: FR-32 · 36 · 37
 * 재분석 시 새 버전이 insert되고(version 증가), is_active/is_primary만 갱신 대상이다.
 * 로드맵 자체를 삭제하는 기능은 없다(버전 이력 보존 원칙 — docs/db-design.md).
 */
public class RoadmapDao {

    public Long insert(RoadmapDto roadmap) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, roadmap);
        }
    }

    public Long insert(Connection conn, RoadmapDto roadmap) throws SQLException {
        String sql = "INSERT INTO ROADMAP (user_id, gap_analysis_id, version, is_active, is_primary, target_level) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, roadmap.getUserId());
            pstmt.setLong(2, roadmap.getGapAnalysisId());
            pstmt.setInt(3, roadmap.getVersion());
            pstmt.setBoolean(4, roadmap.isActive());
            pstmt.setBoolean(5, roadmap.isPrimary());
            pstmt.setString(6, roadmap.getTargetLevel());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<RoadmapDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM ROADMAP WHERE user_id = ? AND is_deleted = FALSE ORDER BY version DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<RoadmapDto> roadmaps = new ArrayList<>();
                while (rs.next()) {
                    roadmaps.add(mapRow(rs));
                }
                return roadmaps;
            }
        }
    }

    // 대시보드·일일 미션이 바라보는 메인 여정
    public RoadmapDto findPrimaryByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM ROADMAP WHERE user_id = ? AND is_primary = TRUE AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // FR-37 재분석으로 새 버전이 생길 때 이전 버전은 비활성화, 대표 로드맵 지정은 호출부에서 트랜잭션으로 함께 처리
    public void updateActiveAndPrimary(Connection conn, Long roadmapId, boolean active, boolean primary)
            throws SQLException {
        String sql = "UPDATE ROADMAP SET is_active = ?, is_primary = ? WHERE id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, active);
            pstmt.setBoolean(2, primary);
            pstmt.setLong(3, roadmapId);
            pstmt.executeUpdate();
        }
    }

    private RoadmapDto mapRow(ResultSet rs) throws SQLException {
        RoadmapDto roadmap = new RoadmapDto();
        roadmap.setId(rs.getLong("id"));
        roadmap.setUserId(rs.getLong("user_id"));
        roadmap.setGapAnalysisId(rs.getLong("gap_analysis_id"));
        roadmap.setVersion(rs.getObject("version", Integer.class));
        roadmap.setActive(rs.getBoolean("is_active"));
        roadmap.setPrimary(rs.getBoolean("is_primary"));
        roadmap.setTargetLevel(rs.getString("target_level"));
        roadmap.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        roadmap.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        roadmap.setDeleted(rs.getBoolean("is_deleted"));
        return roadmap;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
