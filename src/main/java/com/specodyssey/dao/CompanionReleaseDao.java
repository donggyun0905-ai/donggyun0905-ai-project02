package com.specodyssey.dao;

import com.specodyssey.dto.CompanionReleaseDto;
import com.specodyssey.util.DBUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * COMPANION_RELEASE · COMPANION_RELEASE_CHUNK DAO — 데스크톱 캐릭터 설치 파일을 8MB씩 나눠 DB에 둔다.
 * 내려줄 때는 조각을 한 번에 하나씩 읽어 바로 흘려보낸다 (47MB를 메모리에 다 올리지 않게).
 */
public class CompanionReleaseDao {

    private static final String COLUMNS = "id, version, notes, file_name, file_size, sha256, created_at";

    public Long insertRelease(Connection conn, CompanionReleaseDto r, Long uploadedBy) throws SQLException {
        String sql = "INSERT INTO COMPANION_RELEASE (version, notes, file_name, file_size, sha256, uploaded_by) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, r.getVersion());
            pstmt.setString(2, r.getNotes());
            pstmt.setString(3, r.getFileName());
            pstmt.setLong(4, r.getFileSize());
            pstmt.setString(5, r.getSha256());
            pstmt.setObject(6, uploadedBy);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void insertChunk(Connection conn, Long releaseId, int seq, byte[] data, int length) throws SQLException {
        String sql = "INSERT INTO COMPANION_RELEASE_CHUNK (release_id, seq, data) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, releaseId);
            pstmt.setInt(2, seq);
            pstmt.setBinaryStream(3, new java.io.ByteArrayInputStream(data, 0, length), length);
            pstmt.executeUpdate();
        }
    }

    /** 크기·확인값을 다 센 뒤에 채운다 (올리기 시작할 때는 모르므로) */
    public void updateSizeAndHash(Connection conn, Long releaseId, long size, String sha256) throws SQLException {
        String sql = "UPDATE COMPANION_RELEASE SET file_size = ?, sha256 = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, size);
            pstmt.setString(2, sha256);
            pstmt.setLong(3, releaseId);
            pstmt.executeUpdate();
        }
    }

    public boolean existsVersion(String version) throws SQLException {
        String sql = "SELECT 1 FROM COMPANION_RELEASE WHERE version = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, version);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** 살아 있는 버전들, 최근 올린 순 */
    public List<CompanionReleaseDto> findActive() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM COMPANION_RELEASE WHERE is_deleted = FALSE ORDER BY id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<CompanionReleaseDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        }
    }

    /** 오래된 버전 정리 — 논리 삭제하고 조각 내용을 비운다 (DB가 버전마다 47MB씩 커지지 않게) */
    public void retire(Connection conn, Long releaseId) throws SQLException {
        try (PreparedStatement p1 = conn.prepareStatement(
                "UPDATE COMPANION_RELEASE_CHUNK SET data = NULL, is_deleted = TRUE WHERE release_id = ?");
             PreparedStatement p2 = conn.prepareStatement(
                     "UPDATE COMPANION_RELEASE SET is_deleted = TRUE WHERE id = ?")) {
            p1.setLong(1, releaseId);
            p1.executeUpdate();
            p2.setLong(1, releaseId);
            p2.executeUpdate();
        }
    }

    /** 조각을 순서대로 이어 out으로 흘려보낸다 */
    public void writeTo(Long releaseId, OutputStream out) throws SQLException, IOException {
        String ids = "SELECT seq FROM COMPANION_RELEASE_CHUNK WHERE release_id = ? AND is_deleted = FALSE ORDER BY seq";
        String one = "SELECT data FROM COMPANION_RELEASE_CHUNK WHERE release_id = ? AND seq = ?";
        try (Connection conn = DBUtil.getConnection()) {
            List<Integer> seqs = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(ids)) {
                pstmt.setLong(1, releaseId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        seqs.add(rs.getInt(1));
                    }
                }
            }
            try (PreparedStatement pstmt = conn.prepareStatement(one)) {
                for (int seq : seqs) {
                    pstmt.setLong(1, releaseId);
                    pstmt.setInt(2, seq);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            byte[] data = rs.getBytes(1);
                            if (data != null) {
                                out.write(data);
                            }
                        }
                    }
                }
            }
        }
    }

    private CompanionReleaseDto mapRow(ResultSet rs) throws SQLException {
        CompanionReleaseDto r = new CompanionReleaseDto();
        r.setId(rs.getLong("id"));
        r.setVersion(rs.getString("version"));
        r.setNotes(rs.getString("notes"));
        r.setFileName(rs.getString("file_name"));
        r.setFileSize(rs.getLong("file_size"));
        r.setSha256(rs.getString("sha256"));
        Timestamp t = rs.getTimestamp("created_at");
        r.setCreatedAt(t == null ? null : t.toLocalDateTime());
        return r;
    }
}
