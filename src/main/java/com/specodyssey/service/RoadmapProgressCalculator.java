package com.specodyssey.service;

import com.specodyssey.dto.RoadmapStepDto;
import java.util.ArrayList;
import java.util.List;
import static com.specodyssey.service.RoadmapConstants.*;

/**
 * 단계 목록에서 티어별 진행도를 계산한다(FR-36). RoadmapService 안에 있던 계산 부분을 그대로 옮겼다.
 */
public class RoadmapProgressCalculator {

    // FR-36 진행도. target_level이 기본 EXPERT라 길이 계속 늘어나는 구조(db-design.md 설계 판단)라서
    // "전체 대비 %"는 분모가 계속 바뀌어 의미가 없다 — 그래서 티어별로 계산하고, 앞 티어를 다
    // 끝내야(또는 그 티어에 단계가 아예 없으면) 다음 티어가 풀리는 계단식 잠금으로 표현한다
    // (팀 합의, 2026-09-23 / ADVANCED·EXPERT 확장 2026-09-29). ENTRY는 항상 열려 있다.
    //
    // 한 번 열린 티어는 나중에 앞 티어에 단계가 덧붙어도 다시 잠기지 않는다(사용자 결정, 2026-10-06).
    // 다음 라운드 기술을 이어 붙이면 입문에 미완료가 새로 생기는데, 그때마다 걷고 있던 핵심·심화가
    // 통째로 잠기면 진도가 뒤로 가는 느낌이라 로드맵을 더 못 늘리고 있었다. 그래서 "앞 티어를 다
    // 끝냈는지"를 볼 때 이 티어보다 나중에 만들어진 앞 티어 단계는 빼고 본다 — 단계 id는 만든
    // 순서대로 커지므로 이 티어에서 가장 먼저 만들어진 단계의 id가 그 기준이 된다.
    // 한 라운드로 통째로 만든 로드맵에서는 앞 티어 id가 모두 더 작아서 예전과 똑같이 동작한다.
    public RoadmapProgress computeProgress(List<RoadmapStepDto> steps) {
        List<TierProgress> tiers = new ArrayList<>();
        for (int i = 0; i < SKILL_TIER_ORDER.size(); i++) {
            String tier = SKILL_TIER_ORDER.get(i);
            long total = steps.stream().filter(s -> tier.equals(s.getTier())).count();
            long done = steps.stream().filter(s -> tier.equals(s.getTier()) && s.isCompleted()).count();
            int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
            tiers.add(new TierProgress(tier, (int) total, (int) done, percent, isUnlocked(steps, i)));
        }
        return new RoadmapProgress(tiers);
    }

    private boolean isUnlocked(List<RoadmapStepDto> steps, int tierIndex) {
        if (tierIndex == 0) {
            return true; // 입문은 항상 열려 있다
        }
        String tier = SKILL_TIER_ORDER.get(tierIndex);
        List<String> earlierTiers = SKILL_TIER_ORDER.subList(0, tierIndex);
        Long oldestInTier = steps.stream()
                .filter(s -> tier.equals(s.getTier()) && s.getId() != null)
                .map(RoadmapStepDto::getId)
                .min(Long::compare)
                .orElse(null);
        for (RoadmapStepDto step : steps) {
            if (step.isCompleted() || !earlierTiers.contains(step.getTier())) {
                continue;
            }
            // 이 티어가 생기기 전에 이미 있던 앞 티어 단계가 안 끝났으면 아직 잠겨 있다
            if (oldestInTier == null || step.getId() == null || step.getId() < oldestInTier) {
                return false;
            }
        }
        return true;
    }

    // 티어 돌파 환영 모달용(2026-10-01) — 이번 작업 전(before)엔 미완료였던 티어가 작업 후(after)에
    // 완료가 됐으면 그 티어(after 기준)를 돌려준다. "방금 그 순간"만 잡아내려고 전/후 스냅샷을
    // 비교하는 방식이라, 새로고침이나 이미 끝난 티어를 다시 볼 때는 항상 null이다.
    // 한 번에 두 티어가 동시에 끝나는 일은 없지만(단계 하나 완료당 티어 하나만 영향) 가장 앞 티어를 준다.
    public TierProgress findNewlyCompletedTier(RoadmapProgress before, RoadmapProgress after) {
        if (before == null || after == null) {
            return null;
        }
        for (TierProgress tier : after.getTiers()) {
            TierProgress previous = before.getTier(tier.getTier());
            if (tier.isComplete() && previous != null && !previous.isComplete()) {
                return tier;
            }
        }
        return null;
    }
}
