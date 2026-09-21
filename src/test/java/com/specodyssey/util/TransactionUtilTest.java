package com.specodyssey.util;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSpecDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionUtilTest {

    private final UserDao userDao = new UserDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();

    private Long userId;

    @AfterEach
    void tearDown() throws Exception {
        if (userId == null) {
            return;
        }
        try (Connection conn = DBUtil.getConnection()) {
            try (var pstmt = conn.prepareStatement("DELETE FROM USER_SPECS WHERE user_id = ?")) {
                pstmt.setLong(1, userId);
                pstmt.executeUpdate();
            }
            try (var pstmt = conn.prepareStatement("DELETE FROM USERS WHERE id = ?")) {
                pstmt.setLong(1, userId);
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    void 람다에서_RuntimeException을_던지면_그때까지_넣은_행이_남지_않는다() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("txtest_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        UserSpecDto spec = new UserSpecDto();
        spec.setUserId(userId);
        spec.setSpecType("CERT");
        spec.setTitle("트랜잭션 롤백 테스트용");

        assertThrows(NullPointerException.class, () ->
                TransactionUtil.runInTransaction(conn -> {
                    try {
                        userSpecDao.insert(conn, spec);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    throw new NullPointerException("의도적 실패 — 여기서 롤백돼야 한다");
                })
        );

        assertEquals(0, userSpecDao.findByUserId(userId).size());
    }

    @Test
    void 정상_흐름은_커밋된다() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("txtest_ok_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        UserSpecDto spec = new UserSpecDto();
        spec.setUserId(userId);
        spec.setSpecType("CERT");
        spec.setTitle("정상 커밋 테스트용");

        TransactionUtil.runInTransaction(conn -> {
            try {
                return userSpecDao.insert(conn, spec);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(userSpecDao.findByUserId(userId).size() == 1);
    }
}
