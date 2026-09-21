package com.specodyssey.dao;

import com.specodyssey.dto.DdayAlertDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DdayAlertDaoTest {

    private static final UserDao userDao = new UserDao();
    private final DdayAlertDao dao = new DdayAlertDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_dday_user_" + System.nanoTime());
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
        DdayAlertDto alert = new DdayAlertDto();
        alert.setUserId(userId);
        alert.setTitle("A회사 서류 마감");
        alert.setTargetDate(LocalDate.now().plusDays(7));
        alert.setAlertType("CUSTOM");

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, alert);
        }
        try {
            assertNotNull(id);
            List<DdayAlertDto> alerts = dao.findByUserId(userId);
            assertEquals(1, alerts.size());

            DdayAlertDto toUpdate = alerts.get(0);
            toUpdate.setTitle("A회사 서류 마감(수정)");
            try (Connection conn = DBUtil.getConnection()) {
                dao.update(conn, toUpdate, userId);
            }
            assertEquals("A회사 서류 마감(수정)", dao.findByUserId(userId).get(0).getTitle());

            try (Connection conn = DBUtil.getConnection()) {
                dao.delete(conn, id, userId);
            }
            assertTrue(dao.findByUserId(userId).isEmpty());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "DDAY_ALERT", id);
            }
        }
    }
}
