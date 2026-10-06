package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 회원 탈퇴 — 30일 유예 동안은 아이디를 잡아 두고 탈퇴를 취소할 수 있으며, 유예가 끝나면 아이디를 다시 쓸 수 있다. */
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

    // 유예가 끝난 상태를 만들기 위해 탈퇴 신청 시각을 과거로 돌린다
    private void ageWithdrawal(Long userId, int daysAgo) throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement(
                     "UPDATE USERS SET withdraw_requested_at = DATE_SUB(withdraw_requested_at, INTERVAL ? DAY) WHERE id = ?")) {
            p.setInt(1, daysAgo);
            p.setLong(2, userId);
            p.executeUpdate();
        }
    }

    private String loginIdOf(Long userId) throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement("SELECT login_id, name, email FROM USERS WHERE id = ?")) {
            p.setLong(1, userId);
            try (ResultSet rs = p.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Test
    void 탈퇴하면_일반_로그인은_안_되고_비밀번호가_맞으면_탈퇴_취소를_제안한다() throws Exception {
        String loginId = "test_wd_" + System.nanoTime();
        Long id = register(loginId);

        userService.withdraw(id, PASSWORD);

        assertNull(userDao.findByLoginId(loginId), "탈퇴 계정은 일반 조회로 찾을 수 없다");
        UserService.PendingWithdrawalException pending =
                assertThrows(UserService.PendingWithdrawalException.class, () -> userService.login(loginId, PASSWORD));
        assertEquals(id, pending.getUserId());
        assertThrows(UserService.InvalidCredentialException.class, () -> userService.login(loginId, "WrongPassw0rd!"),
                "비밀번호가 틀리면 탈퇴 계정인지 알려주지 않는다");
    }

    @Test
    void 유예_중에는_같은_아이디로_가입할_수_없고_탈퇴를_취소하면_원래_아이디로_로그인된다() throws Exception {
        String loginId = "test_wd_" + System.nanoTime();
        Long id = register(loginId);
        userService.withdraw(id, PASSWORD);

        assertThrows(UserService.DuplicateLoginIdException.class, () -> register(loginId),
                "탈퇴 취소를 위해 유예 중에는 아이디를 잡아 둔다");

        // 복구 코드를 발급받지 않은 계정은 비밀번호 확인만으로 되살린다
        assertEquals(id, userService.cancelWithdrawal(id, null).getId());
        assertEquals(id, userService.login(loginId, PASSWORD).getId());
    }

    @Test
    void 유예가_끝나면_취소할_수_없고_같은_아이디로_새로_가입할_수_있다() throws Exception {
        String loginId = "test_wd_" + System.nanoTime();
        Long old = register(loginId);
        userService.withdraw(old, PASSWORD);
        ageWithdrawal(old, UserService.WITHDRAWAL_GRACE_DAYS + 1);

        assertThrows(UserService.InvalidCredentialException.class, () -> userService.login(loginId, PASSWORD));
        assertThrows(UserService.WithdrawalGraceExpiredException.class, () -> userService.cancelWithdrawal(old, null));

        Long again = register(loginId); // 정리 배치가 아직 안 돌았어도 가입 시 아이디를 비워 준다
        assertNotEquals(old, again);
        assertEquals(again, userService.login(loginId, PASSWORD).getId());
    }

    @Test
    void 정리_배치는_유예가_끝난_계정만_아이디를_비우고_개인정보를_지운다() throws Exception {
        String expiredId = "test_wd_exp_" + System.nanoTime();
        String pendingId = "test_wd_pen_" + System.nanoTime();
        Long expired = register(expiredId);
        Long pending = register(pendingId);
        userService.withdraw(expired, PASSWORD);
        userService.withdraw(pending, PASSWORD);
        ageWithdrawal(expired, UserService.WITHDRAWAL_GRACE_DAYS + 1);

        userService.purgeExpiredWithdrawals();

        assertTrue(loginIdOf(expired).startsWith("del_" + expired + "_"));
        assertEquals(pendingId, loginIdOf(pending), "유예 중인 계정은 그대로 둔다");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement("SELECT name FROM USERS WHERE id = ?")) {
            p.setLong(1, expired);
            try (ResultSet rs = p.executeQuery()) {
                rs.next();
                assertNull(rs.getString(1), "개인정보(이름)를 지운다");
            }
        }
    }

    @Test
    void 아이디가_아주_길어도_유예가_끝난_뒤_아이디가_서로_겹치지_않는다() throws Exception {
        String longId = ("w" + System.nanoTime() + "x".repeat(50)).substring(0, 50);
        Long a = register(longId);
        userService.withdraw(a, PASSWORD);
        ageWithdrawal(a, UserService.WITHDRAWAL_GRACE_DAYS + 1);
        Long b = register(longId);
        userService.withdraw(b, PASSWORD);
        ageWithdrawal(b, UserService.WITHDRAWAL_GRACE_DAYS + 1);
        register(longId); // 같은 아이디가 두 번 정리돼도 UNIQUE 충돌이 없어야 한다
        assertNotEquals(a, b);
    }

    @Test
    void 예전_방식으로_탈퇴해_아이디가_그대로_남은_계정도_재가입하면_아이디를_돌려받는다() throws Exception {
        String loginId = "test_old_" + System.nanoTime();
        Long old = register(loginId);
        // 유예 컬럼이 생기기 전의 탈퇴 상태(withdraw_requested_at 없음, 아이디 그대로)를 만든다
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement("UPDATE USERS SET is_deleted = TRUE WHERE id = ?")) {
            p.setLong(1, old);
            p.executeUpdate();
        }
        Long again = register(loginId);
        assertNotEquals(old, again);
        assertEquals(again, userService.login(loginId, PASSWORD).getId());
        assertThrows(UserService.DuplicateLoginIdException.class, () -> register(loginId), "살아 있는 계정의 아이디는 그대로 중복이다");
    }

    @Test
    void 유예_중인_탈퇴_계정도_복구_코드로_비밀번호를_찾을_수_있다() throws Exception {
        String loginId = "test_wd_rc_" + System.nanoTime();
        Long id = register(loginId);
        String code = userService.issueRecoveryCode(id);
        userService.withdraw(id, PASSWORD);

        userService.resetPasswordWithRecoveryCode(loginId, code, "NewPassw0rd!z");

        assertThrows(UserService.PendingWithdrawalException.class, () -> userService.login(loginId, "NewPassw0rd!z"),
                "비밀번호만 바뀌고 계정은 탈퇴 상태 그대로 — 다음 로그인에서 취소를 제안한다");
    }

    @Test
    void 복구_코드를_받아_둔_계정은_코드가_맞아야_탈퇴가_취소된다() throws Exception {
        String loginId = "test_wd_rcc_" + System.nanoTime();
        Long id = register(loginId);
        String code = userService.issueRecoveryCode(id);
        userService.withdraw(id, PASSWORD);

        assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.cancelWithdrawal(id, null), "코드를 안 내면 안 된다");
        assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.cancelWithdrawal(id, "AAAA-BBBB-CCCC"), "틀린 코드도 안 된다");
        assertThrows(UserService.PendingWithdrawalException.class, () -> userService.login(loginId, PASSWORD),
                "실패해도 계정은 탈퇴 상태 그대로다");

        assertEquals(id, userService.cancelWithdrawal(id, code).getId());
        assertEquals(id, userService.login(loginId, PASSWORD).getId());
    }

    @Test
    void 로그인은_복구_코드가_필요한_계정인지_알려_준다() throws Exception {
        String withCode = "test_wd_need_" + System.nanoTime();
        Long needs = register(withCode);
        userService.issueRecoveryCode(needs);
        userService.withdraw(needs, PASSWORD);
        UserService.PendingWithdrawalException needsCode = assertThrows(
                UserService.PendingWithdrawalException.class, () -> userService.login(withCode, PASSWORD));
        assertTrue(needsCode.isRecoveryCodeRequired());

        String noCode = "test_wd_nocode_" + System.nanoTime();
        Long plain = register(noCode);
        userService.withdraw(plain, PASSWORD);
        UserService.PendingWithdrawalException noCodeNeeded = assertThrows(
                UserService.PendingWithdrawalException.class, () -> userService.login(noCode, PASSWORD));
        assertFalse(noCodeNeeded.isRecoveryCodeRequired());
    }

    @Test
    void 아이디_확인은_살아_있는_계정과_유예_중인_탈퇴_계정을_구분해_알려_준다() throws Exception {
        String loginId = "test_wd_chk_" + System.nanoTime();

        assertTrue(userService.checkLoginId(loginId).available(), "아무도 안 쓰는 아이디");
        assertFalse(userService.checkLoginId("  ").available(), "빈 아이디는 쓸 수 없다");

        Long id = register(loginId);
        UserService.LoginIdCheck taken = userService.checkLoginId(loginId);
        assertFalse(taken.available());
        assertEquals("이미 사용 중인 아이디입니다.", taken.message());

        userService.withdraw(id, PASSWORD);
        UserService.LoginIdCheck held = userService.checkLoginId(loginId);
        assertFalse(held.available(), "유예 중에는 아직 못 쓴다");
        assertTrue(held.message().contains("탈퇴 유예"), held.message());
        assertTrue(held.message().contains("일 뒤"), "언제 쓸 수 있는지 알려 준다: " + held.message());

        ageWithdrawal(id, UserService.WITHDRAWAL_GRACE_DAYS + 1);
        assertTrue(userService.checkLoginId(loginId).available(), "유예가 끝나면 다시 쓸 수 있다");
    }

    @Test
    void 아이디_확인과_가입이_같은_판단을_한다() throws Exception {
        String loginId = "test_wd_same_" + System.nanoTime();
        Long id = register(loginId);
        userService.withdraw(id, PASSWORD);

        UserService.LoginIdCheck check = userService.checkLoginId(loginId);
        UserService.DuplicateLoginIdException thrown =
                assertThrows(UserService.DuplicateLoginIdException.class, () -> register(loginId));
        assertEquals(check.message(), thrown.getMessage(), "화면 안내와 가입 실패 문구가 같아야 한다");
    }
}
