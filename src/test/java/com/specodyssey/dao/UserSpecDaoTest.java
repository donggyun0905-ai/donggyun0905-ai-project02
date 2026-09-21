package com.specodyssey.dao;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserSpecDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final UserSpecDao specDao = new UserSpecDao();
    private static Long userId;

    @BeforeAll
    static void setUpUser() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_spec_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterAll
    static void tearDownUser() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            // delete()는 논리 삭제라 행이 물리적으로 남아있다 — USERS를 지우기 전에 자식부터 정리
            TestFixtures.hardDeleteByColumn(conn, "USER_SPECS", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_update_delete() throws Exception {
        UserSpecDto spec = new UserSpecDto();
        spec.setUserId(userId);
        spec.setSpecType("CERT");
        spec.setTitle("정보처리기사");
        spec.setIssuer("한국산업인력공단");
        spec.setAcquiredDate(LocalDate.of(2024, 1, 1));

        Long specId;
        try (Connection conn = DBUtil.getConnection()) {
            specId = specDao.insert(conn, spec);
        }
        assertNotNull(specId);

        List<UserSpecDto> specs = specDao.findByUserId(userId);
        assertEquals(1, specs.size());
        assertEquals("정보처리기사", specs.get(0).getTitle());

        UserSpecDto toUpdate = specs.get(0);
        toUpdate.setTitle("SQLD");
        try (Connection conn = DBUtil.getConnection()) {
            specDao.update(conn, toUpdate, userId);
        }
        assertEquals("SQLD", specDao.findByUserId(userId).get(0).getTitle());

        try (Connection conn = DBUtil.getConnection()) {
            specDao.delete(conn, specId, userId);
        }
        assertTrue(specDao.findByUserId(userId).isEmpty());
    }
}
