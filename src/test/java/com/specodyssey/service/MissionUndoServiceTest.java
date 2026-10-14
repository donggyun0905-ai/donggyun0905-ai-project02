package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 오늘의 미션 완료·실패 취소 — 연속 기록 되돌리기 규칙 단위테스트 (DB 불필요).
 */
class MissionUndoServiceTest {

    @Test
    void 오늘_몫_하루를_뺀다() {
        assertEquals(3, MissionUndoService.revertedStreak(4));
        assertEquals(0, MissionUndoService.revertedStreak(1));
    }

    @Test
    void 음수가_되지_않는다() {
        assertEquals(0, MissionUndoService.revertedStreak(0));
    }

    @Test
    void 다_끝낸_날인지는_배정_수와_완료_수로_본다() {
        // 취소 전 3/3이면 오늘 연속 기록을 되돌리고, 이미 2/3이었으면 되돌릴 게 없다
        assertTrue(MissionStreakService.isDayDone(new int[]{3, 3}));
        assertFalse(MissionStreakService.isDayDone(new int[]{3, 2}));
        assertFalse(MissionStreakService.isDayDone(null));
    }
}
