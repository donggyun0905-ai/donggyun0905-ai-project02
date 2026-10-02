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

    @Test
    void 연속_보너스는_둘째_날부터_하루마다_늘다가_상한에_멈추고_7일_30일째에_더_붙는다() {
        assertEquals(0, MissionStreakService.bonusFor(1), "첫날은 없다");
        assertEquals(2, MissionStreakService.bonusFor(2));
        assertEquals(10, MissionStreakService.bonusFor(6));
        assertEquals(12 + 30, MissionStreakService.bonusFor(7), "7일째는 큰 보너스가 더 붙는다");
        assertEquals(20, MissionStreakService.bonusFor(11));
        assertEquals(20, MissionStreakService.bonusFor(20), "상한");
        assertEquals(20 + 100, MissionStreakService.bonusFor(30), "30일째");
        assertEquals(20, MissionStreakService.bonusFor(31));
    }
}
