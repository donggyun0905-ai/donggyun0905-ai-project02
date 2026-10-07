package com.specodyssey.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordUtilTest {

    // 예전 PBKDF2 형식으로 저장된 "test1234" — sql/do-not-run/spec_archive_test_accounts_seed.sql과 같은 값
    private static final String LEGACY_TEST1234 =
            "120000:ms2xNjDB1LUAROTu391FVg==:IpoaWkV+daypP6yGycCcel/6GjqGXZFMKiB6gMQcNKM=";

    @Test
    void 새_해시는_Argon2id_형식이고_검증된다() {
        String stored = PasswordUtil.hash("Passw0rd!");
        assertTrue(stored.startsWith("$argon2id$"), stored);
        assertTrue(PasswordUtil.verify("Passw0rd!", stored));
        assertFalse(PasswordUtil.verify("wrong-password", stored));
    }

    @Test
    void 같은_비밀번호도_솔트가_달라_해시가_매번_다르다() {
        String a = PasswordUtil.hash("Passw0rd!");
        String b = PasswordUtil.hash("Passw0rd!");
        assertNotEquals(a, b);
        assertTrue(PasswordUtil.verify("Passw0rd!", a));
        assertTrue(PasswordUtil.verify("Passw0rd!", b));
    }

    @Test
    void 예전_PBKDF2_해시도_검증된다() {
        assertTrue(PasswordUtil.verify("test1234", LEGACY_TEST1234));
        assertFalse(PasswordUtil.verify("test12345", LEGACY_TEST1234));
    }

    @Test
    void 예전_해시만_재해시_대상이다() {
        assertTrue(PasswordUtil.needsRehash(LEGACY_TEST1234));
        assertFalse(PasswordUtil.needsRehash(PasswordUtil.hash("Passw0rd!")));
    }

    @Test
    void 깨진_값이나_null은_예외_없이_false() {
        assertFalse(PasswordUtil.verify("x", null));
        assertFalse(PasswordUtil.verify(null, PasswordUtil.hash("x")));
        assertFalse(PasswordUtil.verify("x", ""));
        assertFalse(PasswordUtil.verify("x", "abc:!!!:###"));
    }
}
