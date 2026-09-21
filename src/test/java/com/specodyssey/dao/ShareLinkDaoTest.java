package com.specodyssey.dao;

import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShareLinkDaoTest {

    private static final UserDao userDao = new UserDao();
    private final ShareLinkDao dao = new ShareLinkDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_sharelink_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_findByToken_update_delete() throws Exception {
        String token = "test_token_" + System.nanoTime();
        ShareLinkDto link = new ShareLinkDto();
        link.setUserId(userId);
        link.setToken(token);
        link.setActive(true);
        link.setScopeBasic(true);
        link.setLabel("A회사 지원");

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, link);
        }
        try {
            assertNotNull(id);
            assertEquals(1, dao.findByUserId(userId).size());

            ShareLinkDto found = dao.findByToken(token);
            assertNotNull(found);
            assertEquals("A회사 지원", found.getLabel());

            found.setLabel("B회사 지원");
            found.setScopeSkills(true);
            try (Connection conn = DBUtil.getConnection()) {
                dao.update(conn, found, userId);
            }
            assertEquals("B회사 지원", dao.findByToken(token).getLabel());
            assertTrue(dao.findByToken(token).isScopeSkills());

            // FR-86: 비활성화하면 토큰으로 더 이상 조회되지 않아야 한다
            ShareLinkDto toDeactivate = dao.findByToken(token);
            toDeactivate.setActive(false);
            try (Connection conn = DBUtil.getConnection()) {
                dao.update(conn, toDeactivate, userId);
            }
            assertNull(dao.findByToken(token));

            try (Connection conn = DBUtil.getConnection()) {
                dao.delete(conn, id, userId);
            }
            assertTrue(dao.findByUserId(userId).isEmpty());
            List<ShareLinkDto> remaining = dao.findByUserId(userId);
            assertTrue(remaining.isEmpty());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SHARE_LINK", id);
            }
        }
    }
}
