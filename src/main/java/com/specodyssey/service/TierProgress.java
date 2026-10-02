package com.specodyssey.service;


/** 티어 하나의 진행도(전체·완료 수, 퍼센트, 잠금 여부). RoadmapService 안에 있던 것을 그대로 꺼냈다. */
public final class TierProgress {
    private final String tier;
    private final int total;
    private final int done;
    private final int percent;
    private final boolean unlocked;

    public TierProgress(String tier, int total, int done, int percent, boolean unlocked) {
        this.tier = tier;
        this.total = total;
        this.done = done;
        this.percent = percent;
        this.unlocked = unlocked;
    }

    public String getTier() {
        return tier;
    }

    public int getTotal() {
        return total;
    }

    public int getDone() {
        return done;
    }

    public int getPercent() {
        return percent;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    // "empty"는 EL 예약어라 JSP에서 t.empty로 접근하면 태그 검증 단계에서 컴파일 자체가
    // 깨진다(2026-09-29 실제 배포 중 발견) — isEmptyTier로 이름을 피해서 짓는다.
    public boolean isEmptyTier() {
        return total == 0;
    }

    public boolean isComplete() {
        return total > 0 && done == total;
    }
}
