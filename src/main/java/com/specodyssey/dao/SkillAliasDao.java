package com.specodyssey.dao;

import com.specodyssey.dto.SkillAliasDto;
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
 * SKILL_ALIAS 테이블 DAO (2026-09-30 신설 — "시맨틱 매칭, 이름 일치라도" 팀 결정).
 * 사용자가 표준 명칭(SKILL.skill_name) 대신 흔히 쓰는 한글 표기·줄임말을 미리 등록해둔 사전.
 * JOB_ALIAS와 같은 패턴 — 조회 전용이며, 정확 일치가 실패했을 때 FuzzyNameMatcher가 참고한다.
 */
public class SkillAliasDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, skill_id, alias_name, match_type, similarity_score, created_at, " +
            "updated_at, is_deleted";

    public List<SkillAliasDto> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SKILL_ALIAS WHERE is_deleted = FALSE ORDER BY alias_name";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<SkillAliasDto> aliases = new ArrayList<>();
            while (rs.next()) {
                aliases.add(mapRow(rs));
            }
            return aliases;
        }
    }

    public SkillAliasDto findByAliasName(String aliasName) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SKILL_ALIAS WHERE alias_name = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, aliasName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private SkillAliasDto mapRow(ResultSet rs) throws SQLException {
        SkillAliasDto alias = new SkillAliasDto();
        alias.setId(rs.getLong("id"));
        alias.setSkillId(rs.getLong("skill_id"));
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
