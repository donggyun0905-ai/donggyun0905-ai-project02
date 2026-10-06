package com.specodyssey.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NotificationServlet "모두 읽음" 뒤 돌아갈 화면 계산 단위테스트 — 보던 화면(Referer)으로, 앱 밖이면 알림 목록으로.
 */
class NotificationServletTest {

    private static final String CTX = "/spec-odyssey";

    @Test
    void backPath_보던_화면의_앱_안_경로로_돌아간다() {
        assertEquals("/dashboard", NotificationServlet.backPath("http://localhost/spec-odyssey/dashboard", "localhost", CTX));
        assertEquals("/spec-archive/post?id=3",
                NotificationServlet.backPath("http://localhost/spec-odyssey/spec-archive/post?id=3", "localhost", CTX));
    }

    @Test
    void backPath_다른_사이트나_다른_앱이면_알림_목록() {
        assertEquals("/notifications", NotificationServlet.backPath(null, "localhost", CTX));
        assertEquals("/notifications", NotificationServlet.backPath("https://evil.example/spec-odyssey/dashboard", "localhost", CTX));
        assertEquals("/notifications", NotificationServlet.backPath("http://localhost/other-app/dashboard", "localhost", CTX));
        assertEquals("/notifications", NotificationServlet.backPath("http://localhost/spec-odyssey-evil/x", "localhost", CTX));
        assertEquals("/notifications", NotificationServlet.backPath("not a url ::", "localhost", CTX));
    }
}
