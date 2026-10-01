package com.specodyssey.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 무차별 대입 방지 — 같은 (아이디, IP)에서 연속으로 MAX_FAILURES번 틀리면 LOCK_DURATION 동안 막는다.
 * 서버 메모리에만 두는 간단한 방식이라 서버를 재시작하면 초기화되고, 여러 서버로 늘리면 공유 저장소가 필요하다.
 * 성공하면 그 키의 실패 기록을 지운다. 시각은 호출하는 쪽이 넘겨서 시간에 의존하지 않고 테스트할 수 있다.
 */
public final class LoginThrottle {

    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final int MAX_TRACKED_KEYS = 10_000;

    private static final class Entry {
        int failures;
        long lockedUntilMillis;
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public static String key(String loginId, String ip) {
        return (loginId == null ? "" : loginId.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    /** 지금 막혀 있으면 남은 시간(초), 아니면 0 */
    public long secondsLocked(String key, long nowMillis) {
        Entry e = entries.get(key);
        if (e == null) {
            return 0;
        }
        synchronized (e) {
            long remaining = e.lockedUntilMillis - nowMillis;
            return remaining > 0 ? (remaining + 999) / 1000 : 0;
        }
    }

    public void recordFailure(String key, long nowMillis) {
        if (entries.size() > MAX_TRACKED_KEYS) {
            entries.clear(); // 아이디를 마구 바꿔 보내 메모리를 채우는 공격 대비 — 가장 단순한 상한
        }
        Entry e = entries.computeIfAbsent(key, k -> new Entry());
        synchronized (e) {
            if (e.lockedUntilMillis != 0 && e.lockedUntilMillis <= nowMillis) {
                e.failures = 0; // 잠금이 끝났으면 처음부터 다시 센다
                e.lockedUntilMillis = 0;
            }
            e.failures++;
            if (e.failures >= MAX_FAILURES) {
                e.lockedUntilMillis = nowMillis + LOCK_DURATION.toMillis();
            }
        }
    }

    public void recordSuccess(String key) {
        entries.remove(key);
    }
}
