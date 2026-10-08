package com.specodyssey.util;

import java.util.function.LongSupplier;

/**
 * 외부 API 호출 횟수 제한 — 토큰 버킷 (2026-10-08).
 * 관련 요구사항: FR-111 AI API 실패 대응 · FR-112 외부 데이터 조회 실패 대응
 *
 * Groq 무료 한도는 분당 요청 수로 걸린다. 지금까지는 한도를 넘으면 429를 받고 재시도 정책이 20·40·60초를
 * 기다렸다 — <b>이미 거절당한 뒤에</b> 기다리는 것이라 그 시간만큼 화면이나 배치가 멈춘다.
 * 토큰 버킷은 <b>보내기 전에</b> 막아서, 어차피 거절될 호출에 시간을 쓰지 않고 바로 대체 동작으로 넘긴다.
 *
 * 토큰을 일정 속도로 채우고 호출마다 하나씩 쓴다. 빈 동안은 거절한다. 창(window)을 끊어 세는 방식과 달리
 * 창이 바뀌는 순간에 두 배가 몰리는 문제가 없고, 잠깐 몰리는 호출은 쌓인 토큰으로 받아 준다.
 *
 * 시간은 주입받는다 — 테스트에서 sleep 없이 시간을 앞으로 돌리기 위해서다.
 * 스레드 여러 개(요청 스레드 + 스케줄러)가 같이 쓰므로 모든 상태 변경은 synchronized 안에서 한다.
 */
public final class TokenBucket {

    private final int capacity;
    private final double refillPerMilli;
    private final LongSupplier clock;

    private double tokens;
    private long lastRefillAt;

    /**
     * @param capacity       한꺼번에 몰아 쓸 수 있는 최대 호출 수(버킷 크기)
     * @param refillPerMinute 분당 채워지는 토큰 수 = 평균 허용 호출 수
     */
    public TokenBucket(int capacity, double refillPerMinute) {
        this(capacity, refillPerMinute, System::currentTimeMillis);
    }

    TokenBucket(int capacity, double refillPerMinute, LongSupplier clock) {
        if (capacity <= 0 || refillPerMinute <= 0) {
            throw new IllegalArgumentException("버킷 크기와 채우는 속도는 0보다 커야 합니다");
        }
        this.capacity = capacity;
        this.refillPerMilli = refillPerMinute / 60_000.0;
        this.clock = clock;
        this.tokens = capacity; // 처음에는 꽉 차 있다 — 서버를 켠 직후 첫 호출이 막히면 안 된다
        this.lastRefillAt = clock.getAsLong();
    }

    /** 호출해도 되는지. 된다면 토큰 하나를 쓴다. */
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens < 1.0) {
            return false;
        }
        tokens -= 1.0;
        return true;
    }

    /** 지금 남은 토큰 수(소수점 버림) — 로그·관리자 화면용 */
    public synchronized int available() {
        refill();
        return (int) tokens;
    }

    /** 다음 토큰이 채워질 때까지 남은 밀리초. 이미 있으면 0 — 호출부가 "언제 다시 되는지" 알려줄 때 쓴다. */
    public synchronized long millisUntilNext() {
        refill();
        if (tokens >= 1.0) {
            return 0L;
        }
        return (long) Math.ceil((1.0 - tokens) / refillPerMilli);
    }

    private void refill() {
        long now = clock.getAsLong();
        long elapsed = now - lastRefillAt;
        if (elapsed <= 0) {
            return; // 시계가 뒤로 가도(시간 동기화) 토큰을 깎지 않는다
        }
        lastRefillAt = now;
        tokens = Math.min(capacity, tokens + elapsed * refillPerMilli);
    }
}
