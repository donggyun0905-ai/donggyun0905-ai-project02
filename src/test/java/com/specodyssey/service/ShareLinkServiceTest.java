package com.specodyssey.service;

import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareLinkViewLogDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShareLinkService 통합테스트. 관련 요구사항: FR-85 · 86
 */
class ShareLinkServiceTest {

    private final UserDao userDao = new UserDao();
    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final ShareLinkViewLogDao shareLinkViewLogDao = new ShareLinkViewLogDao();
    private final ShareLinkService shareLinkService = new ShareLinkService();

    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("sharelink_svc_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterEach
    void tearDown() throws Exception {
        // delete()가 논리 삭제라 findByUserId(is_deleted=FALSE)로는 지운 링크를 다시 못 찾는다 —
        // 테스트가 만든 행은 활성·비활성·삭제 여부와 무관하게 전부 치워야 USERS를 지울 수 있다.
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement deleteLogs = conn.prepareStatement(
                     "DELETE FROM SHARE_LINK_VIEW_LOG WHERE share_link_id IN " +
                             "(SELECT id FROM SHARE_LINK WHERE user_id = ?)");
             PreparedStatement deleteLinks = conn.prepareStatement("DELETE FROM SHARE_LINK WHERE user_id = ?")) {
            deleteLogs.setLong(1, userId);
            deleteLogs.executeUpdate();
            deleteLinks.setLong(1, userId);
            deleteLinks.executeUpdate();
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void issue_하면_추측_불가능한_토큰과_함께_활성_상태로_생성된다() throws Exception {
        ShareLinkDto link = shareLinkService.issue(userId, "A사 지원", 30, true, true, false);

        assertNotNull(link.getId());
        assertNotNull(link.getToken());
        assertTrue(link.getToken().length() >= 32, "SecureRandom 32바이트를 Base64로 인코딩하면 짧을 수 없다");
        assertTrue(link.isActive());
        assertNotNull(link.getExpiresAt());
        assertTrue(link.isScopeBasic());
        assertTrue(link.isScopeSkills());
        assertFalse(link.isScopeGrowth());
    }

    @Test
    void issue_두_번_호출하면_토큰이_서로_다르다() throws Exception {
        ShareLinkDto a = shareLinkService.issue(userId, "A", null, true, false, false);
        ShareLinkDto b = shareLinkService.issue(userId, "B", null, true, false, false);

        assertNotEquals(a.getToken(), b.getToken());
    }

    @Test
    void expiresInDays가_null이면_만료_없음() throws Exception {
        ShareLinkDto link = shareLinkService.issue(userId, "무제한", null, true, false, false);

        assertNull(link.getExpiresAt());
    }

    @Test
    void listMine은_열람_횟수와_최근_열람_시각을_계산해서_돌려준다() throws Exception {
        ShareLinkDto link = shareLinkService.issue(userId, "A사", 30, true, false, false);
        try (Connection conn = DBUtil.getConnection()) {
            ShareLinkViewLogDto log1 = new ShareLinkViewLogDto();
            log1.setShareLinkId(link.getId());
            log1.setViewedAt(LocalDateTime.now().minusDays(1));
            log1.setViewerIp("127.0.0.1");
            shareLinkViewLogDao.insert(conn, log1);

            ShareLinkViewLogDto log2 = new ShareLinkViewLogDto();
            log2.setShareLinkId(link.getId());
            log2.setViewedAt(LocalDateTime.now());
            log2.setViewerIp("127.0.0.2");
            shareLinkViewLogDao.insert(conn, log2);
        }

        List<ShareLinkService.ShareLinkView> views = shareLinkService.listMine(userId);

        assertEquals(1, views.size());
        assertEquals(2, views.get(0).getViewCount());
        assertNotNull(views.get(0).getLastViewedAtDisplay());
        assertEquals("ACTIVE", views.get(0).getStatus());
    }

    @Test
    void setActive_false로_바꾸면_면접관이_토큰으로_더_이상_조회할_수_없다() throws Exception {
        ShareLinkDto link = shareLinkService.issue(userId, "A사", 30, true, false, false);

        shareLinkService.setActive(userId, link.getId(), false);

        assertNull(shareLinkDao.findByToken(link.getToken()));
    }

    @Test
    void 다른_사용자_id로_setActive를_호출해도_아무_영향이_없다() throws Exception {
        ShareLinkDto link = shareLinkService.issue(userId, "A사", 30, true, false, false);

        shareLinkService.setActive(userId + 999_999L, link.getId(), false);

        assertNotNull(shareLinkDao.findByToken(link.getToken()), "본인 소유가 아니면 조용히 무시돼야 한다");
    }

    @Test
    void delete_하면_목록에서_사라진다() throws Exception {
        ShareLinkDto link = shareLinkService.issue(userId, "A사", 30, true, false, false);

        shareLinkService.delete(userId, link.getId());

        assertTrue(shareLinkDao.findByUserId(userId).isEmpty());
    }
}
