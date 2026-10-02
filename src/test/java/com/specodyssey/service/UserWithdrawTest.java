package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 회원 탈퇴 — 탈퇴한 아이디로 다시 가입할 수 있어야 한다. */
class UserWithdrawTest {

    private static final String PASSWORD = "Passw0rd!x";
    private final UserService userService = new UserService();
    private final UserDao userDao = new UserDao();
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : created) {
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION", "user_id", id);
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    private Long register(String loginId) throws Exception {
        Long id = userService.register(loginId, PASSWORD, null, PersonalInfo.of("탈퇴테스터", "25", "JOB_SEEKER", null), null, null);
        created.add(id);
        return id;
    }

    @Test
    void 탈퇴한_아이디로_다시_가입할_수_있고_옛_계정으로는_로그인되지_않는다() throws Exception {
        String loginId = "test_wd_" + System.nanoTime();
        Long first = register(loginId);

        userService.withdraw(first, PASSWORD);

        assertNull(userDao.findByLoginId(loginId), "탈퇴 계정은 아이디로 찾을 수 없다");
        assertThrows(UserService.InvalidCredentialException.class, () -> userService.login(loginId, PASSWORD));

        Long second = register(loginId); // 이전에는 "이미 사용 중인 아이디입니다"
        assertNotEquals(first, second);
        assertEquals(second, userService.login(loginId, PASSWORD).getId());
    }

    @Test
    void 아이디가_아주_길어도_탈퇴_뒤_아이디가_서로_겹치지_않는다() throws Exception {
        String longId = ("w" + System.nanoTime() + "x".repeat(50)).substring(0, 50);
        Long a = register(longId);
        userService.withdraw(a, PASSWORD);
        Long b = register(longId);
        userService.withdraw(b, PASSWORD); // 같은 아이디가 두 번 탈퇴해도 UNIQUE 충돌이 없어야 한다
        assertNotEquals(a, b);
    }
}
