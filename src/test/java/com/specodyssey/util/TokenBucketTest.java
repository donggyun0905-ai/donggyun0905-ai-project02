package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 외부 API 호출 횟수 제한 — 토큰 버킷 (2026-10-08).
 * 시간을 주입해 sleep 없이 돌린다 — 테스트가 느려지면 전체 수백 개가 같이 느려진다.
 */
class TokenBucketTest {

    private final AtomicLong now = new AtomicLong(0);

    private TokenBucket bucket(int capacity, double perMinute) {
        return new TokenBucket(capacity, perMinute, now::get);
    }

    private void advance(long millis) {
        now.addAndGet(millis);
    }

    @Test
    void 처음에는_꽉_차_있어_바로_쓸_수_있다() {
        TokenBucket bucket = bucket(3, 60);

        assertEquals(3, bucket.available(), "서버를 켠 직후 첫 호출이 막히면 안 된다");
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
    }

    @Test
    void 버킷이_비면_거절한다() {
        TokenBucket bucket = bucket(2, 60);
        bucket.tryAcquire();
        bucket.tryAcquire();

        assertFalse(bucket.tryAcquire(), "어차피 거절될 호출을 보내지 않는 것이 이 장치의 목적이다");
        assertEquals(0, bucket.available());
    }

    @Test
    void 시간이_지나면_채워진다() {
        TokenBucket bucket = bucket(2, 60); // 분당 60개 = 1초에 1개
        bucket.tryAcquire();
        bucket.tryAcquire();
        assertFalse(bucket.tryAcquire());

        advance(1_000);

        assertTrue(bucket.tryAcquire(), "1초 뒤에는 하나 채워져 있어야 한다");
        assertFalse(bucket.tryAcquire(), "하나만 채워졌다");
    }

    @Test
    void 버킷_크기보다_많이_쌓이지_않는다() {
        TokenBucket bucket = bucket(2, 60);
        bucket.tryAcquire();
        bucket.tryAcquire();

        advance(600_000); // 10분 — 600개가 채워질 시간

        assertEquals(2, bucket.available(), "오래 안 쓰다가 한꺼번에 몰아 보내면 상대가 또 거절한다");
    }

    @Test
    void 다음_토큰까지_남은_시간을_알려준다() {
        TokenBucket bucket = bucket(1, 60); // 1초에 1개
        bucket.tryAcquire();

        assertEquals(1_000, bucket.millisUntilNext(), 1, "호출부가 '언제 다시 되는지' 알려줄 수 있어야 한다");

        advance(400);
        assertEquals(600, bucket.millisUntilNext(), 1);

        advance(600);
        assertEquals(0, bucket.millisUntilNext());
    }

    @Test
    void 잠깐_몰리는_호출은_쌓인_토큰으로_받아_준다() {
        // 창을 끊어 세는 방식과 다른 점 — 평소에 안 쓰면 그만큼 몰아 쓸 수 있다
        TokenBucket bucket = bucket(5, 60);
        advance(10_000);

        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (bucket.tryAcquire()) {
                allowed++;
            }
        }

        assertEquals(5, allowed, "버킷 크기만큼은 한꺼번에 통과한다");
    }

    @Test
    void 시계가_뒤로_가도_토큰을_깎지_않는다() {
        TokenBucket bucket = bucket(2, 60);
        bucket.tryAcquire();

        now.addAndGet(-5_000); // 시간 동기화로 시계가 뒤로 간 상황

        assertEquals(1, bucket.available(), "남은 토큰이 음수가 되면 영원히 막힌다");
        assertTrue(bucket.tryAcquire());
    }

    @Test
    void 잘못된_설정은_만들_때_막는다() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 60));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1, 60));
    }
}
