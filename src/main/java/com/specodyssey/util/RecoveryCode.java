package com.specodyssey.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 비밀번호 찾기용 복구 코드 — 메일 발송 없이 쓸 수 있는 재설정 수단.
 * 가입(또는 프로필에서 발급)할 때 화면에 한 번만 보여 주고, 서버에는 비밀번호와 같은 방식의 해시만 둔다.
 * 16자(헷갈리는 글자 0/O/1/I 제외한 32가지) = 80비트라 추측할 수 없고, 4자씩 끊어 읽기 쉽게 보여 준다.
 */
public final class RecoveryCode {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCode() {
    }

    /** 새 복구 코드 — 화면에 보여 줄 형태(XXXX-XXXX-XXXX-XXXX) */
    public static String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 입력을 비교용으로 다듬는다 — 대소문자·하이픈·공백은 무시한다(글자를 바꿔치기하지는 않는다). */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : input.toUpperCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 해시를 만들 때와 확인할 때 같은 형태(하이픈 없는 대문자)로 맞춘다. */
    public static String forHash(String code) {
        return normalize(code);
    }

    /** 형식(16자, 허용 글자만)이 맞는지 — 틀리면 해시 계산 없이 바로 거절할 수 있다. */
    public static boolean looksValid(String input) {
        String n = normalize(input);
        if (n.length() != LENGTH) {
            return false;
        }
        for (char c : n.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
