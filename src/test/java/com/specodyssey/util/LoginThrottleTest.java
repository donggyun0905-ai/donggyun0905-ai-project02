package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    @Test
    void 연속_실패가_한도에_닿으면_잠기고_시간이_지나면_풀린다() {
        LoginThrottle throttle = new LoginThrottle();
        String key = LoginThrottle.key(" Alice ", "1.2.3.4");
        long t = 1_000_000L;
        for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
            throttle.recordFailure(key, t);
            assertEquals(0, throttle.secondsLocked(key, t));
        }
        throttle.recordFailure(key, t);
        assertTrue(throttle.secondsLocked(key, t) > 0);
        assertTrue(throttle.secondsLocked(key, t + LoginThrottle.LOCK_DURATION.toMillis() - 1) > 0);
        assertEquals(0, throttle.secondsLocked(key, t + LoginThrottle.LOCK_DURATION.toMillis()));

        // 풀린 뒤에는 처음부터 다시 센다
        throttle.recordFailure(key, t + LoginThrottle.LOCK_DURATION.toMillis());
        assertEquals(0, throttle.secondsLocked(key, t + LoginThrottle.LOCK_DURATION.toMillis()));
    }

    @Test
    void 성공하면_실패_기록이_지워지고_아이디와_IP가_다르면_따로_센다() {
        LoginThrottle throttle = new LoginThrottle();
        String key = LoginThrottle.key("bob", "1.1.1.1");
        for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
            throttle.recordFailure(key, 0);
        }
        throttle.recordSuccess(key);
        throttle.recordFailure(key, 0);
        assertEquals(0, throttle.secondsLocked(key, 0));

        String other = LoginThrottle.key("bob", "2.2.2.2");
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure(other, 0);
        }
        assertTrue(throttle.secondsLocked(other, 0) > 0);
        assertEquals(0, throttle.secondsLocked(key, 0), "다른 IP의 잠금이 옮겨오지 않는다");
    }
}
