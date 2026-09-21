package com.specodyssey.dao;

import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.dto.EvaluationSessionItemDto;
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

class EvaluationSessionItemDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private static final EvaluationSessionDao sessionDao = new EvaluationSessionDao();
    private final EvaluationSessionItemDao dao = new EvaluationSessionItemDao();

    private static Long userId;
    private static Long shareLinkId;
    private static Long sessionId;
    private static String sessionToken;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_evalitem_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        ShareLinkDto link = new ShareLinkDto();
        link.setUserId(userId);
        link.setToken("test_token_evalitem_" + System.nanoTime());
        link.setActive(true);
        link.setScopeBasic(true);

        sessionToken = "test_session_evalitem_" + System.nanoTime();
        EvaluationSessionDto session = new EvaluationSessionDto();
        session.setSessionToken(sessionToken);
        session.setCompanyName("테스트 회사");

        try (Connection conn = DBUtil.getConnection()) {
            shareLinkId = shareLinkDao.insert(conn, link);
            sessionId = sessionDao.insert(conn, session);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "EVALUATION_SESSION", sessionId);
            TestFixtures.hardDelete(conn, "SHARE_LINK", shareLinkId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findBySessionId_delete() throws Exception {
        EvaluationSessionItemDto item = new EvaluationSessionItemDto();
        item.setSessionId(sessionId);
        item.setShareLinkId(shareLinkId);
        item.setAddedAt(LocalDateTime.now());

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, item);
        }
        try {
            assertNotNull(id);
            List<EvaluationSessionItemDto> items = dao.findBySessionId(sessionId);
            assertEquals(1, items.size());

            try (Connection conn = DBUtil.getConnection()) {
                dao.delete(conn, id, sessionToken);
            }
            assertTrue(dao.findBySessionId(sessionId).isEmpty());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION_ITEM", id);
            }
        }
    }
}
