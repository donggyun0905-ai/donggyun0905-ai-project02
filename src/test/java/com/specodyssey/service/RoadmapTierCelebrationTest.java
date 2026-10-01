package com.specodyssey.service;

import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 티어 돌파 환영 모달 신호(findNewlyCompletedTier) — DB 없이 진행도 계산만으로 검증한다. */
class RoadmapTierCelebrationTest {

    private final RoadmapService roadmapService = new RoadmapService(new ProjectIdeaService(new StubLlmClient()));

    private List<RoadmapStepDto> steps(boolean[] entryDone, boolean[] coreDone) {
        List<RoadmapStepDto> steps = new ArrayList<>();
        for (boolean done : entryDone) {
            steps.add(step("ENTRY", done));
        }
        for (boolean done : coreDone) {
            steps.add(step("CORE", done));
        }
        return steps;
    }

    private RoadmapStepDto step(String tier, boolean done) {
        RoadmapStepDto step = new RoadmapStepDto();
        step.setTier(tier);
        step.setCompleted(done);
        return step;
    }

    @Test
    void 마지막_단계를_완료해_티어가_100퍼센트가_되면_그_티어를_돌려준다() {
        var before = roadmapService.computeProgress(steps(new boolean[]{true, false}, new boolean[]{false}));
        var after = roadmapService.computeProgress(steps(new boolean[]{true, true}, new boolean[]{false}));

        assertEquals("ENTRY", roadmapService.findNewlyCompletedTier(before, after).getTier());
    }

    @Test
    void 티어가_아직_덜_끝났거나_이미_끝나_있었으면_null이다() {
        var half = roadmapService.computeProgress(steps(new boolean[]{true, false}, new boolean[]{false}));
        var almost = roadmapService.computeProgress(steps(new boolean[]{false, false}, new boolean[]{false}));
        var done = roadmapService.computeProgress(steps(new boolean[]{true, true}, new boolean[]{false}));

        assertNull(roadmapService.findNewlyCompletedTier(almost, half));
        assertNull(roadmapService.findNewlyCompletedTier(done, done), "새로고침처럼 변화가 없으면 뜨면 안 된다");
        assertNull(roadmapService.findNewlyCompletedTier(null, done));
    }
}
