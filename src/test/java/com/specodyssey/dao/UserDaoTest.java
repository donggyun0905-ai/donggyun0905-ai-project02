package com.specodyssey.dao;

import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserDaoTest {

    private final UserDao userDao = new UserDao();

    @Test
    void insert_findByLoginId_updateProfile_softDelete() throws Exception {
        String loginId = "test_user_" + System.nanoTime();
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(loginId);
        user.setPasswordHash("dummy_hash");
        user.setMajor("컴퓨터공학");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());

        Long id = userDao.insert(user);
        assertNotNull(id);
        try {
            assertTrue(userDao.existsByLoginId(loginId));

            UserDto found = userDao.findByLoginId(loginId);
            assertNotNull(found);
            assertEquals("컴퓨터공학", found.getMajor());

            found.setEmail("a@b.com");
            found.setMajor("소프트웨어공학");
            found.setDesiredJobStatus("UNSET");
            found.setProfileUpdatedAt(LocalDateTime.now());
            userDao.updateProfile(found);

            UserDto updated = userDao.findById(id);
            assertEquals("소프트웨어공학", updated.getMajor());
            assertEquals("a@b.com", updated.getEmail());

            userDao.updateLastLogin(id);
            assertNotNull(userDao.findById(id).getLastLoginAt());

            try (Connection conn = DBUtil.getConnection()) {
                userDao.touchProfileUpdatedAt(conn, id);
            }

            userDao.softDelete(id);
            assertNull(userDao.findById(id));
            assertFalse(userDao.existsByLoginId(loginId));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }
}
