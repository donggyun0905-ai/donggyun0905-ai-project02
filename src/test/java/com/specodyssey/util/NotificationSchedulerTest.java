package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NotificationScheduler 다음 실행까지 남은 시간 계산 단위테스트.
 */
class NotificationSchedulerTest {

    @Test
    void millisUntil_오늘_그_시각_전이면_오늘까지() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 22, 30);
        assertEquals(Duration.ofMinutes(30).toMillis(), NotificationScheduler.millisUntil(now, LocalTime.of(23, 0)));
    }

    @Test
    void millisUntil_이미_지났거나_딱_그_시각이면_내일까지() {
        LocalDateTime past = LocalDateTime.of(2026, 10, 6, 9, 30);
        assertEquals(Duration.ofHours(23).plusMinutes(30).toMillis(), NotificationScheduler.millisUntil(past, LocalTime.of(9, 0)));
        LocalDateTime exact = LocalDateTime.of(2026, 10, 6, 9, 0);
        assertEquals(Duration.ofDays(1).toMillis(), NotificationScheduler.millisUntil(exact, LocalTime.of(9, 0)));
    }
}
