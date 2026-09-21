package com.specodyssey.dao;

import com.specodyssey.dto.ScoreLogDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoreLogDaoTest {

    private static final UserDao userDao = new UserDao();
    private final ScoreLogDao dao = new ScoreLogDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_score_user_" + System.nanoTime());
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
    void insert_findByUserId() throws Exception {
        ScoreLogDto log = new ScoreLogDto();
        log.setUserId(userId);
        log.setSignalType("PROBLEM");
        log.setRefId(1L);
        log.setPoints(5);
        log.setEarnedAt(LocalDateTime.now());

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, log);
        }
        try {
            assertNotNull(id);
            List<ScoreLogDto> logs = dao.findByUserId(userId);
            assertEquals(1, logs.size());
            assertEquals(5, logs.get(0).getPoints());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SCORE_LOG", id);
            }
        }
    }
}
