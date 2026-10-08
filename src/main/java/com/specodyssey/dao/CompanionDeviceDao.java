package com.specodyssey.dao;

import com.specodyssey.dto.CompanionDeviceDto;
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
 * COMPANION_DEVICE DAO — 데스크톱 캐릭터 연결. 코드·토큰은 서비스가 해시로 바꿔서 넘긴다 (원문은 여기까지 오지 않는다).
 */
public class CompanionDeviceDao {

    private static final String COLUMNS = "id, user_id, device_name, connected_at, last_used_at, revoked_at, "
            + "signed_out_at, token_hash";

    /** 일회용 코드 발급 — 아직 토큰이 없는 행을 하나 만든다 */
    public Long insertCode(Long userId, String codeHash, LocalDateTime expiresAt) throws SQLException {
        String sql = "INSERT INTO COMPANION_DEVICE (user_id, connect_code_hash, code_expires_at) VALUES (?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, codeHash);
            pstmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** 이 사용자의 만료된(토큰으로 안 바꾼) 코드 행을 정리한다 — 논리 삭제 */
    public int deleteExpiredCodes(Long userId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE COMPANION_DEVICE SET is_deleted = TRUE, connect_code_hash = NULL "
                + "WHERE user_id = ? AND token_hash IS NULL AND code_expires_at < ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setTimestamp(2, Timestamp.valueOf(now));
            return pstmt.executeUpdate();
        }
    }

    /**
     * 코드 → 토큰 교환. 만료 전이고 아직 안 쓴 코드일 때만 한 번 성공한다 (UPDATE 한 문장이라 두 번 동시에 와도 하나만 된다).
     * @return 성공하면 그 행의 사용자 id, 아니면 null
     */
    public Long exchangeCode(String codeHash, String tokenHash, String deviceName, LocalDateTime now) throws SQLException {
        String update = "UPDATE COMPANION_DEVICE SET token_hash = ?, connect_code_hash = NULL, device_name = ?, "
                + "connected_at = ?, last_used_at = ? "
                + "WHERE connect_code_hash = ? AND token_hash IS NULL AND code_expires_at >= ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(update)) {
                pstmt.setString(1, tokenHash);
                pstmt.setString(2, deviceName);
                pstmt.setTimestamp(3, Timestamp.valueOf(now));
                pstmt.setTimestamp(4, Timestamp.valueOf(now));
                pstmt.setString(5, codeHash);
                pstmt.setTimestamp(6, Timestamp.valueOf(now));
                if (pstmt.executeUpdate() != 1) {
                    return null;
                }
            }
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT user_id FROM COMPANION_DEVICE WHERE token_hash = ?")) {
                pstmt.setString(1, tokenHash);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : null;
                }
            }
        }
    }

    /** 토큰으로 연결을 찾는다 — 해제·삭제된 것은 없다고 본다 */
    public CompanionDeviceDto findActiveByTokenHash(String tokenHash) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM COMPANION_DEVICE "
                + "WHERE token_hash = ? AND revoked_at IS NULL AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tokenHash);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public void touch(Long id, LocalDateTime now) throws SQLException {
        String sql = "UPDATE COMPANION_DEVICE SET last_used_at = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(now));
            pstmt.setLong(2, id);
            pstmt.executeUpdate();
        }
    }

    /** 본인 연결만 해제한다. 해제했으면 true */
    public boolean revoke(Long id, Long userId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE COMPANION_DEVICE SET revoked_at = ? "
                + "WHERE id = ? AND user_id = ? AND revoked_at IS NULL AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(now));
            pstmt.setLong(2, id);
            pstmt.setLong(3, userId);
            return pstmt.executeUpdate() == 1;
        }
    }

    /** id로 한 행 (논리 삭제된 것은 없다고 본다). 해제 여부는 호출하는 쪽이 revokedAt으로 판단한다 */
    public CompanionDeviceDto findById(Long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM COMPANION_DEVICE WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * 같은 PC가 다시 연결할 때 예전 연결을 끊는다 — 캐릭터가 들고 있던 예전 토큰으로 찾으므로 계정이 달라도 끊긴다
     * (한 PC = 한 연결). 끊었으면 true.
     */
    public boolean revokeByTokenHash(String tokenHash, LocalDateTime now) throws SQLException {
        String sql = "UPDATE COMPANION_DEVICE SET revoked_at = ? "
                + "WHERE token_hash = ? AND revoked_at IS NULL AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(now));
            pstmt.setString(2, tokenHash);
            return pstmt.executeUpdate() == 1;
        }
    }

    /** 이 PC의 캐릭터를 다른 계정으로 옮긴다 (그 PC 브라우저에서 다른 계정으로 로그인) — 쉬는 표시도 지운다 */
    public boolean switchUser(Long id, Long userId) throws SQLException {
        String sql = "UPDATE COMPANION_DEVICE SET user_id = ?, signed_out_at = NULL "
                + "WHERE id = ? AND token_hash IS NOT NULL AND revoked_at IS NULL AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, id);
            return pstmt.executeUpdate() == 1;
        }
    }

    /** 그 PC 브라우저에서 로그아웃 — 연결은 두고 쉬는 상태로 */
    public boolean signOut(Long id, LocalDateTime now) throws SQLException {
        String sql = "UPDATE COMPANION_DEVICE SET signed_out_at = ? "
                + "WHERE id = ? AND token_hash IS NOT NULL AND revoked_at IS NULL AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(now));
            pstmt.setLong(2, id);
            return pstmt.executeUpdate() == 1;
        }
    }

    /** 연결된 PC 목록 (토큰으로 바꿨고 해제 안 한 것), 최근 사용 순 */
    public List<CompanionDeviceDto> findConnectedByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM COMPANION_DEVICE "
                + "WHERE user_id = ? AND token_hash IS NOT NULL AND revoked_at IS NULL AND is_deleted = FALSE "
                + "ORDER BY last_used_at DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<CompanionDeviceDto> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    private CompanionDeviceDto mapRow(ResultSet rs) throws SQLException {
        CompanionDeviceDto d = new CompanionDeviceDto();
        d.setId(rs.getLong("id"));
        d.setUserId(rs.getLong("user_id"));
        d.setDeviceName(rs.getString("device_name"));
        d.setConnectedAt(toLocal(rs.getTimestamp("connected_at")));
        d.setLastUsedAt(toLocal(rs.getTimestamp("last_used_at")));
        d.setRevokedAt(toLocal(rs.getTimestamp("revoked_at")));
        d.setSignedOutAt(toLocal(rs.getTimestamp("signed_out_at")));
        d.setTokenHash(rs.getString("token_hash"));
        return d;
    }

    private static LocalDateTime toLocal(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
