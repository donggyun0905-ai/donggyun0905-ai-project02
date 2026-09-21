package com.specodyssey.dao;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserScoreSummaryDaoTest {

    private static final UserDao userDao = new UserDao();
    private final UserScoreSummaryDao dao = new UserScoreSummaryDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_summary_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            // USER_SCORE_SUMMARY.user_id는 AUTO_INCREMENT가 아니라 USERS.id를 그대로 쓰는 PK/FK라
            // 테스트 본문에서 이미 지워졌더라도 안전하게 한 번 더 정리
            TestFixtures.hardDeleteByColumn(conn, "USER_SCORE_SUMMARY", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_update() throws Exception {
        UserScoreSummaryDto summary = new UserScoreSummaryDto();
        summary.setUserId(userId);
        summary.setTotalScore(0);
        summary.setStreakCount(0);

        try (Connection conn = DBUtil.getConnection()) {
            dao.insert(conn, summary);
        }

        UserScoreSummaryDto found = dao.findByUserId(userId);
        assertNotNull(found);
        assertEquals(0, found.getTotalScore());

        found.setTotalScore(100);
        found.setStreakCount(1);
        try (Connection conn = DBUtil.getConnection()) {
            dao.update(conn, found);
        }
        UserScoreSummaryDto updated = dao.findByUserId(userId);
        assertEquals(100, updated.getTotalScore());
        assertEquals(1, updated.getStreakCount());
    }
}
