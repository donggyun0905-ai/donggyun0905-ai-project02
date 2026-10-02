package com.specodyssey.dao;

import com.specodyssey.dto.TechArticleAttachmentDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** TECH_ARTICLE_ATTACHMENT 테이블 DAO — 글 첨부(업로드 이미지 · 이미지 링크 · 유튜브). */
public class TechArticleAttachmentDao {

    public void insert(Connection conn, TechArticleAttachmentDto a) throws SQLException {
        String sql = "INSERT INTO TECH_ARTICLE_ATTACHMENT (article_id, attachment_type, sort_order, url, embed_key, " +
                "original_name, stored_name, file_path, file_size, mime_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, a.getArticleId());
            pstmt.setString(2, a.getAttachmentType());
            pstmt.setInt(3, a.getSortOrder());
            pstmt.setString(4, a.getUrl());
            pstmt.setString(5, a.getEmbedKey());
            pstmt.setString(6, a.getOriginalName());
            pstmt.setString(7, a.getStoredName());
            pstmt.setString(8, a.getFilePath());
            if (a.getFileSize() == null) {
                pstmt.setNull(9, java.sql.Types.BIGINT);
            } else {
                pstmt.setLong(9, a.getFileSize());
            }
            pstmt.setString(10, a.getMimeType());
            pstmt.executeUpdate();
        }
    }

    /** 글의 첨부, 표시 순서대로 */
    public List<TechArticleAttachmentDto> findByArticleId(Long articleId) throws SQLException {
        String sql = "SELECT * FROM TECH_ARTICLE_ATTACHMENT WHERE article_id = ? AND is_deleted = FALSE ORDER BY sort_order";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, articleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<TechArticleAttachmentDto> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    /**
     * 이미지 보여주기용 — 공개된 스펙 아카이브 글에 달린 업로드 이미지만 찾는다.
     * 지운 글·내린 글의 이미지는 주소를 알아도 받을 수 없다. 없으면 null.
     */
    public TechArticleAttachmentDto findVisibleUploadedImage(Long attachmentId) throws SQLException {
        String sql = "SELECT t.* FROM TECH_ARTICLE_ATTACHMENT t JOIN TECH_ARTICLE a ON a.id = t.article_id " +
                "WHERE t.id = ? AND t.is_deleted = FALSE AND t.attachment_type = 'IMAGE_UPLOAD' " +
                "AND a.source_type = '" + TechArticleDao.SOURCE_ARCHIVE_TIP + "' " +
                "AND a.status = '" + TechArticleDao.STATUS_PUBLISHED + "' AND a.is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, attachmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private TechArticleAttachmentDto mapRow(ResultSet rs) throws SQLException {
        TechArticleAttachmentDto a = new TechArticleAttachmentDto();
        a.setId(rs.getLong("id"));
        a.setArticleId(rs.getLong("article_id"));
        a.setAttachmentType(rs.getString("attachment_type"));
        a.setSortOrder(rs.getInt("sort_order"));
        a.setUrl(rs.getString("url"));
        a.setEmbedKey(rs.getString("embed_key"));
        a.setOriginalName(rs.getString("original_name"));
        a.setStoredName(rs.getString("stored_name"));
        a.setFilePath(rs.getString("file_path"));
        long size = rs.getLong("file_size");
        a.setFileSize(rs.wasNull() ? null : size);
        a.setMimeType(rs.getString("mime_type"));
        a.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        a.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        a.setDeleted(rs.getBoolean("is_deleted"));
        return a;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
