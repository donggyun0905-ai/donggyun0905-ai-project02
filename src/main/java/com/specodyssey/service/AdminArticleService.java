package com.specodyssey.service;

import com.specodyssey.dao.TechArticleDao;
import com.specodyssey.dao.TechArticleReportDao;
import com.specodyssey.dto.TechArticleDto;
import com.specodyssey.dto.TechArticleReportDto;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 게시판 관리(2026-10-06) — 자동 게시 + 사후 관리 방식(db-design.md 4-3)의 "사후 관리" 쪽.
 * 대기 신고를 보고 글을 내리거나(+신고 일괄 처리) 신고를 기각하고, 내려간 글을 다시 올린다.
 */
public class AdminArticleService {

    private static final int PAGE_SIZE = 30;

    private final TechArticleDao articleDao = new TechArticleDao();
    private final TechArticleReportDao reportDao = new TechArticleReportDao();

    public List<TechArticleReportDto> listOpenReports() throws SQLException {
        return reportDao.findOpen();
    }

    public List<TechArticleDto> listArticles(String statusFilter, int page) throws SQLException {
        return articleDao.findAllPageForAdmin(statusFilter, (page - 1) * PAGE_SIZE, PAGE_SIZE);
    }

    public int countArticles(String statusFilter) throws SQLException {
        return articleDao.countAllForAdmin(statusFilter);
    }

    // 글을 내리고(status=HIDDEN, 사유 기록), 그 글에 걸린 다른 OPEN 신고도 전부 ACTION_TAKEN으로 묶는다.
    public void hideArticle(Long articleId, String reason) throws SQLException {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("내리는 사유를 입력해주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        TransactionUtil.runInTransaction(conn -> {
            articleDao.updateStatus(conn, articleId, TechArticleDao.STATUS_HIDDEN, reason, now);
            reportDao.markAllOpenActionTaken(conn, articleId, now);
            return null;
        });
    }

    // 관리자가 잘못 내렸거나 작성자 소명 후 되살릴 때 — hidden_reason/at은 비운다.
    public void restoreArticle(Long articleId) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            articleDao.updateStatus(conn, articleId, TechArticleDao.STATUS_PUBLISHED, null, null);
            return null;
        });
    }

    // 신고 하나만 "문제 없음"으로 기각 — 글은 그대로 둔다.
    public void dismissReport(Long reportId) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            reportDao.updateStatus(conn, reportId, TechArticleReportDto.STATUS_DISMISSED, LocalDateTime.now());
            return null;
        });
    }
}
