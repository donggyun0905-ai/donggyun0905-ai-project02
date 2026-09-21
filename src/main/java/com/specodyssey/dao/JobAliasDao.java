package com.specodyssey.dao;

import com.specodyssey.dto.JobAliasDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JOB_ALIAS 테이블 DAO.
 * 1주차에는 마스터 데이터만 시드로 채워두고 조회 전용으로만 쓴다.
 * On-demand 직무 매칭에 필요한 저장 메서드는 2주차 담당자가 추가한다.
 */
public class JobAliasDao {

    public List<JobAliasDto> findAll() throws SQLException {
        String sql = "SELECT * FROM JOB_ALIAS WHERE is_deleted = FALSE ORDER BY alias_name";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<JobAliasDto> aliases = new ArrayList<>();
            while (rs.next()) {
                aliases.add(mapRow(rs));
            }
            return aliases;
        }
    }

    public JobAliasDto findByAliasName(String aliasName) throws SQLException {
        String sql = "SELECT * FROM JOB_ALIAS WHERE alias_name = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, aliasName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private JobAliasDto mapRow(ResultSet rs) throws SQLException {
        JobAliasDto alias = new JobAliasDto();
        alias.setId(rs.getLong("id"));
        alias.setJobId(rs.getLong("job_id"));
        alias.setAliasName(rs.getString("alias_name"));
        alias.setMatchType(rs.getString("match_type"));
        alias.setSimilarityScore(rs.getBigDecimal("similarity_score"));
        alias.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        alias.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        alias.setDeleted(rs.getBoolean("is_deleted"));
        return alias;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
