package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** AdminUserService 통합테스트. 관리자 회원 검색·프로필 수정·비밀번호 재설정·탈퇴 처리. */
class AdminUserServiceTest {

    private final UserDao userDao = new UserDao();
    private final AdminUserService service = new AdminUserService();

    private Long userId;
    private String loginId;

    @BeforeEach
    void setUp() throws Exception {
        loginId = "admin_user_test_" + System.nanoTime();
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(loginId);
        user.setPasswordHash(PasswordUtil.hash("originalPassword1"));
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 아이디로_검색하면_찾아진다() throws Exception {
        var results = service.search(loginId);
        assertEquals(1, results.size());
        assertEquals(userId, results.get(0).getId());
    }

    @Test
    void 빈_검색어면_빈_목록이다() throws Exception {
        assertTrue(service.search("  ").isEmpty());
    }

    @Test
    void 프로필을_수정할_수_있다() throws Exception {
        service.updateProfile(userId, "새이름", 25, "STUDENT", "new@test.com", "컴공", "3학년", "백엔드", null, "UNSET");

        UserDto reloaded = userDao.findByIdIncludingDeleted(userId);
        assertEquals("새이름", reloaded.getName());
        assertEquals(25, reloaded.getAge());
        assertEquals("new@test.com", reloaded.getEmail());
    }

    @Test
    void 비밀번호를_재설정하면_새_비밀번호로_인증된다() throws Exception {
        service.resetPassword(userId, "newPassword123");

        UserDto reloaded = userDao.findByIdIncludingDeleted(userId);
        assertTrue(PasswordUtil.verify("newPassword123", reloaded.getPasswordHash()));
    }

    @Test
    void 비밀번호가_너무_짧으면_예외를_던진다() {
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(userId, "short"));
    }

    @Test
    void 탈퇴_처리하면_is_deleted가_true가_된다() throws Exception {
        service.softDelete(userId);
        assertTrue(userDao.findByIdIncludingDeleted(userId).isDeleted());
    }
}
