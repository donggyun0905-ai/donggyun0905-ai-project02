package com.specodyssey.service;

import com.specodyssey.dto.RoadmapStepDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 티어 잠금 규칙 — 앞 티어를 끝내야 다음 티어가 열리지만, 한 번 열린 티어는 나중에 앞 티어에
 * 단계가 덧붙어도("길 더 만들기") 다시 잠기지 않는다(사용자 결정, 2026-10-06). DB 없음.
 */
class RoadmapProgressCalculatorTest {

    private final RoadmapProgressCalculator calculator = new RoadmapProgressCalculator();
    private long nextId = 1;

    private RoadmapStepDto step(String tier, boolean completed) {
        RoadmapStepDto s = new RoadmapStepDto();
        s.setId(nextId++);
        s.setStepType("SKILL");
        s.setTier(tier);
        s.setCompleted(completed);
        return s;
    }

    private boolean unlocked(RoadmapProgress progress, String tier) {
        return progress.getTier(tier).isUnlocked();
    }

    @Test
    void 입문은_항상_열려_있고_끝내야_핵심이_열린다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        steps.add(step(RoadmapConstants.TIER_ENTRY, false));
        steps.add(step(RoadmapConstants.TIER_CORE, false));

        RoadmapProgress before = calculator.computeProgress(steps);
        assertTrue(unlocked(before, RoadmapConstants.TIER_ENTRY));
        assertFalse(unlocked(before, RoadmapConstants.TIER_CORE));

        steps.get(0).setCompleted(true);
        RoadmapProgress after = calculator.computeProgress(steps);
        assertTrue(unlocked(after, RoadmapConstants.TIER_CORE));
    }

    @Test
    void 나중에_붙은_입문_단계는_이미_열린_핵심을_다시_잠그지_않는다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        steps.add(step(RoadmapConstants.TIER_ENTRY, true));   // id 1 — 끝낸 입문
        steps.add(step(RoadmapConstants.TIER_CORE, false));   // id 2 — 열려 있던 핵심
        assertTrue(unlocked(calculator.computeProgress(steps), RoadmapConstants.TIER_CORE));

        // "길 더 만들기"로 새 기술이 들어와 입문에 미완료가 생긴다 — id가 더 크다(나중에 만들어졌다)
        steps.add(step(RoadmapConstants.TIER_ENTRY, false));  // id 3
        RoadmapProgress after = calculator.computeProgress(steps);

        assertTrue(unlocked(after, RoadmapConstants.TIER_CORE), "걷고 있던 핵심은 열린 상태로 남는다");
        assertEquals(2, after.getTier(RoadmapConstants.TIER_ENTRY).getTotal());
        assertEquals(1, after.getTier(RoadmapConstants.TIER_ENTRY).getDone());
    }

    @Test
    void 아직_열린_적_없는_티어는_앞_티어를_끝내야_열린다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        steps.add(step(RoadmapConstants.TIER_ENTRY, false));      // id 1 — 안 끝낸 입문
        steps.add(step(RoadmapConstants.TIER_CORE, false));       // id 2
        steps.add(step(RoadmapConstants.TIER_ADVANCED, false));   // id 3

        RoadmapProgress progress = calculator.computeProgress(steps);
        assertFalse(unlocked(progress, RoadmapConstants.TIER_CORE));
        assertFalse(unlocked(progress, RoadmapConstants.TIER_ADVANCED), "핵심이 잠겨 있으면 심화도 잠긴다");
    }

    @Test
    void 완료_취소하면_그_티어보다_먼저_만들어진_것만_보고_다시_잠긴다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        steps.add(step(RoadmapConstants.TIER_ENTRY, true));  // id 1
        steps.add(step(RoadmapConstants.TIER_CORE, false));  // id 2
        assertTrue(unlocked(calculator.computeProgress(steps), RoadmapConstants.TIER_CORE));

        steps.get(0).setCompleted(false); // 처음부터 있던 입문 단계를 완료 취소
        assertFalse(unlocked(calculator.computeProgress(steps), RoadmapConstants.TIER_CORE),
                "원래 있던 앞 단계를 되돌리면 다시 잠긴다");
    }
}
