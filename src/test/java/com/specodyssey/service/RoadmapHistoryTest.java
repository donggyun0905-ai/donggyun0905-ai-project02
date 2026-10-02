package com.specodyssey.service;

import com.specodyssey.dto.RoadmapStepDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadmapHistoryTest {

    private static RoadmapStepDto step(long id, int order, boolean completed, LocalDateTime at) {
        RoadmapStepDto s = new RoadmapStepDto();
        s.setId(id);
        s.setStepOrder(order);
        s.setCompleted(completed);
        s.setCompletedAt(at);
        return s;
    }

    private static List<Long> ids(List<RoadmapStepDto> steps) {
        return steps.stream().map(RoadmapStepDto::getId).collect(Collectors.toList());
    }

    @Test
    void 끝낸_단계가_기본_개수_이하면_전부_보여준다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            steps.add(step(i, i, i <= 3, LocalDateTime.of(2026, 1, i, 0, 0)));
        }
        RoadmapHistory h = RoadmapHistory.of(steps, 3, false);
        assertEquals(ids(steps), ids(h.getVisible()));
        assertEquals(0, h.getHiddenCompleted());
        assertFalse(h.isCollapsible(3));
    }

    @Test
    void 끝낸_단계는_최근_것만_남기고_안_끝낸_단계는_하나도_숨기지_않는다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        // 1~6 끝냄(완료 시각이 id 순서와 반대: 6이 가장 오래됨), 7~8 안 끝냄
        for (int i = 1; i <= 6; i++) {
            steps.add(step(i, i, true, LocalDateTime.of(2026, 1, 10 - i, 0, 0)));
        }
        steps.add(step(7, 7, false, null));
        steps.add(step(8, 8, false, null));

        RoadmapHistory h = RoadmapHistory.of(steps, 3, false);

        // 최근 3개 = 완료 시각이 가장 늦은 1,2,3 — 원래 순서(step_order)는 그대로 유지된다
        assertEquals(List.of(1L, 2L, 3L, 7L, 8L), ids(h.getVisible()));
        assertEquals(3, h.getHiddenCompleted());
        assertTrue(h.isCollapsible(3));
    }

    @Test
    void 전체_보기를_고르면_전부_보여주고_완료_시각이_없는_단계는_가장_오래된_것으로_친다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        steps.add(step(1, 1, true, null)); // 승계로 시각 없이 끝난 단계
        steps.add(step(2, 2, true, LocalDateTime.of(2026, 1, 1, 0, 0)));
        steps.add(step(3, 3, true, LocalDateTime.of(2026, 1, 2, 0, 0)));

        assertEquals(List.of(2L, 3L), ids(RoadmapHistory.of(steps, 2, false).getVisible()));
        assertEquals(List.of(1L, 2L, 3L), ids(RoadmapHistory.of(steps, 2, true).getVisible()));
        assertEquals(0, RoadmapHistory.of(steps, 2, true).getHiddenCompleted());
    }
}
