package com.specodyssey.service;

import java.util.List;

/** 로드맵 전체 진행도 — 티어별 진행도와 "지금 할 일" 티어 판단. RoadmapService 안에 있던 것을 그대로 꺼냈다. */
public final class RoadmapProgress {
    private final List<TierProgress> tiers;

    public RoadmapProgress(List<TierProgress> tiers) {
        this.tiers = tiers;
    }

    public List<TierProgress> getTiers() {
        return tiers;
    }

    public TierProgress getTier(String tierName) {
        return tiers.stream().filter(t -> t.getTier().equals(tierName)).findFirst().orElse(null);
    }

    // 화면의 "지금 할 일" 섹션 — 열려 있고, 비어 있지 않고, 아직 다 안 끝난 첫 번째 티어.
    public TierProgress getCurrentTier() {
        return tiers.stream()
                .filter(t -> t.isUnlocked() && !t.isEmptyTier() && !t.isComplete())
                .findFirst().orElse(null);
    }

    // "다음 단계 미리보기" 섹션 — 아직 잠겨 있고 비어 있지 않은 첫 번째 티어.
    public TierProgress getNextLockedTier() {
        return tiers.stream()
                .filter(t -> !t.isUnlocked() && !t.isEmptyTier())
                .findFirst().orElse(null);
    }

    // 지금 할 일도, 다음에 풀릴 잠긴 단계도 없다 — 부족 기술을 전부 채웠거나 애초에 없었던 것.
    public boolean isJourneyComplete() {
        return getCurrentTier() == null && getNextLockedTier() == null;
    }
}
