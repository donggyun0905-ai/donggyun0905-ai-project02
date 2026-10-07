package com.specodyssey.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 프로필 화면에 한 번만 보여 줄 안내 문구 (2026-10-07).
 *
 * 기술·스펙 추가는 저장 뒤 /profile로 리다이렉트하기 때문에 요청 속성으로는 문구를 넘길 수 없다.
 * "이미 등록된 기술입니다"처럼 흔한 입력 실수에 resp.sendError(409·400)를 쓰면 컨테이너 에러 페이지가
 * 떠서(web.xml에 409·400 항목이 없어 Tomcat 기본 화면) 입력하던 내용이 통째로 사라졌다 —
 * 그래서 세션에 잠깐 두고 다음 /profile 조회에서 꺼내 쓰고 지운다(RoadmapServlet의 roadmapNotice와 같은 방식).
 */
final class ProfileNotice {

    private static final String ERROR_KEY = "profileErrorNotice";

    private ProfileNotice() {
    }

    /** 리다이렉트 뒤 프로필 화면에 빨간 문구로 한 번 보여 준다. */
    static void putError(HttpServletRequest req, String message) {
        req.getSession().setAttribute(ERROR_KEY, message);
    }

    /** 프로필 조회에서 꺼내 요청 속성으로 옮기고 세션에서 지운다 — 새로고침하면 다시 뜨지 않는다. */
    static void consume(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return;
        }
        Object message = session.getAttribute(ERROR_KEY);
        if (message != null) {
            session.removeAttribute(ERROR_KEY);
            req.setAttribute("errorMessage", message);
        }
    }
}
