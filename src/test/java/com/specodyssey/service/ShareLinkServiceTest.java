package com.specodyssey.service;

import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareLinkItemDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ShareLinkService 통합테스트. 실제 DB에 링크를 만들고 끝나면 지운다.
 */
class ShareLinkServiceTest {

    private static final UserDao userDao = new UserDao();
    private final ShareLinkService service = new ShareLinkService();
    private final ShareLinkDao dao = new ShareLinkDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_sharesvc_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            // 공유 링크 열람은 알림을 남긴다 — NOTIFICATION이 USERS를 RESTRICT로 잡는다
            TestFixtures.hardDeleteByColumn(conn, "NOTIFICATION", "user_id", userId);
            // SpecScoreScheduler는 웹앱이 뜨는 순간 전체 사용자에게 스냅샷을 남긴다 — 누가 같은 공유 DB로
            // 서버를 띄워 두면 테스트가 방금 만든 사용자 몫까지 생긴다. USERS 바로 앞에서 지운다.
            TestFixtures.hardDeleteByColumn(conn, "SPEC_SCORE_HISTORY", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 링크를_만들면_토큰으로_조회되고_입력한_범위와_만료가_저장된다() throws Exception {
        ShareLinkDto created = service.createLink(userId, "  A사 백엔드 지원  ", 30, true, true, false);

        ShareLinkDto found = dao.findByToken(created.getToken());
        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals("A사 백엔드 지원", found.getLabel());
        assertTrue(found.isActive());
        assertTrue(found.isScopeBasic());
        assertTrue(found.isScopeSkills());
        assertFalse(found.isScopeGrowth());
        assertTrue(found.getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));
        assertTrue(found.getExpiresAt().isBefore(LocalDateTime.now().plusDays(31)));
    }

    @Test
    void 만료_없음과_빈_메모는_null로_저장된다() throws Exception {
        ShareLinkDto created = service.createLink(userId, "   ", null, true, false, false);

        ShareLinkDto found = dao.findByToken(created.getToken());
        assertNull(found.getExpiresAt());
        assertNull(found.getLabel());
    }

    @Test
    void 토큰은_URL에_안전한_43자이고_매번_다르다() {
        String first = ShareLinkService.generateToken();
        String second = ShareLinkService.generateToken();

        assertEquals(43, first.length());
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
        assertNotEquals(first, second);
    }

    @Test
    void 목록은_상태와_공개_범위와_열람_횟수를_보여주고_중단_재개_삭제가_반영된다() throws Exception {
        ShareLinkDto created = service.createLink(userId, "목록 확인용", 30, true, true, false);
        new ShareViewService().loadView(created.getToken(), "127.0.0.1", null);
        new ShareViewService().loadView(created.getToken(), "127.0.0.1", null);
        try {
            ShareLinkItemDto item = findItem(created.getId());
            assertEquals("ACTIVE", item.getStatus());
            assertEquals("기본 이력, 보유 기술 스택", item.getScopeText());
            assertEquals(2, item.getViewCount());
            assertEquals(LocalDate.now().toString(), item.getLastViewedDate());
            assertEquals(LocalDate.now().plusDays(30).toString(), item.getExpiresDate());

            service.setActive(userId, created.getId(), false);
            assertEquals("STOPPED", findItem(created.getId()).getStatus());
            assertNull(dao.findByToken(created.getToken()));

            service.setActive(userId, created.getId(), true);
            assertEquals("ACTIVE", findItem(created.getId()).getStatus());
            assertNotNull(dao.findByToken(created.getToken()));

            service.deleteLink(userId, created.getId());
            assertNull(findItem(created.getId()));
            assertNull(dao.findByToken(created.getToken()));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK_VIEW_LOG", "share_link_id", created.getId());
            }
        }
    }

    @Test
    void 다른_사용자의_링크는_중단하거나_삭제할_수_없다() throws Exception {
        ShareLinkDto created = service.createLink(userId, "남의 링크", 30, true, false, false);
        long otherUserId = userId + 1_000_000L;

        service.setActive(otherUserId, created.getId(), false);
        service.deleteLink(otherUserId, created.getId());

        assertEquals("ACTIVE", findItem(created.getId()).getStatus());
    }

    @Test
    void 만료가_지난_링크는_활성이어도_만료됨으로_본다() {
        ShareLinkDto link = new ShareLinkDto();
        link.setActive(true);
        LocalDateTime now = LocalDateTime.now();

        link.setExpiresAt(now.minusSeconds(1));
        assertEquals("EXPIRED", ShareLinkService.statusOf(link, now));
        link.setExpiresAt(now.plusDays(1));
        assertEquals("ACTIVE", ShareLinkService.statusOf(link, now));
        link.setExpiresAt(null);
        assertEquals("ACTIVE", ShareLinkService.statusOf(link, now));
        link.setActive(false);
        assertEquals("STOPPED", ShareLinkService.statusOf(link, now));
    }

    private ShareLinkItemDto findItem(Long linkId) throws Exception {
        for (ShareLinkItemDto item : service.listLinks(userId)) {
            if (item.getId().equals(linkId)) {
                return item;
            }
        }
        return null;
    }

    @Test
    void 잘못된_입력은_저장하지_않고_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createLink(userId, "가".repeat(51), 30, true, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> service.createLink(userId, "메모", 365, true, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> service.createLink(userId, "메모", 30, false, false, false));
    }
}
