package com.specodyssey.dao;

import com.specodyssey.dto.ExternalApiCacheDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * EXTERNAL_API_CACHE 테이블 DAO.
 * 관련 요구사항: FR-111 · 112, NFR-1
 * 캐시 히트 여부 판단이 목적이라 조회 + 신규 적재만 지원한다.
 * 복합 UNIQUE(api_type, request_key) 위반(같은 키 재수집) 시 갱신할지 새로 남길지는
 * 캐싱 정책을 만드는 담당자가 결정한다.
 */
public class ExternalApiCacheDao {

    public Long insert(ExternalApiCacheDto cache) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, cache);
        }
    }

    public Long insert(Connection conn, ExternalApiCacheDto cache) throws SQLException {
        String sql = "INSERT INTO EXTERNAL_API_CACHE " +
                "(api_type, request_key, response_body, status, cached_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, cache.getApiType());
            pstmt.setString(2, cache.getRequestKey());
            pstmt.setString(3, cache.getResponseBody());
            pstmt.setString(4, cache.getStatus());
            pstmt.setTimestamp(5, toTimestamp(cache.getCachedAt()));
            pstmt.setTimestamp(6, toTimestamp(cache.getExpiresAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 캐시 히트 조회 — 만료 여부 판단은 호출부에서 expiresAt과 현재 시각을 비교
    public ExternalApiCacheDto findByTypeAndKey(String apiType, String requestKey) throws SQLException {
        String sql = "SELECT * FROM EXTERNAL_API_CACHE " +
                "WHERE api_type = ? AND request_key = ? AND is_deleted = FALSE " +
                "ORDER BY cached_at DESC LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, apiType);
            pstmt.setString(2, requestKey);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private ExternalApiCacheDto mapRow(ResultSet rs) throws SQLException {
        ExternalApiCacheDto cache = new ExternalApiCacheDto();
        cache.setId(rs.getLong("id"));
        cache.setApiType(rs.getString("api_type"));
        cache.setRequestKey(rs.getString("request_key"));
        cache.setResponseBody(rs.getString("response_body"));
        cache.setStatus(rs.getString("status"));
        cache.setCachedAt(toLocalDateTime(rs.getTimestamp("cached_at")));
        cache.setExpiresAt(toLocalDateTime(rs.getTimestamp("expires_at")));
        cache.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        cache.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        cache.setDeleted(rs.getBoolean("is_deleted"));
        return cache;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
