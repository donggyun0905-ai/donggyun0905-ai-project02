package com.specodyssey.dao;

import com.specodyssey.dto.TechArticleReportDto;
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
 * TECH_ARTICLE_REPORT 테이블 DAO — 관리자 게시판 관리 화면(2026-10-06)에서 쓴다.
 * 사용자가 신고를 올리는 화면은 아직 없다(스펙 아카이브 작업 범위 밖) — 신고가 없어도 관리자는
 * {@link TechArticleDao#findAllPageForAdmin}로 글을 직접 훑어보고 숨길 수 있다.
 */
public class TechArticleReportDao {

    private static final String SELECT_WITH_JOIN =
            "SELECT r.id, r.article_id, r.reporter_user_id, r.reason_type, r.detail, r.status, r.handled_at, " +
            "r.created_at, r.is_deleted, a.title AS article_title, COALESCE(NULLIF(u.name, ''), u.login_id) AS reporter_name " +
            "FROM TECH_ARTICLE_REPORT r " +
            "JOIN TECH_ARTICLE a ON a.id = r.article_id " +
            "JOIN USERS u ON u.id = r.reporter_user_id ";

    public Long insert(Connection conn, TechArticleReportDto report) throws SQLException {
        String sql = "INSERT INTO TECH_ARTICLE_REPORT (article_id, reporter_user_id, reason_type, detail, status) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, report.getArticleId());
            pstmt.setLong(2, report.getReporterUserId());
            pstmt.setString(3, report.getReasonType());
            pstmt.setString(4, report.getDetail());
            pstmt.setString(5, report.getStatus());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    /** 대기(OPEN) 신고 — 오래된 것 먼저(먼저 들어온 민원부터 처리). */
    public List<TechArticleReportDto> findOpen() throws SQLException {
        String sql = SELECT_WITH_JOIN + "WHERE r.status = ? AND r.is_deleted = FALSE ORDER BY r.created_at ASC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TechArticleReportDto.STATUS_OPEN);
            return mapRows(pstmt);
        }
    }

    public int countOpen() throws SQLException {
        String sql = "SELECT COUNT(*) FROM TECH_ARTICLE_REPORT WHERE status = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TechArticleReportDto.STATUS_OPEN);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 신고 하나의 처리 결과를 저장한다(ACTION_TAKEN/DISMISSED). */
    public void updateStatus(Connection conn, Long reportId, String status, LocalDateTime handledAt)
            throws SQLException {
        String sql = "UPDATE TECH_ARTICLE_REPORT SET status = ?, handled_at = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setTimestamp(2, toTimestamp(handledAt));
            pstmt.setLong(3, reportId);
            pstmt.executeUpdate();
        }
    }

    /** 글을 내릴 때 그 글의 다른 OPEN 신고도 한꺼번에 ACTION_TAKEN으로 처리한다(db-design.md 설계 판단). */
    public void markAllOpenActionTaken(Connection conn, Long articleId, LocalDateTime handledAt) throws SQLException {
        String sql = "UPDATE TECH_ARTICLE_REPORT SET status = ?, handled_at = ? WHERE article_id = ? AND status = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TechArticleReportDto.STATUS_ACTION_TAKEN);
            pstmt.setTimestamp(2, toTimestamp(handledAt));
            pstmt.setLong(3, articleId);
            pstmt.setString(4, TechArticleReportDto.STATUS_OPEN);
            pstmt.executeUpdate();
        }
    }

    private List<TechArticleReportDto> mapRows(PreparedStatement pstmt) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            List<TechArticleReportDto> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        }
    }

    private TechArticleReportDto mapRow(ResultSet rs) throws SQLException {
        TechArticleReportDto r = new TechArticleReportDto();
        r.setId(rs.getLong("id"));
        r.setArticleId(rs.getLong("article_id"));
        r.setReporterUserId(rs.getLong("reporter_user_id"));
        r.setReasonType(rs.getString("reason_type"));
        r.setDetail(rs.getString("detail"));
        r.setStatus(rs.getString("status"));
        r.setHandledAt(toLocalDateTime(rs.getTimestamp("handled_at")));
        r.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        r.setDeleted(rs.getBoolean("is_deleted"));
        r.setArticleTitle(rs.getString("article_title"));
        r.setReporterName(rs.getString("reporter_name"));
        return r;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
