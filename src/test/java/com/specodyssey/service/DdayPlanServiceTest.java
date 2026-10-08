package com.specodyssey.service;

import com.specodyssey.dto.RoadmapStepDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-day 계획의 후보 고르기 규칙 (2026-10-08). DB 없이 "지금 할 수 있는 단계인가"만 본다.
 *
 * 로드맵은 앞 티어를 끝내야 다음 티어가 열리는 계단식 잠금이다. 잠긴 단계를 추천하면
 * "할 수 없는 일"을 알려 주는 셈이라, 후보에서 빼는 것이 이 기능의 전제다.
 */
class DdayPlanServiceTest {

    private final RoadmapProgressCalculator calculator = new RoadmapProgressCalculator();

    private static RoadmapStepDto step(long id, String tier, boolean completed) {
        RoadmapStepDto step = new RoadmapStepDto();
        step.setId(id);
        step.setStepType("SKILL");
        step.setTier(tier);
        step.setCompleted(completed);
        return step;
    }

    @Test
    void 입문은_항상_열려_있어_후보가_된다() {
        RoadmapProgress progress = calculator.computeProgress(List.of(step(1, "ENTRY", false)));

        assertTrue(DdayPlanService.isDoableNow(progress, "ENTRY"));
    }

    @Test
    void 앞_티어가_안_끝났으면_뒤_티어는_후보에서_뺀다() {
        // 입문이 미완료 — 핵심은 아직 잠겨 있다
        RoadmapProgress progress = calculator.computeProgress(List.of(
                step(1, "ENTRY", false),
                step(2, "CORE", false)));

        assertTrue(DdayPlanService.isDoableNow(progress, "ENTRY"));
        assertFalse(DdayPlanService.isDoableNow(progress, "CORE"), "할 수 없는 일을 추천하면 안 된다");
    }

    @Test
    void 앞_티어를_끝내면_뒤_티어도_후보가_된다() {
        RoadmapProgress progress = calculator.computeProgress(List.of(
                step(1, "ENTRY", true),
                step(2, "CORE", false)));

        assertTrue(DdayPlanService.isDoableNow(progress, "CORE"));
    }

    @Test
    void 복습처럼_사다리에_없는_티어는_언제든_할_수_있다() {
        // 입문이 미완료여도 복습은 이미 익힌 것을 다시 보는 것이라 잠기지 않는다
        RoadmapProgress progress = calculator.computeProgress(List.of(step(1, "ENTRY", false)));

        assertTrue(DdayPlanService.isDoableNow(progress, "REVIEW"));
        assertTrue(DdayPlanService.isDoableNow(progress, null), "티어가 없는 단계도 막지 않는다");
        assertTrue(DdayPlanService.isDoableNow(progress, "새로운티어"), "모르는 티어 때문에 계획이 비면 안 된다");
    }
}
