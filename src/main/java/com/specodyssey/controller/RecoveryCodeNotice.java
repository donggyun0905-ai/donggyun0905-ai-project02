package com.specodyssey.controller;

import jakarta.servlet.http.HttpSession;

/**
 * 복구 코드를 "한 번만" 보여 주기 위한 세션 전달 — 가입·비밀번호 찾기 직후 /recovery-code 화면이 꺼내 쓰고 바로 지운다.
 * (프로필에서 재발급한 코드는 같은 키를 쓰되 프로필 화면이 꺼내 쓴다.) 코드 원문은 서버 DB에 없으니
 * 이 세션 값이 사라지면 다시 볼 수 없고, 그때는 프로필에서 새로 발급받아야 한다.
 */
final class RecoveryCodeNotice {

    enum Context { REGISTER, RESET, PROFILE }

    static final String CODE_KEY = "recoveryCodeOnce";
    static final String CONTEXT_KEY = "recoveryCodeContext";

    private RecoveryCodeNotice() {
    }

    static void put(HttpSession session, String code, Context context) {
        session.setAttribute(CODE_KEY, code);
        session.setAttribute(CONTEXT_KEY, context.name());
    }

    /** 꺼내면서 지운다. 없으면 null. */
    static String[] take(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object code = session.getAttribute(CODE_KEY);
        Object context = session.getAttribute(CONTEXT_KEY);
        session.removeAttribute(CODE_KEY);
        session.removeAttribute(CONTEXT_KEY);
        return code == null ? null : new String[] {(String) code, context == null ? Context.REGISTER.name() : (String) context};
    }
}
