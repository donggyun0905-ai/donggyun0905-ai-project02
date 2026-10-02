package com.specodyssey.util;

import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * CSRF 토큰 — 세션마다 하나. 모든 POST 폼이 hidden 입력 {@code _csrf}로(자바스크립트 요청은 {@code X-CSRF-Token}
 * 헤더로) 같은 값을 돌려보내야 요청이 처리된다. 다른 사이트는 이 값을 알 수 없어서 로그인한 사용자의 브라우저를
 * 이용한 위조 요청이 통하지 않는다.
 */
public final class CsrfToken {

    public static final String SESSION_ATTRIBUTE = "csrfToken";
    public static final String REQUEST_ATTRIBUTE = "csrfToken";
    public static final String PARAMETER_NAME = "_csrf";
    public static final String HEADER_NAME = "X-CSRF-Token";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfToken() {
    }

    /** 세션의 토큰을 돌려주고, 없으면 새로 만든다. */
    public static String forSession(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_ATTRIBUTE);
            if (existing instanceof String token && !token.isEmpty()) {
                return token;
            }
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);
            session.setAttribute(SESSION_ATTRIBUTE, token);
            return token;
        }
    }

    /** 제출된 값이 세션의 토큰과 같은지(시간 차 공격을 피하려고 상수 시간 비교). 둘 중 하나라도 비면 false. */
    public static boolean matches(String expected, String submitted) {
        if (expected == null || expected.isEmpty() || submitted == null || submitted.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8));
    }
}
