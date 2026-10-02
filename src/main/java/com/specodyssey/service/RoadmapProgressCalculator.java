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
    // 매번 steps 원본에서 새로 계산하기 때문에, 완료 취소로 이전 티어가 다시 미완료가 되면
    // 이후 티어도 그 즉시 다시 잠긴다 — 잠기기 전에 이미 완료한 단계 자체는 그대로 완료로 남는다.
    public RoadmapProgress computeProgress(List<RoadmapStepDto> steps) {
        List<TierProgress> tiers = new ArrayList<>();
        boolean unlocked = true;
        for (String tier : SKILL_TIER_ORDER) {
            long total = steps.stream().filter(s -> tier.equals(s.getTier())).count();
            long done = steps.stream().filter(s -> tier.equals(s.getTier()) && s.isCompleted()).count();
            int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
            tiers.add(new TierProgress(tier, (int) total, (int) done, percent, unlocked));
            boolean cleared = total == 0 || done == total;
            unlocked = unlocked && cleared;
        }
        return new RoadmapProgress(tiers);
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
