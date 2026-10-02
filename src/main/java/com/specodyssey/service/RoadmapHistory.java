package com.specodyssey.service;

import com.specodyssey.dto.RoadmapStepDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 여정 화면에 보여줄 단계 고르기 — 로드맵은 복습이 계속 이어 붙어 끝없이 길어지므로, 끝낸 단계는 가장 최근 것만
 * 보여주고 나머지는 "더 보기"로 접는다. 아직 안 끝낸 단계는 하나도 숨기지 않고, 진행도 계산은 전체 단계로 따로 한다.
 */
public final class RoadmapHistory {

    /** 기본으로 보여주는 끝낸 단계 수 */
    public static final int DEFAULT_KEEP_COMPLETED = 12;

    private final List<RoadmapStepDto> visible;
    private final int hiddenCompleted;
    private final int totalCompleted;

    private RoadmapHistory(List<RoadmapStepDto> visible, int hiddenCompleted, int totalCompleted) {
        this.visible = visible;
        this.hiddenCompleted = hiddenCompleted;
        this.totalCompleted = totalCompleted;
    }

    /**
     * @param keepCompleted 끝낸 단계 중 보여줄 최근 개수. showAll이면 전부 보여준다.
     *        "최근"은 완료 시각 기준이고(없으면 가장 오래된 것으로 친다), 원래 순서는 그대로 유지한다.
     */
    public static RoadmapHistory of(List<RoadmapStepDto> steps, int keepCompleted, boolean showAll) {
        List<RoadmapStepDto> completed = new ArrayList<>();
        for (RoadmapStepDto step : steps) {
            if (step.isCompleted()) {
                completed.add(step);
            }
        }
        int total = completed.size();
        if (showAll || total <= keepCompleted) {
            return new RoadmapHistory(List.copyOf(steps), 0, total);
        }
        completed.sort(Comparator
                .comparing((RoadmapStepDto s) -> s.getCompletedAt() == null ? LocalDateTime.MIN : s.getCompletedAt())
                .reversed()
                .thenComparing(Comparator.comparing((RoadmapStepDto s) -> s.getStepOrder() == null ? 0 : s.getStepOrder()).reversed()));
        Set<Long> keep = new HashSet<>();
        for (int i = 0; i < keepCompleted; i++) {
            keep.add(completed.get(i).getId());
        }
        List<RoadmapStepDto> visible = new ArrayList<>();
        for (RoadmapStepDto step : steps) {
            if (!step.isCompleted() || keep.contains(step.getId())) {
                visible.add(step);
            }
        }
        return new RoadmapHistory(List.copyOf(visible), total - keepCompleted, total);
    }

    public List<RoadmapStepDto> getVisible() {
        return visible;
    }

    public int getHiddenCompleted() {
        return hiddenCompleted;
    }

    /** 접을 수 있는(끝낸 단계가 기본 개수보다 많은) 로드맵인지 */
    public boolean isCollapsible(int keepCompleted) {
        return totalCompleted > keepCompleted;
    }
}
