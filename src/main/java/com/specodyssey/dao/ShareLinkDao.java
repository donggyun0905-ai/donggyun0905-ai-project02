package com.specodyssey.dao;

import com.specodyssey.dto.ShareLinkDto;
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
 * SHARE_LINK 테이블 DAO.
 * 관련 요구사항: FR-85 · 86, NFR-9
 * 지원자가 직접 발급·수정(공개 범위 조정)·비활성화(FR-86)하는 본인 소유 리소스라 완전한 CRUD를 갖춘다.
 */
public class ShareLinkDao {

    public Long insert(ShareLinkDto link) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, link);
        }
    }

    public Long insert(Connection conn, ShareLinkDto link) throws SQLException {
        String sql = "INSERT INTO SHARE_LINK " +
                "(user_id, token, is_active, expires_at, scope_basic, scope_skills, scope_growth, label) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, link.getUserId());
            pstmt.setString(2, link.getToken());
            pstmt.setBoolean(3, link.isActive());
            pstmt.setTimestamp(4, toTimestamp(link.getExpiresAt()));
            pstmt.setBoolean(5, link.isScopeBasic());
            pstmt.setBoolean(6, link.isScopeSkills());
            pstmt.setBoolean(7, link.isScopeGrowth());
            pstmt.setString(8, link.getLabel());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<ShareLinkDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM SHARE_LINK WHERE user_id = ? AND is_deleted = FALSE ORDER BY id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ShareLinkDto> links = new ArrayList<>();
                while (rs.next()) {
                    links.add(mapRow(rs));
                }
                return links;
            }
        }
    }

    // 평가 비교(EVALUATION_SESSION_ITEM)가 담아둔 share_link_id로 지원자를 다시 찾을 때 쓴다(FR-82).
    // findByToken과 똑같이 활성·미만료 여부를 함께 확인한다 — 지원자가 나중에 공유를 멈추거나 링크가
    // 만료되면 이미 담아둔 비교표에서도 자연히 빠져야 한다(화면설계 "비교표에서 빠집니다" 문구).
    public ShareLinkDto findById(Long id) throws SQLException {
        String sql = "SELECT * FROM SHARE_LINK WHERE id = ? AND is_active = TRUE " +
                "AND (expires_at IS NULL OR expires_at > NOW()) AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 면접관이 토큰으로 접근할 때 조회 (FR-85)
    // is_active·expires_at도 함께 확인한다 — 안 그러면 지원자가 링크를 비활성화(FR-86)해도 계속 열람 가능해진다.
    public ShareLinkDto findByToken(String token) throws SQLException {
        String sql = "SELECT * FROM SHARE_LINK WHERE token = ? AND is_active = TRUE " +
                "AND (expires_at IS NULL OR expires_at > NOW()) AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, token);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 본인 소유가 아닌 id는 WHERE 조건에서 자연히 걸러진다 (0행 갱신)
    public void update(Connection conn, ShareLinkDto link, Long userId) throws SQLException {
        String sql = "UPDATE SHARE_LINK SET is_active = ?, expires_at = ?, scope_basic = ?, scope_skills = ?, " +
                "scope_growth = ?, label = ? WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, link.isActive());
            pstmt.setTimestamp(2, toTimestamp(link.getExpiresAt()));
            pstmt.setBoolean(3, link.isScopeBasic());
            pstmt.setBoolean(4, link.isScopeSkills());
            pstmt.setBoolean(5, link.isScopeGrowth());
            pstmt.setString(6, link.getLabel());
            pstmt.setLong(7, link.getId());
            pstmt.setLong(8, userId);
            pstmt.executeUpdate();
        }
    }

    // FR-86 "공유 중단"/"다시 공유하기" — 활성 여부만 토글. update()처럼 전체 필드를 다시 받을 필요
    // 없이 이 한 플래그만 바꿀 때 쓴다. 본인 소유가 아니면 WHERE 조건에서 자연히 0행 갱신된다.
    public void updateActive(Connection conn, Long linkId, Long userId, boolean active) throws SQLException {
        String sql = "UPDATE SHARE_LINK SET is_active = ? WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, active);
            pstmt.setLong(2, linkId);
            pstmt.setLong(3, userId);
            pstmt.executeUpdate();
        }
    }

    // FR-86 공유 중단 — 논리 삭제
    public void delete(Connection conn, Long linkId, Long userId) throws SQLException {
        String sql = "UPDATE SHARE_LINK SET is_deleted = TRUE WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, linkId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    private ShareLinkDto mapRow(ResultSet rs) throws SQLException {
        ShareLinkDto link = new ShareLinkDto();
        link.setId(rs.getLong("id"));
        link.setUserId(rs.getLong("user_id"));
        link.setToken(rs.getString("token"));
        link.setActive(rs.getBoolean("is_active"));
        link.setExpiresAt(toLocalDateTime(rs.getTimestamp("expires_at")));
        link.setScopeBasic(rs.getBoolean("scope_basic"));
        link.setScopeSkills(rs.getBoolean("scope_skills"));
        link.setScopeGrowth(rs.getBoolean("scope_growth"));
        link.setLabel(rs.getString("label"));
        link.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        link.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        link.setDeleted(rs.getBoolean("is_deleted"));
        return link;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
