package com.specodyssey.service;

import com.specodyssey.dto.SpecScoreHistoryDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-84 면접관 뷰 성장 잠재력 그래프 — 주·월·년 묶음, 증감, 세로축 범위 단위테스트.
 */
class GrowthChartTest {

    @Test
    void 기록이_없으면_null() {
        assertNull(GrowthChart.build(List.of()));
        assertNull(GrowthChart.build(null));
    }

    @Test
    void 기간마다_마지막_값이_막대가_되고_직전_기간과_비교한다() {
        // 9/27(일) 40 → 9/28(월) 42 → 9/30(수) 45 → 10/5(월) 50
        List<SpecScoreHistoryDto> rows = List.of(
                row("2026-09-27", "40"), row("2026-09-28", "42"), row("2026-09-30", "45"), row("2026-10-05", "50"));
        GrowthChart.Chart chart = GrowthChart.build(rows);

        GrowthChart.Period week = chart.periods().get(0);
        assertEquals("week", week.key());
        assertEquals(List.of("9/21주", "9/28주", "10/5주"), week.bars().stream().map(GrowthChart.Bar::label).toList());
        assertEquals(List.of("40", "45", "50"), week.bars().stream().map(GrowthChart.Bar::scoreText).toList());
        assertEquals(List.of("", "+5", "+5"), week.bars().stream().map(GrowthChart.Bar::deltaText).toList());
        assertEquals("최근 3주 동안 +10점", week.changeText());

        GrowthChart.Period month = chart.periods().get(1);
        assertEquals(List.of("9월", "10월"), month.bars().stream().map(GrowthChart.Bar::label).toList());
        assertEquals(List.of("45", "50"), month.bars().stream().map(GrowthChart.Bar::scoreText).toList());
        // 첫 달은 직전 달이 없어 그 달 안에서 오른 만큼(40 → 45)
        assertEquals(List.of("+5", "+5"), month.bars().stream().map(GrowthChart.Bar::deltaText).toList());

        GrowthChart.Period year = chart.periods().get(2);
        assertEquals(1, year.bars().size());
        assertEquals("2026년 동안 +10점", year.changeText());
        assertTrue(chart.summaryText().contains("8일 동안 40점 → 50점 (+10점)"));
    }

    @Test
    void 세로축은_0이_아니라_기록_근처만_잘라서_변화가_보인다() {
        GrowthChart.Chart chart = GrowthChart.build(List.of(row("2026-08-03", "42.46"), row("2026-10-07", "55.46")));
        assertEquals(30, chart.axisMin());
        assertEquals(70, chart.axisMax());
        List<GrowthChart.Bar> months = chart.periods().get(1).bars();
        assertTrue(months.get(months.size() - 1).heightPercent() - months.get(0).heightPercent() >= 30,
                "13점 차이가 막대 높이로 크게 드러나야 한다");
    }

    @Test
    void 초반_점수가_낮으면_세로축이_그만큼_내려가_잘리지_않는다() {
        GrowthChart.Chart chart = GrowthChart.build(List.of(
                row("2026-06-01", "12"), row("2026-07-01", "28"), row("2026-10-07", "55")));
        assertEquals(0, chart.axisMin());
        assertEquals(60, chart.axisMax());
        List<GrowthChart.Bar> months = chart.periods().get(1).bars();
        assertEquals("12", months.get(0).scoreText());
        assertEquals(20, months.get(0).heightPercent()); // 12 / 60
        assertEquals(47, months.get(1).heightPercent()); // 28 / 60
    }

    @Test
    void 떨어지면_down으로_표시한다() {
        GrowthChart.Chart chart = GrowthChart.build(List.of(row("2026-08-31", "60"), row("2026-09-01", "55")));
        GrowthChart.Period month = chart.periods().get(1);
        assertTrue(month.bars().get(1).down());
        assertEquals("-5", month.bars().get(1).deltaText());
        assertTrue(month.down());
    }

    @Test
    void 주는_최근_12개만_보여주되_첫_막대도_직전_주와_비교한다() {
        List<SpecScoreHistoryDto> rows = new ArrayList<>();
        LocalDate monday = LocalDate.parse("2026-06-01");
        for (int i = 0; i < 20; i++) {
            rows.add(row(monday.plusWeeks(i).toString(), String.valueOf(30 + i)));
        }
        GrowthChart.Period week = GrowthChart.build(rows).periods().get(0);
        assertEquals(GrowthChart.MAX_WEEKS, week.bars().size());
        assertEquals("+1", week.bars().get(0).deltaText());
        assertEquals("최근 12주 동안 +11점", week.changeText());
    }

    @Test
    void 해가_바뀌면_월_이름에_연도를_붙인다() {
        GrowthChart.Chart chart = GrowthChart.build(List.of(row("2025-12-15", "30"), row("2026-01-10", "35")));
        assertEquals(List.of("25.12월", "26.1월"),
                chart.periods().get(1).bars().stream().map(GrowthChart.Bar::label).toList());
        assertEquals(2, chart.periods().get(2).bars().size());
    }

    private static SpecScoreHistoryDto row(String date, String score) {
        SpecScoreHistoryDto h = new SpecScoreHistoryDto();
        h.setSnapshotDate(LocalDate.parse(date));
        h.setCompletenessScore(new BigDecimal(score));
        return h;
    }
}
