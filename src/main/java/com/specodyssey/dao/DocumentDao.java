package com.specodyssey.dao;

import com.specodyssey.dto.DocumentDto;
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
 * DOCUMENTS 테이블 DAO.
 * 관련 요구사항: FR-61~64, NFR-7
 * 파일 자체 교체는 삭제 후 재업로드로 처리하는 게 일반적이라, update는
 * 사용자가 실제로 바꿀 수 있는 표시명·연결 프로젝트(FR-63)만 대상으로 한다.
 */
public class DocumentDao {

    public Long insert(DocumentDto document) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, document);
        }
    }

    public Long insert(Connection conn, DocumentDto document) throws SQLException {
        String sql = "INSERT INTO DOCUMENTS " +
                "(user_id, project_id, original_name, stored_name, file_path, file_size, mime_type, checksum) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, document.getUserId());
            setNullableLong(pstmt, 2, document.getProjectId());
            pstmt.setString(3, document.getOriginalName());
            pstmt.setString(4, document.getStoredName());
            pstmt.setString(5, document.getFilePath());
            pstmt.setLong(6, document.getFileSize());
            pstmt.setString(7, document.getMimeType());
            pstmt.setString(8, document.getChecksum());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<DocumentDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM DOCUMENTS WHERE user_id = ? AND is_deleted = FALSE ORDER BY id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<DocumentDto> documents = new ArrayList<>();
                while (rs.next()) {
                    documents.add(mapRow(rs));
                }
                return documents;
            }
        }
    }

    // FR-63 표시명·연결 프로젝트만 수정 대상 — 파일 자체 교체는 삭제 후 재업로드
    public void update(Connection conn, DocumentDto document, Long userId) throws SQLException {
        String sql = "UPDATE DOCUMENTS SET original_name = ?, project_id = ? " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, document.getOriginalName());
            setNullableLong(pstmt, 2, document.getProjectId());
            pstmt.setLong(3, document.getId());
            pstmt.setLong(4, userId);
            pstmt.executeUpdate();
        }
    }

    public void delete(Connection conn, Long documentId, Long userId) throws SQLException {
        String sql = "UPDATE DOCUMENTS SET is_deleted = TRUE WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, documentId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    private DocumentDto mapRow(ResultSet rs) throws SQLException {
        DocumentDto document = new DocumentDto();
        document.setId(rs.getLong("id"));
        document.setUserId(rs.getLong("user_id"));
        document.setProjectId(rs.getObject("project_id", Long.class));
        document.setOriginalName(rs.getString("original_name"));
        document.setStoredName(rs.getString("stored_name"));
        document.setFilePath(rs.getString("file_path"));
        document.setFileSize(rs.getObject("file_size", Long.class));
        document.setMimeType(rs.getString("mime_type"));
        document.setChecksum(rs.getString("checksum"));
        document.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        document.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        document.setDeleted(rs.getBoolean("is_deleted"));
        return document;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
