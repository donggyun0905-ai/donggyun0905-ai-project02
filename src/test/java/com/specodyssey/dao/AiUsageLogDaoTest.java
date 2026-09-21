package com.specodyssey.dao;

import com.specodyssey.dto.AiUsageLogDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiUsageLogDaoTest {

    private static final UserDao userDao = new UserDao();
    private final AiUsageLogDao dao = new AiUsageLogDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_ailog_user_" + System.nanoTime());
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
    void insert_findByUserId_update_delete() throws Exception {
        AiUsageLogDto log = new AiUsageLogDto();
        log.setUserId(userId);
        log.setUsageRecordJson("{\"tool\":\"claude\",\"count\":3}");
        log.setShared(false);

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, log);
        }
        try {
            assertNotNull(id);
            List<AiUsageLogDto> logs = dao.findByUserId(userId);
            assertEquals(1, logs.size());
            assertFalse(logs.get(0).isShared());

            AiUsageLogDto toUpdate = logs.get(0);
            toUpdate.setShared(true);
            try (Connection conn = DBUtil.getConnection()) {
                dao.update(conn, toUpdate, userId);
            }
            assertTrue(dao.findByUserId(userId).get(0).isShared());

            try (Connection conn = DBUtil.getConnection()) {
                dao.delete(conn, id, userId);
            }
            assertTrue(dao.findByUserId(userId).isEmpty());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "AI_USAGE_LOG", id);
            }
        }
    }
}
