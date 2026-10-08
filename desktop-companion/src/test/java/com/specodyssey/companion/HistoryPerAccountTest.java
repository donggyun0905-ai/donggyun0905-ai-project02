package com.specodyssey.companion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 말풍선 기록은 지금 계정 것만 — 한 PC에서 계정을 바꿔 써도 서로의 기록이 섞이지 않는다 (2026-10-08) */
class HistoryPerAccountTest {

    private static Message msg(String text) {
        return Message.local("k:" + text, Message.TODO, "할 일", text, null, null);
    }

    @Test
    void 지금_계정의_기록만_보인다() {
        Settings s = new Settings();
        s.account = 1L;
        s.addHistory(1, msg("A의 미션"));
        s.account = 2L;
        s.addHistory(2, msg("B의 미션"));

        List<Settings.HistoryItem> mine = s.historyFor(2L);
        assertEquals(1, mine.size());
        assertEquals("B의 미션", mine.get(0).text);
        assertEquals("A의 미션", s.historyFor(1L).get(0).text, "다시 A로 돌아오면 A의 기록이 그대로 있다");
    }

    @Test
    void 계정을_모르는_예전_기록과_연결_전에는_보이지_않는다() {
        Settings s = new Settings();
        s.addHistory(1, msg("누구 것인지 모르는 기록")); // account == null
        assertTrue(s.historyFor(5L).isEmpty());
        assertTrue(s.historyFor(null).isEmpty());
    }
}
