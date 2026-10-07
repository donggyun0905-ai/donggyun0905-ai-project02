package com.specodyssey.companion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 말풍선 규칙 — 시각을 넘겨 30분 대기·자동 꺼짐·조용히·반복 안 함을 확인한다 */
class BubbleRulesTest {

    private static final long MIN = 60_000L;
    private static final long T0 = 1_000_000_000L;

    private static Message server(String key) {
        return new Message(key, Message.TODO, "할 일", key, "가기", "http://x/" + key, null, false);
    }

    private static Message local(String key) {
        return Message.local(key, Message.PRAISE, "칭찬", key, null, null);
    }

    @Test
    void 처음에는_바로_가장_앞의_말을_한다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        assertEquals("a", r.next(List.of(server("a"), server("b")), List.of(), T0, 0).key());
    }

    @Test
    void 떠_있는_동안에는_다른_말을_하지_않는다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        r.shown(server("a"));
        assertNull(r.next(List.of(server("a"), server("b")), List.of(), T0, 0));
    }

    @Test
    void 끈_뒤_30분이_지나야_다음_말() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        r.shown(server("a"));
        r.closed(T0);
        List<Message> list = List.of(server("a"), server("b"));
        assertNull(r.next(list, List.of(), T0 + 29 * MIN, 0));
        assertEquals("b", r.next(list, List.of(), T0 + 30 * MIN, 0).key());
    }

    @Test
    void 간격은_바꿀_수_있다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        r.shown(server("a"));
        r.closed(T0);
        r.setIntervalMillis(10 * MIN);
        assertEquals("b", r.next(List.of(server("b")), List.of(), T0 + 10 * MIN, 0).key());
    }

    @Test
    void 한_번_한_말은_다시_하지_않는다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of("a"));
        assertEquals("b", r.next(List.of(server("a"), server("b")), List.of(), T0, 0).key());
    }

    @Test
    void 말한_일을_하면_서버_목록에서_사라져_스스로_끈다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        r.shown(server("mission-left:2"));
        assertFalse(r.shouldAutoClose(List.of(server("mission-left:2"))));
        assertTrue(r.shouldAutoClose(List.of(server("mission-left:1"))));
    }

    @Test
    void 캐릭터가_만든_말은_서버_목록과_상관없이_떠_있다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        r.shown(local("hello"));
        assertFalse(r.shouldAutoClose(List.of()));
    }

    @Test
    void 조용히_시간에는_먼저_말하지_않는다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        assertNull(r.next(List.of(server("a")), List.of(local("tier:3")), T0, T0 + 60 * MIN));
        assertEquals("tier:3", r.next(List.of(server("a")), List.of(local("tier:3")), T0 + 61 * MIN, T0 + 60 * MIN).key());
    }

    @Test
    void 인사_칭찬은_대기_시간을_건너뛴다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        r.shown(server("a"));
        r.closed(T0);
        assertEquals("tier:3", r.next(List.of(server("b")), List.of(local("tier:3")), T0 + MIN, 0).key());
    }

    @Test
    void 대기_중인_말_수와_사라진_말_잊기() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of("old"));
        r.shown(server("a"));
        assertEquals(2, r.waitingCount(List.of(server("a"), server("b"), server("c"))));
        r.forgetGone(List.of(server("a")), List.of());
        assertEquals(Set.of("a"), r.spokenKeys(), "지금 없는 말은 잊어서 같은 상황이 다시 오면 다시 말한다");
    }

    @Test
    void 숫자만_바뀐_말은_이어받는다() {
        BubbleRules r = new BubbleRules(30 * MIN, Set.of());
        r.shown(server("mission-left:2026-10-07:3"));
        assertEquals("mission-left:2026-10-07:2", r.successor(List.of(server("mission-left:2026-10-07:2"), server("x"))).key());
        r.shown(server("noti:5"));
        assertNull(r.successor(List.of(server("noti:6"))), "다른 알림은 이어받지 않는다");
    }
}
