package com.specodyssey.service;

import com.specodyssey.service.DdayPlanner.Candidate;
import com.specodyssey.service.DdayPlanner.Plan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-day 계획 — 0/1 배낭 (2026-10-08). DB 없이 알고리즘만 본다.
 *
 * D-day 화면이 "며칠 남았다"만 보여 주던 것을 "이 기간에 점수를 가장 많이 올리는 조합"까지 보여 주게 했다.
 * 여기서 고정하는 것: 실제로 최적해를 찾는지(그리디보다 나은지), 용량을 넘기지 않는지, 상한을 지키는지.
 */
class DdayPlannerTest {

    private static long nextId = 1;

    private static String labels(Plan plan) {
        return plan.picked().stream().map(Candidate::label).collect(Collectors.joining(", "));
    }

    private static Candidate item(int days, int points) {
        return new Candidate(nextId++, "단계 " + days + "일 " + points + "점", "SKILL", "ENTRY", days, points);
    }

    @Test
    void 남은_기간에_점수_합이_가장_큰_조합을_고른다() {
        // 그리디(점수/일수 높은 것부터)는 6일 100점을 담고 1일을 버려 100점에서 멈춘다.
        // DP는 4일+3일을 담아 115점을 찾는다 — 이 차이가 DP를 쓰는 이유다.
        Plan plan = DdayPlanner.plan(List.of(item(6, 100), item(4, 60), item(3, 55)), 7);

        assertEquals(115, plan.totalPoints(), "실제로 고른 것: " + labels(plan));
        assertEquals(7, plan.usedDays());
        assertEquals(2, plan.picked().size());
    }

    @Test
    void 용량을_절대_넘기지_않는다() {
        Plan plan = DdayPlanner.plan(List.of(item(5, 50), item(5, 50), item(5, 50)), 12);

        assertTrue(plan.usedDays() <= 12, "쓴 일수: " + plan.usedDays());
        assertEquals(100, plan.totalPoints(), "두 개까지만 들어간다");
        assertEquals(2, plan.leftoverDays());
    }

    @Test
    void 남은_기간_안에_끝낼_수_없는_단계는_아예_넣지_않는다() {
        // 자격증(21일)만 있고 5일 남았다
        Plan plan = DdayPlanner.plan(List.of(item(21, 300)), 5);

        assertTrue(plan.isEmpty(), "할 수 없는 일을 추천하면 안 된다");
        assertEquals(0, plan.totalPoints());
        assertEquals(5, plan.availableDays());
    }

    @Test
    void 남은_기간이_넉넉하면_전부_담는다() {
        Plan plan = DdayPlanner.plan(List.of(item(3, 30), item(7, 70), item(1, 10)), 100);

        assertEquals(3, plan.picked().size());
        assertEquals(110, plan.totalPoints());
        assertEquals(11, plan.usedDays());
        assertEquals(89, plan.leftoverDays());
    }

    @Test
    void D_day가_지났거나_오늘이면_계획을_세우지_않는다() {
        assertTrue(DdayPlanner.plan(List.of(item(1, 10)), 0).isEmpty());
        assertTrue(DdayPlanner.plan(List.of(item(1, 10)), -5).isEmpty(), "지난 D-day");
        assertEquals(0, DdayPlanner.plan(List.of(item(1, 10)), -5).availableDays());
    }

    @Test
    void 할_수_있는_단계가_없으면_빈_계획이다() {
        assertTrue(DdayPlanner.plan(List.of(), 30).isEmpty());
        assertTrue(DdayPlanner.plan(null, 30).isEmpty());
    }

    @Test
    void 점수가_없는_단계는_담지_않는다() {
        // 가치가 0이면 용량만 쓴다
        Plan plan = DdayPlanner.plan(List.of(item(3, 0), item(3, 30)), 6);

        assertEquals(1, plan.picked().size());
        assertEquals(30, plan.totalPoints());
    }

    @Test
    void 소요_일수가_잘못된_값이면_하루로_본다() {
        Plan plan = DdayPlanner.plan(List.of(new Candidate(99L, "이상한 단계", "SKILL", "ENTRY", 0, 40)), 3);

        assertEquals(40, plan.totalPoints(), "0일로 두면 무한히 담긴다");
        assertEquals(1, plan.usedDays());
    }

    @Test
    void 화면_순서는_짧게_끝나는_것부터다() {
        Plan plan = DdayPlanner.plan(List.of(item(7, 70), item(1, 10), item(3, 30)), 11);

        List<Integer> days = plan.picked().stream().map(Candidate::effortDays).collect(Collectors.toList());
        assertEquals(List.of(1, 3, 7), days, "오늘 뭐부터 할지 순서로 읽혀야 한다");
    }

    @Test
    void 계획_기간은_1년으로_끊는다() {
        Plan plan = DdayPlanner.plan(List.of(item(3, 30)), 5_000);

        assertEquals(DdayPlanner.MAX_DAYS, plan.availableDays(), "표가 무한히 커지면 안 된다");
    }

    @Test
    void 후보가_아주_많아도_상한만큼만_넣고_좋은_것이_남는다() {
        List<Candidate> many = new ArrayList<>();
        many.add(item(1, 999)); // 가장 밀도 높은 것
        for (int i = 0; i < 300; i++) {
            many.add(item(5, 5)); // 쓸모없는 후보 300개
        }

        Plan plan = DdayPlanner.plan(many, 10);

        assertTrue(plan.picked().size() <= DdayPlanner.MAX_CANDIDATES);
        assertTrue(plan.picked().stream().anyMatch(c -> c.points() == 999),
                "밀도 높은 것부터 자르므로 좋은 후보는 살아남아야 한다");
    }

    @Test
    void 소요_일수는_단계_종류와_티어로_정해진다() {
        assertEquals(3, DdayPlanner.effortDays("SKILL", "ENTRY"));
        assertEquals(7, DdayPlanner.effortDays("SKILL", "CORE"));
        assertEquals(5, DdayPlanner.effortDays("SKILL", "ADVANCED"));
        assertEquals(4, DdayPlanner.effortDays("SKILL", "EXPERT"));
        assertEquals(21, DdayPlanner.effortDays("CERT", "ENTRY"), "접수·시험 일정이 끼어 가장 길다");
        assertEquals(10, DdayPlanner.effortDays("PROJECT", "ENTRY"));
        assertEquals(1, DdayPlanner.effortDays("REVIEW", "REVIEW"));
    }

    @Test
    void 모르는_단계_종류나_티어여도_계획이_멈추지_않는다() {
        assertEquals(3, DdayPlanner.effortDays("새로운종류", null), "기본값으로 떨어진다");
        assertEquals(3, DdayPlanner.effortDays("SKILL", "새로운티어"), "모르는 티어는 입문으로 본다");
        assertEquals(3, DdayPlanner.effortDays(null, null));
        assertEquals(3, DdayPlanner.effortDays("SKILL", "  "));
    }

    @Test
    void 화면은_picked가_빈지로_판단한다() {
        // empty는 EL 예약어라 ${plan.empty}를 쓸 수 없다 — 화면은 picked를 본다
        assertTrue(DdayPlanner.plan(List.of(), 10).picked().isEmpty());
        assertFalse(DdayPlanner.plan(List.of(item(1, 10)), 10).picked().isEmpty());
    }
}
