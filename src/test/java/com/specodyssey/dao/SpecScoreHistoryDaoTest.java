package com.specodyssey.dao;

import com.specodyssey.dto.SpecScoreHistoryDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpecScoreHistoryDaoTest {

    private static final UserDao userDao = new UserDao();
    private final SpecScoreHistoryDao dao = new SpecScoreHistoryDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_history_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setMajor("컴퓨터공학");
        user.setGrade("3학년");
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
    void insert_findByUserId_findByMajorAndGrade() throws Exception {
        SpecScoreHistoryDto history = new SpecScoreHistoryDto();
        history.setUserId(userId);
        history.setSnapshotDate(LocalDate.now());
        history.setCompletenessScore(new BigDecimal("40.00"));
        history.setMajor("컴퓨터공학");
        history.setGrade("3학년");
        history.setSeed(false);

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, history);
        }
        try {
            assertNotNull(id);
            assertEquals(1, dao.findByUserId(userId).size());

            List<SpecScoreHistoryDto> peers = dao.findByMajorAndGrade("컴퓨터공학", "3학년");
            assertTrue(peers.stream().anyMatch(h -> h.getId().equals(id)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SPEC_SCORE_HISTORY", id);
            }
        }
    }
}
