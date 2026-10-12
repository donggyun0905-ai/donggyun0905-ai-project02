package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * 오늘의 미션 새로 불러오기 — 등급 레벨 범위 보정 단위테스트 (DB 불필요).
 */
class MissionRefreshServiceTest {

    @Test
    void 등급_범위는_그대로_쓴다() {
        assertArrayEquals(new int[]{3, 4}, MissionRefreshService.levelRange(new int[]{3, 4}));
    }

    @Test
    void 등급_정보가_없으면_Lv0부터_1() {
        assertArrayEquals(new int[]{0, 1}, MissionRefreshService.levelRange(null));
    }

    @Test
    void 범위를_0부터_5_안으로_맞춘다() {
        assertArrayEquals(new int[]{0, 5}, MissionRefreshService.levelRange(new int[]{-2, 9}));
        assertArrayEquals(new int[]{5, 5}, MissionRefreshService.levelRange(new int[]{7, 8}));
    }

    @Test
    void 최대가_최소보다_작으면_최소로_맞춘다() {
        assertArrayEquals(new int[]{4, 4}, MissionRefreshService.levelRange(new int[]{4, 2}));
    }
}
