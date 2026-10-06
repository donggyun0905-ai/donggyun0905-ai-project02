package com.specodyssey.service.simulation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 하루 요약 — 패널 한 줄 문구와 "화면 따라가기"가 갈 화면 */
class SimulationReportTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 14);

    @Test
    void 로드맵_단계를_끝낸_날은_로드맵으로_따라간다() {
        SimulationService.DayReport r = SimulationService.report(DAY, true, 2, 1, 86, 900, "취준생", false, 77L, 1);
        assertEquals("/roadmap", r.page());
        assertEquals(77L, r.stepId());
        assertEquals("08-14 · 미션 2/3 · 로드맵 1단계 완료 · +86점", r.text());
    }

    @Test
    void 미션만_한_날은_대시보드() {
        SimulationService.DayReport r = SimulationService.report(DAY, true, 3, 0, 18, 500, "취준생", true, null, 0);
        assertEquals("/dashboard", r.page());
        assertEquals("08-14 · 미션 3/3 · +18점 · 취준생 달성!", r.text());
    }

    @Test
    void 쉬는_날() {
        SimulationService.DayReport r = SimulationService.report(DAY, false, 0, 0, 0, 500, "취준생", false, null, 0);
        assertEquals("08-14 · 쉬는 날", r.text());
        assertEquals("/dashboard", r.page());
    }
}
