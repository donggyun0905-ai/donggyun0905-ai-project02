package com.specodyssey.service;

import com.specodyssey.dao.TechArticleDao;
import com.specodyssey.dao.TechArticleReportDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.TechArticleDto;
import com.specodyssey.dto.TechArticleReportDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdminArticleService 통합테스트. 관련 요구사항: db-design.md 4-3(자동 게시 + 사후 관리), "회의에서 정할 것"
 */
class AdminArticleServiceTest {

    private final UserDao userDao = new UserDao();
    private final TechArticleDao articleDao = new TechArticleDao();
    private final TechArticleReportDao reportDao = new TechArticleReportDao();
    private final AdminArticleService service = new AdminArticleService();

    private Long authorId;
    private Long skillId;
    private Long articleId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto author = new UserDto();
        author.setUserType("APPLICANT");
        author.setLoginId("admin_article_test_" + System.nanoTime());
        author.setPasswordHash("dummy_hash");
        author.setDesiredJobStatus("UNSET");
        author.setPrivacyConsentAt(LocalDateTime.now());
        authorId = userDao.insert(author);

        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, "관리자게시판테스트스킬_" + System.nanoTime());

            TechArticleDto article = new TechArticleDto();
            article.setUserId(authorId);
            article.setSkillId(skillId);
            article.setSourceType("ARCHIVE_TIP");
            article.setTitle("테스트 글");
            article.setContent("테스트 본문");
            article.setStatus(TechArticleDao.STATUS_PUBLISHED);
            article.setPublishedAt(LocalDateTime.now());
            articleId = articleDao.insert(conn, article);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "TECH_ARTICLE_REPORT", "article_id", articleId);
            TestFixtures.hardDelete(conn, "TECH_ARTICLE", articleId);
            TestFixtures.hardDelete(conn, "SKILL", skillId);
            TestFixtures.hardDelete(conn, "USERS", authorId);
        }
    }

    @Test
    void 글을_내리면_상태가_HIDDEN이_되고_사유가_저장된다() throws Exception {
        service.hideArticle(articleId, "부적절한 내용");

        TechArticleDto reloaded = articleDao.findByIdForAdmin(articleId);
        assertEquals(TechArticleDao.STATUS_HIDDEN, reloaded.getStatus());
        assertEquals("부적절한 내용", reloaded.getHiddenReason());
        assertNotNull(reloaded.getHiddenAt());
    }

    @Test
    void 사유_없이_내리면_예외를_던진다() {
        assertThrows(IllegalArgumentException.class, () -> service.hideArticle(articleId, "  "));
    }

    @Test
    void 글을_내리면_그_글의_OPEN_신고가_전부_ACTION_TAKEN으로_바뀐다() throws Exception {
        Long reporterId = createReporter();
        Long reportId;
        try (Connection conn = DBUtil.getConnection()) {
            TechArticleReportDto report = new TechArticleReportDto();
            report.setArticleId(articleId);
            report.setReporterUserId(reporterId);
            report.setReasonType("SPAM");
            report.setStatus(TechArticleReportDto.STATUS_OPEN);
            reportId = reportDao.insert(conn, report);
        }
        try {
            service.hideArticle(articleId, "신고 처리");

            List<TechArticleReportDto> open = service.listOpenReports();
            assertTrue(open.stream().noneMatch(r -> r.getId().equals(reportId)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "TECH_ARTICLE_REPORT", reportId);
                TestFixtures.hardDelete(conn, "USERS", reporterId);
            }
        }
    }

    @Test
    void 되살리면_다시_공개되고_내려간_사유는_지워진다() throws Exception {
        service.hideArticle(articleId, "일단 내림");
        service.restoreArticle(articleId);

        TechArticleDto reloaded = articleDao.findByIdForAdmin(articleId);
        assertEquals(TechArticleDao.STATUS_PUBLISHED, reloaded.getStatus());
        assertNull(reloaded.getHiddenReason());
        assertNull(reloaded.getHiddenAt());
    }

    @Test
    void 신고를_기각하면_글은_그대로_공개_상태다() throws Exception {
        Long reporterId = createReporter();
        Long reportId;
        try (Connection conn = DBUtil.getConnection()) {
            TechArticleReportDto report = new TechArticleReportDto();
            report.setArticleId(articleId);
            report.setReporterUserId(reporterId);
            report.setReasonType("OTHER");
            report.setStatus(TechArticleReportDto.STATUS_OPEN);
            reportId = reportDao.insert(conn, report);
        }
        try {
            service.dismissReport(reportId);

            TechArticleDto reloaded = articleDao.findByIdForAdmin(articleId);
            assertEquals(TechArticleDao.STATUS_PUBLISHED, reloaded.getStatus());
            List<TechArticleReportDto> open = service.listOpenReports();
            assertTrue(open.stream().noneMatch(r -> r.getId().equals(reportId)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "TECH_ARTICLE_REPORT", reportId);
                TestFixtures.hardDelete(conn, "USERS", reporterId);
            }
        }
    }

    private Long createReporter() throws Exception {
        UserDto reporter = new UserDto();
        reporter.setUserType("APPLICANT");
        reporter.setLoginId("admin_article_reporter_" + System.nanoTime());
        reporter.setPasswordHash("dummy_hash");
        reporter.setDesiredJobStatus("UNSET");
        reporter.setPrivacyConsentAt(LocalDateTime.now());
        return userDao.insert(reporter);
    }
}
