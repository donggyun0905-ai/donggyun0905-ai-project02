package com.specodyssey.dao;

import com.specodyssey.dto.GapAnalysisDto;
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
 * GAP_ANALYSIS 테이블 DAO.
 * 관련 요구사항: FR-31 · 37 · 42
 * 사용자가 분석을 실행할 때마다 새 스냅샷이 쌓이는 구조라 수정·삭제는 없다(재분석 = 새 행 추가).
 */
public class GapAnalysisDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, user_id, job_id, match_rate, job_requirement_version, analyzed_at, " +
            "created_at, updated_at, is_deleted";

    public Long insert(GapAnalysisDto analysis) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, analysis);
        }
    }

    public Long insert(Connection conn, GapAnalysisDto analysis) throws SQLException {
        String sql = "INSERT INTO GAP_ANALYSIS (user_id, job_id, match_rate, job_requirement_version, analyzed_at) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, analysis.getUserId());
            pstmt.setLong(2, analysis.getJobId());
            pstmt.setBigDecimal(3, analysis.getMatchRate());
            if (analysis.getJobRequirementVersion() == null) {
                pstmt.setNull(4, java.sql.Types.INTEGER);
            } else {
                pstmt.setInt(4, analysis.getJobRequirementVersion());
            }
            pstmt.setTimestamp(5, toTimestamp(analysis.getAnalyzedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 최신 분석순 — 대시보드는 보통 가장 최근 것을 보여준다.
    // analyzed_at은 DATETIME(초 단위)이라 짧은 시간에 재분석하면 값이 같을 수 있다 — id DESC를
    // 2차 정렬로 둬서 동점일 때도 항상 더 나중에 만들어진(=더 큰 id) 쪽이 먼저 오게 한다.
    public List<GapAnalysisDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM GAP_ANALYSIS WHERE user_id = ? AND is_deleted = FALSE " +
                "ORDER BY analyzed_at DESC, id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<GapAnalysisDto> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    public GapAnalysisDto findById(Long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM GAP_ANALYSIS WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private GapAnalysisDto mapRow(ResultSet rs) throws SQLException {
        GapAnalysisDto analysis = new GapAnalysisDto();
        analysis.setId(rs.getLong("id"));
        analysis.setUserId(rs.getLong("user_id"));
        analysis.setJobId(rs.getLong("job_id"));
        analysis.setMatchRate(rs.getBigDecimal("match_rate"));
        analysis.setJobRequirementVersion(rs.getObject("job_requirement_version", Integer.class));
        analysis.setAnalyzedAt(toLocalDateTime(rs.getTimestamp("analyzed_at")));
        analysis.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        analysis.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        analysis.setDeleted(rs.getBoolean("is_deleted"));
        return analysis;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
