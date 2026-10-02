package com.specodyssey.controller;

import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;

/**
 * 복습 단계 생성 검사를 "세션당 하루 한 번"으로 줄이는 문 — 로드맵 화면을 열 때마다 DB를 훑지 않게 한다.
 * 마지막 검사 날짜를 세션에 두고, 오늘 이미 했으면 건너뛴다. 새로 로그인하면(새 세션) 한 번 다시 검사한다.
 * 로드맵을 새로 만들면(generate·재분석) reset으로 다시 열어 준다.
 */
final class ReviewCheckGate {

    static final String SESSION_KEY = "reviewCheckedOn";

    private ReviewCheckGate() {
    }

    /** 오늘 검사해야 하면 true — 그리고 "오늘 했음"으로 기록한다. */
    static boolean shouldCheck(HttpSession session, LocalDate today) {
        if (session == null) {
            return true; // 세션이 없으면 기록할 곳이 없으니 매번 검사한다(결과는 같다 — 멱등)
        }
        if (today.toString().equals(session.getAttribute(SESSION_KEY))) {
            return false;
        }
        session.setAttribute(SESSION_KEY, today.toString());
        return true;
    }

    static void reset(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }
}
