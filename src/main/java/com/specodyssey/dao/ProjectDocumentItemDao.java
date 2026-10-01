package com.specodyssey.dao;

import com.specodyssey.dto.ProjectDocumentItemDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * PROJECT_DOCUMENT_ITEM 테이블 DAO.
 * 관련 요구사항: FR-24 · FR-61~63 · 개발일지 4-4
 * 프로젝트마다 문서 종류(README·스크린샷 등)별로 "제출" 또는 "해당 없음"을 한 줄씩 둔다.
 * 복합 UNIQUE (project_id, doc_type)라서, 같은 종류를 다시 제출하면 새 행이 아니라 그 행을 갱신한다.
 */
public class ProjectDocumentItemDao {

    /**
     * 종류별 한 줄을 만들거나 갱신한다. 이전에 지운(is_deleted) 행이 있으면 되살린다 —
     * 서류 보관함에서 파일을 지우면 이 줄도 논리 삭제되는데, 다시 제출할 때 UNIQUE와 부딪히지 않게 한다.
     */
    public void upsert(Connection conn, ProjectDocumentItemDto item) throws SQLException {
        String sql = "INSERT INTO PROJECT_DOCUMENT_ITEM (project_id, doc_type, status, document_id) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE status = VALUES(status), document_id = VALUES(document_id), " +
                "is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, item.getProjectId());
            pstmt.setString(2, item.getDocType());
            pstmt.setString(3, item.getStatus());
            if (item.getDocumentId() == null) {
                pstmt.setNull(4, Types.BIGINT);
            } else {
                pstmt.setLong(4, item.getDocumentId());
            }
            pstmt.executeUpdate();
        }
    }

    public List<ProjectDocumentItemDto> findByProjectId(Long projectId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return findByProjectId(conn, projectId);
        }
    }

    public List<ProjectDocumentItemDto> findByProjectId(Connection conn, Long projectId) throws SQLException {
        String sql = "SELECT * FROM PROJECT_DOCUMENT_ITEM WHERE project_id = ? AND is_deleted = FALSE ORDER BY id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, projectId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ProjectDocumentItemDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
                return items;
            }
        }
    }

    /**
     * 서류 보관함에서 파일을 지울 때 그 파일을 가리키던 체크리스트 줄을 함께 논리 삭제한다.
     * 이 테이블에는 user_id가 없어서 프로젝트(USER_PROJECTS)를 거쳐 본인 것만 건드린다.
     * @return 정리한 줄 수
     */
    public int softDeleteByDocumentId(Connection conn, Long documentId, Long userId) throws SQLException {
        String sql = "UPDATE PROJECT_DOCUMENT_ITEM i JOIN USER_PROJECTS p ON p.id = i.project_id " +
                "SET i.is_deleted = TRUE WHERE i.document_id = ? AND p.user_id = ? AND i.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, documentId);
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate();
        }
    }

    private ProjectDocumentItemDto mapRow(ResultSet rs) throws SQLException {
        ProjectDocumentItemDto item = new ProjectDocumentItemDto();
        item.setId(rs.getLong("id"));
        item.setProjectId(rs.getLong("project_id"));
        item.setDocType(rs.getString("doc_type"));
        item.setStatus(rs.getString("status"));
        item.setDocumentId(rs.getObject("document_id", Long.class));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        item.setCreatedAt(created == null ? null : created.toLocalDateTime());
        item.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        item.setDeleted(rs.getBoolean("is_deleted"));
        return item;
    }
}
