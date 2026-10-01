package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MissionStreakService 스트릭 규칙 단위테스트 (DB 불필요).
 */
class MissionStreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    @Test
    void 배정된_문제를_모두_끝내야_수행한_날() {
        assertTrue(MissionStreakService.isDayDone(new int[]{3, 3}));
        assertFalse(MissionStreakService.isDayDone(new int[]{3, 2}));
        assertFalse(MissionStreakService.isDayDone(new int[]{0, 0}), "배정이 없던 날은 수행한 날이 아니다");
        assertFalse(MissionStreakService.isDayDone(null));
        // 문제 풀이 모자라 2개만 배정된 날은 2개를 다 끝내면 수행
        assertTrue(MissionStreakService.isDayDone(new int[]{2, 2}));
    }

    @Test
    void 어제_이어서면_1_늘고_아니면_1부터() {
        assertEquals(5, MissionStreakService.nextStreak(4, TODAY.minusDays(1), TODAY));
        assertEquals(1, MissionStreakService.nextStreak(4, TODAY.minusDays(2), TODAY));
        assertEquals(1, MissionStreakService.nextStreak(0, null, TODAY));
    }

    @Test
    void 화면_스트릭은_오늘이나_어제_수행했을_때만_이어진다() {
        assertEquals(4, MissionStreakService.displayStreak(4, TODAY, TODAY));
        assertEquals(4, MissionStreakService.displayStreak(4, TODAY.minusDays(1), TODAY), "오늘은 아직 안 끝냈어도 유지");
        assertEquals(0, MissionStreakService.displayStreak(4, TODAY.minusDays(2), TODAY), "하루 비면 끊김");
        assertEquals(0, MissionStreakService.displayStreak(0, null, TODAY));
    }
}
