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

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, user_id, project_id, roadmap_step_id, original_name, stored_name, " +
            "file_path, file_size, mime_type, checksum, (file_data IS NOT NULL) AS stored_in_db, " +
            "created_at, updated_at, is_deleted";

    public Long insert(DocumentDto document) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, document);
        }
    }

    public Long insert(Connection conn, DocumentDto document) throws SQLException {
        String sql = "INSERT INTO DOCUMENTS " +
                "(user_id, project_id, roadmap_step_id, original_name, stored_name, file_path, file_size, " +
                " mime_type, checksum, file_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, document.getUserId());
            setNullableLong(pstmt, 2, document.getProjectId());
            setNullableLong(pstmt, 3, document.getRoadmapStepId());
            pstmt.setString(4, document.getOriginalName());
            pstmt.setString(5, document.getStoredName());
            pstmt.setString(6, document.getFilePath());
            pstmt.setLong(7, document.getFileSize());
            pstmt.setString(8, document.getMimeType());
            pstmt.setString(9, document.getChecksum());
            if (document.getFileData() == null) {
                pstmt.setNull(10, Types.LONGVARBINARY);
            } else {
                pstmt.setBytes(10, document.getFileData());
            }
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 다운로드 서블릿에서 소유자 확인 후 스트리밍할 때 사용 — id만으로 조회하고,
    // 본인 소유인지는 호출부(서블릿)가 userId와 비교해서 판단한다.
    public DocumentDto findById(Long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM DOCUMENTS WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 면접관 공유 화면에서 CERT 단계 증빙 서류를 역으로 찾을 때 사용 (ShareViewService).
    // 한 단계에 서류를 여러 번 올릴 일은 없지만, 재제출로 여러 건이 쌓였을 수 있어 최신 1건만 쓴다.
    public DocumentDto findByRoadmapStepId(Long roadmapStepId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM DOCUMENTS WHERE roadmap_step_id = ? AND is_deleted = FALSE ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, roadmapStepId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 최근 서류 몇 개만 — 화면 위젯처럼 전체가 필요 없는 곳에서 서류가 많은 사용자도 가볍게 읽도록 DB에서 개수를 자른다
    public List<DocumentDto> findRecentByUserId(Long userId, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM DOCUMENTS WHERE user_id = ? AND is_deleted = FALSE ORDER BY id DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, Math.max(1, limit));
            try (ResultSet rs = pstmt.executeQuery()) {
                List<DocumentDto> documents = new ArrayList<>();
                while (rs.next()) {
                    documents.add(mapRow(rs));
                }
                return documents;
            }
        }
    }

    public List<DocumentDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM DOCUMENTS WHERE user_id = ? AND is_deleted = FALSE ORDER BY id DESC";
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

    // 서류 보관함에서 연결 프로젝트만 바꿀 때 — 표시명은 건드리지 않는다. projectId가 null이면 연결을 푼다.
    // 본인 소유가 아니면 0행 갱신이라 false를 돌려준다.
    public boolean updateProject(Connection conn, Long documentId, Long userId, Long projectId) throws SQLException {
        String sql = "UPDATE DOCUMENTS SET project_id = ? WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setNullableLong(pstmt, 1, projectId);
            pstmt.setLong(2, documentId);
            pstmt.setLong(3, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    // 행은 논리 삭제로 남기되 파일 내용은 비운다 — 디스크에 두던 시절 삭제할 때 파일을 지우던 것과 같은 효과(개인정보)
    public void delete(Connection conn, Long documentId, Long userId) throws SQLException {
        String sql = "UPDATE DOCUMENTS SET is_deleted = TRUE, file_data = NULL WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, documentId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- 파일 내용 (DOCUMENTS.file_data)

    /**
     * 파일 내용을 그대로 흘려 보낸다(통째로 메모리에 올리지 않는다). 소유자·공개 범위 확인은 호출부가 끝낸 뒤 부른다.
     * @return 내용이 DB에 없으면 false (예전 디스크 서류)
     */
    public boolean writeFileData(Long id, java.io.OutputStream out) throws SQLException, java.io.IOException {
        String sql = "SELECT file_data FROM DOCUMENTS WHERE id = ? AND file_data IS NOT NULL";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                try (java.io.InputStream in = rs.getBinaryStream(1)) {
                    in.transferTo(out);
                }
                return true;
            }
        }
    }

    /** 작은 파일(연습장 노트 등)을 통째로 읽는다. 내용이 DB에 없으면 null */
    public byte[] readFileData(Long id) throws SQLException {
        String sql = "SELECT file_data FROM DOCUMENTS WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    /** 예전(디스크) 서류 중 아직 DB로 옮기지 않은 것 — id와 경로만. DocumentBlobBackfill 전용 */
    public java.util.Map<Long, String> findDiskOnlyPaths() throws SQLException {
        String sql = "SELECT id, file_path FROM DOCUMENTS WHERE file_data IS NULL AND file_path IS NOT NULL " +
                "AND file_path <> '' AND is_deleted = FALSE";
        java.util.Map<Long, String> paths = new java.util.LinkedHashMap<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                paths.put(rs.getLong(1), rs.getString(2));
            }
        }
        return paths;
    }

    /** 예전 서류의 내용을 DB에 채운다. 이미 채워져 있으면 덮어쓰지 않는다(다른 PC가 먼저 옮긴 경우). */
    public boolean fillFileData(Long id, byte[] data) throws SQLException {
        String sql = "UPDATE DOCUMENTS SET file_data = ? WHERE id = ? AND file_data IS NULL";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBytes(1, data);
            pstmt.setLong(2, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    /** 예전에 빈 내용으로 만든 [TEST] 통과 서류에 안내 문구를 채운다. 채운 행 수를 돌려준다. */
    public int fillEmptyTestShortcutDocuments() throws SQLException {
        String sql = "UPDATE DOCUMENTS SET file_data = ?, file_size = ? WHERE checksum = ? AND file_data IS NULL " +
                "AND (file_path IS NULL OR file_path = '') AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBytes(1, DocumentDto.TEST_SHORTCUT_CONTENT);
            pstmt.setLong(2, DocumentDto.TEST_SHORTCUT_CONTENT.length);
            pstmt.setString(3, DocumentDto.TEST_SHORTCUT_CHECKSUM);
            return pstmt.executeUpdate();
        }
    }

    private DocumentDto mapRow(ResultSet rs) throws SQLException {
        DocumentDto document = new DocumentDto();
        document.setId(rs.getLong("id"));
        document.setUserId(rs.getLong("user_id"));
        document.setProjectId(rs.getObject("project_id", Long.class));
        document.setRoadmapStepId(rs.getObject("roadmap_step_id", Long.class));
        document.setOriginalName(rs.getString("original_name"));
        document.setStoredName(rs.getString("stored_name"));
        document.setFilePath(rs.getString("file_path"));
        document.setFileSize(rs.getObject("file_size", Long.class));
        document.setMimeType(rs.getString("mime_type"));
        document.setChecksum(rs.getString("checksum"));
        document.setStoredInDb(rs.getBoolean("stored_in_db"));
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
