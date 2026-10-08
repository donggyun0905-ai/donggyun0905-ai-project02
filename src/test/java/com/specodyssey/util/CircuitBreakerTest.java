package com.specodyssey.util;

import com.specodyssey.util.CircuitBreaker.State;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 외부 API가 계속 실패할 때 호출을 끊는다 — 서킷 브레이커 (2026-10-08).
 * 시간을 주입해 sleep 없이 돌린다.
 */
class CircuitBreakerTest {

    private final AtomicLong now = new AtomicLong(0);

    private CircuitBreaker breaker(int threshold, long openMillis) {
        return new CircuitBreaker("테스트", threshold, openMillis, now::get);
    }

    private void advance(long millis) {
        now.addAndGet(millis);
    }

    @Test
    void 평소에는_그냥_통과시킨다() {
        CircuitBreaker breaker = breaker(3, 10_000);

        assertEquals(State.CLOSED, breaker.state());
        assertTrue(breaker.allowRequest());
        breaker.recordSuccess();
        assertTrue(breaker.allowRequest());
    }

    @Test
    void 연속_실패가_쌓이면_끊는다() {
        CircuitBreaker breaker = breaker(3, 10_000);

        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.allowRequest(), "임계값 전에는 계속 보낸다");

        breaker.recordFailure();

        assertEquals(State.OPEN, breaker.state());
        assertFalse(breaker.allowRequest(), "끊긴 뒤에는 보내지 않고 바로 대체 동작으로 넘긴다");
    }

    @Test
    void 중간에_성공하면_연속_실패가_초기화된다() {
        CircuitBreaker breaker = breaker(3, 10_000);

        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();

        assertEquals(State.CLOSED, breaker.state(), "띄엄띄엄 실패하는 것은 장애가 아니다");
        assertTrue(breaker.allowRequest());
    }

    @Test
    void 차단_시간이_지나면_한_번만_떠본다() {
        CircuitBreaker breaker = breaker(1, 10_000);
        breaker.recordFailure();
        assertFalse(breaker.allowRequest());

        advance(10_000);

        assertTrue(breaker.allowRequest(), "시간이 지나면 한 번은 보내 본다");
        assertFalse(breaker.allowRequest(), "떠보는 호출이 끝나기 전에 나머지를 또 보내면 한꺼번에 몰린다");
        assertEquals(State.HALF_OPEN, breaker.state());
    }

    @Test
    void 떠본_호출이_성공하면_정상으로_돌아간다() {
        CircuitBreaker breaker = breaker(1, 10_000);
        breaker.recordFailure();
        advance(10_000);
        breaker.allowRequest();

        breaker.recordSuccess();

        assertEquals(State.CLOSED, breaker.state());
        assertTrue(breaker.allowRequest());
        assertTrue(breaker.allowRequest(), "정상으로 돌아오면 제한 없이 통과한다");
    }

    @Test
    void 떠본_호출이_또_실패하면_다시_끊는다() {
        CircuitBreaker breaker = breaker(2, 10_000);
        breaker.recordFailure();
        breaker.recordFailure();
        advance(10_000);
        breaker.allowRequest();

        breaker.recordFailure();

        assertEquals(State.OPEN, breaker.state(), "아직 죽어 있으면 다시 끊어야 한다");
        assertFalse(breaker.allowRequest());
        // 임계값 2번이 아니라 한 번 실패로 바로 다시 끊긴다
        assertEquals(10_000, breaker.millisUntilRetry());
    }

    @Test
    void 끊긴_상태가_풀릴_때까지_남은_시간을_알려준다() {
        CircuitBreaker breaker = breaker(1, 10_000);
        assertEquals(0, breaker.millisUntilRetry(), "안 끊겼으면 0");

        breaker.recordFailure();
        assertEquals(10_000, breaker.millisUntilRetry());

        advance(4_000);
        assertEquals(6_000, breaker.millisUntilRetry());

        advance(6_000);
        assertEquals(0, breaker.millisUntilRetry());
    }

    @Test
    void 조회만으로도_다시_떠볼_시점인지_알_수_있다() {
        CircuitBreaker breaker = breaker(1, 10_000);
        breaker.recordFailure();
        assertEquals(State.OPEN, breaker.state());

        advance(10_000);

        assertEquals(State.HALF_OPEN, breaker.state(), "관리자 화면이 '곧 다시 시도'를 보여줄 수 있어야 한다");
    }

    @Test
    void 잘못된_설정은_만들_때_막는다() {
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker("x", 0, 1_000));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker("x", 1, 0));
    }
}
