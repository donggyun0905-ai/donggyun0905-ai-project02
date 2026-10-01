package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DailyMissionService의 직무별 출제 비율·출처 라벨 규칙 단위테스트 (DB 불필요).
 */
class DailyMissionServiceTest {

    @Test
    void 데이터_직무는_SQL_2문제() {
        assertEquals(2, DailyMissionService.sqlQuota("DATA"));
    }

    @Test
    void 백엔드와_기획은_SQL_1문제() {
        assertEquals(1, DailyMissionService.sqlQuota("BACKEND"));
        assertEquals(1, DailyMissionService.sqlQuota("PM"));
    }

    @Test
    void 나머지_직무와_목표_없음은_알고리즘만() {
        assertEquals(0, DailyMissionService.sqlQuota("FRONTEND"));
        assertEquals(0, DailyMissionService.sqlQuota("DEVOPS"));
        assertEquals(0, DailyMissionService.sqlQuota("SECURITY"));
        assertEquals(0, DailyMissionService.sqlQuota(null));
    }

    @Test
    void SQL_할당량은_하루_문제_수를_넘지_않는다() {
        for (String category : new String[]{"DATA", "BACKEND", "PM", "FRONTEND", "DEVOPS", "SECURITY"}) {
            assertTrue(DailyMissionService.sqlQuota(category) <= DailyMissionService.DAILY_COUNT);
        }
    }

    @Test
    void 출처_라벨() {
        assertEquals("프로그래머스",
                DailyMissionService.sourceLabel("https://school.programmers.co.kr/learn/courses/30/lessons/59034"));
        assertEquals("백준", DailyMissionService.sourceLabel("https://www.acmicpc.net/problem/1000"));
        assertEquals("외부 링크", DailyMissionService.sourceLabel("https://example.com"));
        assertEquals("외부 링크", DailyMissionService.sourceLabel(null));
    }
}
