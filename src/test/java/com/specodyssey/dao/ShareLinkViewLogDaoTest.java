package com.specodyssey.dao;

import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareLinkViewLogDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShareLinkViewLogDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final ShareLinkViewLogDao dao = new ShareLinkViewLogDao();

    private static Long userId;
    private static Long shareLinkId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_viewlog_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        ShareLinkDto link = new ShareLinkDto();
        link.setUserId(userId);
        link.setToken("test_token_" + System.nanoTime());
        link.setActive(true);
        link.setScopeBasic(true);
        try (Connection conn = DBUtil.getConnection()) {
            shareLinkId = shareLinkDao.insert(conn, link);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "SHARE_LINK", shareLinkId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByShareLinkId() throws Exception {
        ShareLinkViewLogDto log = new ShareLinkViewLogDto();
        log.setShareLinkId(shareLinkId);
        log.setViewedAt(LocalDateTime.now());
        log.setViewerIp("127.0.0.1");

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, log);
        }
        try {
            assertNotNull(id);
            List<ShareLinkViewLogDto> logs = dao.findByShareLinkId(shareLinkId);
            assertEquals(1, logs.size());
            assertEquals("127.0.0.1", logs.get(0).getViewerIp());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SHARE_LINK_VIEW_LOG", id);
            }
        }
    }
}
