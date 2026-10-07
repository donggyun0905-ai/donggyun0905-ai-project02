package com.specodyssey.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 비밀번호 해시 + 솔트 유틸 (Argon2id).
 * 저장 형식(PHC 문자열): $argon2id$v=19$m={memoryKiB},t={iterations},p={parallelism}${saltBase64}${hashBase64}
 * 솔트(16바이트)는 해시할 때마다 새로 만들어 문자열 안에 함께 저장된다.
 *
 * 예전 형식(PBKDF2WithHmacSHA256, {iterations}:{saltBase64}:{hashBase64})도 검증만은 계속 지원한다.
 * 이미 가입한 계정·발급한 복구 코드가 깨지지 않게 하기 위함이며, 로그인 성공 시 needsRehash()로 Argon2id로 바꿔 저장한다.
 */
public final class PasswordUtil {

    // OWASP Password Storage Cheat Sheet 권장 최소 설정 (m=19MiB, t=2, p=1)
    private static final int MEMORY_KIB = 19 * 1024;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final String ARGON2ID_PREFIX = "$argon2id$";

    // 상태가 없어 여러 스레드가 함께 써도 된다
    private static final Argon2 ARGON2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    private static final String LEGACY_ALGORITHM = "PBKDF2WithHmacSHA256";

    private PasswordUtil() {
    }

    public static String hash(String rawPassword) {
        char[] chars = rawPassword.toCharArray();
        try {
            return ARGON2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, chars);
        } finally {
            ARGON2.wipeArray(chars); // 평문을 메모리에 남기지 않는다
        }
    }

    public static boolean verify(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        if (stored.startsWith(ARGON2ID_PREFIX)) {
            char[] chars = rawPassword.toCharArray();
            try {
                return ARGON2.verify(stored, chars);
            } finally {
                ARGON2.wipeArray(chars);
            }
        }
        return verifyLegacyPbkdf2(rawPassword, stored);
    }

    /** 예전 PBKDF2 해시이거나 Argon2id 설정값이 지금과 다르면 true — 로그인 성공 직후 다시 해시해 저장한다. */
    public static boolean needsRehash(String stored) {
        if (stored == null || !stored.startsWith(ARGON2ID_PREFIX)) {
            return true;
        }
        return ARGON2.needsRehash(stored, ITERATIONS, MEMORY_KIB, PARALLELISM);
    }

    // ---------------------------------------------------------------- 예전 형식(PBKDF2) 검증 전용

    private static boolean verifyLegacyPbkdf2(String rawPassword, String stored) {
        String[] parts = stored.split(":");
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, expectedHash.length * 8);
            byte[] actualHash = SecretKeyFactory.getInstance(LEGACY_ALGORITHM).generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException e) {
            return false; // 숫자·Base64가 깨진 값
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("비밀번호 해시 검증 실패", e);
        }
    }
}
