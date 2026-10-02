package com.specodyssey.controller;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.PersonalInfo;
import com.specodyssey.service.UserService;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.PasswordUtil;
import com.specodyssey.util.RecoveryCode;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 비밀번호 찾기(복구 코드) — 서비스 규칙, 서블릿, 한 번만 보여 주는 화면, 프로필에서의 재발급. */
class PasswordResetFlowTest {

    private static final String OLD = "OldPassw0rd!";
    private final UserService userService = new UserService();
    private final UserDao userDao = new UserDao();
    private final PasswordResetServlet resetServlet = new PasswordResetServlet();
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : created) {
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    private String[] newUserWithCode() throws Exception {
        String loginId = "test_reset_" + System.nanoTime();
        Long id = userService.register(loginId, OLD, null, PersonalInfo.of("복구테스터", "25", "JOB_SEEKER", null), null, null);
        created.add(id);
        return new String[] {loginId, userService.issueRecoveryCode(id), String.valueOf(id)};
    }

    private FakeWeb.Request reset(String loginId, String code, String next, String confirm) {
        return FakeWeb.request().post("/password-reset").param("loginId", loginId).param("recoveryCode", code)
                .param("newPassword", next).param("newPasswordConfirm", confirm);
    }

    @Test
    void 코드는_해시로만_저장되고_맞는_코드로_비밀번호를_바꾸면_새_코드가_나오고_옛_코드는_못_쓴다() throws Exception {
        String[] u = newUserWithCode();
        String stored = userDao.findByLoginId(u[0]).getRecoveryCodeHash();
        assertNotNull(stored);
        assertFalse(stored.contains(RecoveryCode.forHash(u[1])), "원문이 저장되면 안 된다");

        // 소문자·하이픈 없이 입력해도 된다
        String sloppy = RecoveryCode.forHash(u[1]).toLowerCase();
        String newCode = userService.resetPasswordWithRecoveryCode(u[0], sloppy, "BrandNewPassw0rd!");

        assertNotEquals(u[1], newCode);
        assertNotNull(userService.login(u[0], "BrandNewPassw0rd!"));
        assertThrows(UserService.InvalidCredentialException.class, () -> userService.login(u[0], OLD));
        assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.resetPasswordWithRecoveryCode(u[0], u[1], "AnotherPassw0rd!"), "쓴 코드는 한 번뿐");
        assertNotNull(userService.resetPasswordWithRecoveryCode(u[0], newCode, "AnotherPassw0rd!"));
    }

    @Test
    void 없는_아이디_코드_없는_계정_틀린_코드는_같은_메시지로_거절한다() throws Exception {
        String[] u = newUserWithCode();
        String loginIdNoCode = "test_nocode_" + System.nanoTime();
        created.add(userService.register(loginIdNoCode, OLD, null, PersonalInfo.of("코드없음", "25", "JOB_SEEKER", null), null, null));

        String m1 = assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.resetPasswordWithRecoveryCode("nobody_" + System.nanoTime(), u[1], "NewPassw0rd!x")).getMessage();
        String m2 = assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.resetPasswordWithRecoveryCode(loginIdNoCode, u[1], "NewPassw0rd!x")).getMessage();
        String m3 = assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.resetPasswordWithRecoveryCode(u[0], RecoveryCode.generate(), "NewPassw0rd!x")).getMessage();
        String m4 = assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.resetPasswordWithRecoveryCode(u[0], "짧음", "NewPassw0rd!x")).getMessage();

        assertEquals(m1, m2);
        assertEquals(m2, m3);
        assertEquals(m3, m4);
        assertTrue(PasswordUtil.verify(OLD, userDao.findByLoginId(u[0]).getPasswordHash()), "거절되면 아무것도 바뀌지 않는다");
    }

    @Test
    void 새_비밀번호가_규칙에_안_맞으면_코드를_쓰지_않고_거절한다() throws Exception {
        String[] u = newUserWithCode();
        assertThrows(UserService.InvalidInputException.class,
                () -> userService.resetPasswordWithRecoveryCode(u[0], u[1], "short"));
        assertNotNull(userService.resetPasswordWithRecoveryCode(u[0], u[1], "ValidPassw0rd!"), "코드는 아직 유효하다");
    }

    @Test
    void 서블릿_성공하면_복구_코드_화면으로_가고_새_코드가_세션에_실린다() throws Exception {
        String[] u = newUserWithCode();
        FakeWeb.Request req = reset(u[0], u[1], "ServletPassw0rd!", "ServletPassw0rd!");
        req.session = new FakeWeb.Session();
        FakeWeb.Response resp = FakeWeb.response();

        resetServlet.doPost(req.http(), resp.http());

        assertEquals("/recovery-code", resp.redirect);
        String newCode = (String) req.session.attributes.get("recoveryCodeOnce");
        assertTrue(RecoveryCode.looksValid(newCode));
        assertEquals("RESET", req.session.attributes.get("recoveryCodeContext"));
        assertNotNull(userService.login(u[0], "ServletPassw0rd!"));
    }

    @Test
    void 서블릿_확인_입력이_다르거나_코드가_틀리면_폼을_다시_보여_준다() throws Exception {
        String[] u = newUserWithCode();
        FakeWeb.Request mismatch = reset(u[0], u[1], "ServletPassw0rd!", "Different0ne!!");
        resetServlet.doPost(mismatch.http(), FakeWeb.response().http());
        assertEquals(List.of("/WEB-INF/views/password-reset.jsp"), mismatch.forwards);
        assertTrue(((String) mismatch.attributes.get("errorMessage")).contains("서로 다릅니다"));

        FakeWeb.Request wrong = reset(u[0], RecoveryCode.generate(), "ServletPassw0rd!", "ServletPassw0rd!");
        FakeWeb.Response resp = FakeWeb.response();
        resetServlet.doPost(wrong.http(), resp.http());
        assertNull(resp.redirect);
        assertEquals("아이디 또는 복구 코드가 올바르지 않습니다.", wrong.attributes.get("errorMessage"));
    }

    @Test
    void 코드를_연달아_틀리면_맞는_코드도_잠시_막힌다() throws Exception {
        String[] u = newUserWithCode();
        for (int i = 0; i < 5; i++) {
            resetServlet.doPost(reset(u[0], RecoveryCode.generate(), "ServletPassw0rd!", "ServletPassw0rd!").http(),
                    FakeWeb.response().http());
        }
        FakeWeb.Request req = reset(u[0], u[1], "ServletPassw0rd!", "ServletPassw0rd!");
        FakeWeb.Response resp = FakeWeb.response();
        resetServlet.doPost(req.http(), resp.http());

        assertNull(resp.redirect);
        assertTrue(((String) req.attributes.get("errorMessage")).contains("잠시 막혔습니다"));
        assertTrue(PasswordUtil.verify(OLD, userDao.findByLoginId(u[0]).getPasswordHash()));
    }

    @Test
    void 복구_코드_화면은_코드를_한_번만_보여_주고_없으면_로그인으로_보낸다() throws Exception {
        RecoveryCodeServlet servlet = new RecoveryCodeServlet();
        FakeWeb.Request req = FakeWeb.request();
        req.session = new FakeWeb.Session();
        RecoveryCodeNotice.put(req.session.http(), "ABCD-EFGH-JKLM-NPQR", RecoveryCodeNotice.Context.REGISTER);
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doGet(req.http(), resp.http());

        assertEquals(List.of("/WEB-INF/views/recovery-code.jsp"), req.forwards);
        assertEquals("ABCD-EFGH-JKLM-NPQR", req.attributes.get("recoveryCode"));
        assertEquals("no-store", resp.headers.get("Cache-Control"));

        FakeWeb.Response again = FakeWeb.response();
        FakeWeb.Request second = FakeWeb.request();
        second.session = req.session;
        servlet.doGet(second.http(), again.http());
        assertEquals("/login", again.redirect, "새로고침하면 코드는 사라진다");

        FakeWeb.Response direct = FakeWeb.response();
        servlet.doGet(FakeWeb.request().http(), direct.http());
        assertEquals("/login", direct.redirect);
    }

    @Test
    void 프로필에서_현재_비밀번호로_재발급하면_이전_코드는_못_쓰고_틀리면_발급되지_않는다() throws Exception {
        String[] u = newUserWithCode();
        UserDto user = userDao.findById(Long.valueOf(u[2]));
        user.setPasswordHash(null);
        PasswordServlet servlet = new PasswordServlet();

        FakeWeb.Request wrong = FakeWeb.request().post("/profile/password").loggedIn(user)
                .param("action", "issueRecoveryCode").param("currentPassword", "틀린비밀번호!!");
        servlet.doPost(wrong.http(), FakeWeb.response().http());
        assertEquals("현재 비밀번호가 올바르지 않습니다.", wrong.session.attributes.get("passwordError"));
        assertNull(wrong.session.attributes.get("recoveryCodeOnce"));

        FakeWeb.Request ok = FakeWeb.request().post("/profile/password").loggedIn(user)
                .param("action", "issueRecoveryCode").param("currentPassword", OLD);
        FakeWeb.Response resp = FakeWeb.response();
        servlet.doPost(ok.http(), resp.http());
        assertEquals("/profile", resp.redirect);
        String fresh = (String) ok.session.attributes.get("recoveryCodeOnce");
        assertTrue(RecoveryCode.looksValid(fresh));
        assertThrows(UserService.InvalidCredentialException.class,
                () -> userService.resetPasswordWithRecoveryCode(u[0], u[1], "NewPassw0rd!x"), "이전 코드는 무효");
        assertNotNull(userService.resetPasswordWithRecoveryCode(u[0], fresh, "NewPassw0rd!x"));
    }
}
