package com.specodyssey.service;

import com.specodyssey.dto.RoadmapStepDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.specodyssey.service.RoadmapConstants.SKILL_TIER_ORDER;
import static com.specodyssey.service.RoadmapConstants.TIER_ENTRY;

/**
 * "바뀐 부분만 반영"의 계산 부분 — 지금 로드맵의 단계와 새 격차 분석을 비교해 무엇을 빼고 무엇을 넣을지,
 * 그리고 단계 순서를 어떻게 다시 매길지 정한다. DB를 건드리지 않는 순수 계산이라 따로 떼어 테스트한다
 * (실제 반영은 RoadmapRefresher).
 *
 * 규칙
 *  - 끝낸 단계는 어떤 경우에도 그대로 둔다(기록).
 *  - 직무 요구에서 빠진 기술은 아직 안 끝낸 단계만 뺀다.
 *  - 이전 분석 이후 "새로" 부족해진 기술만 우선순위 순으로 넣는다(한 번에 최대 한 라운드 크기만큼).
 *    예전부터 부족했지만 이번 라운드에 안 담았던 기술은 바뀐 것이 아니므로 넣지 않는다 — 넣으면 입문 단계가
 *    새로 생겨 걷고 있던 뒤 티어가 다시 잠긴다. 그런 기술은 로드맵을 다 끝낸 뒤 다음 라운드에서 담는다.
 *  - 그대로인 단계는 내용도, 서로의 순서도 바꾸지 않는다. 새 단계는 같은 티어의 맨 뒤에 붙인다.
 */
final class RoadmapRefreshPlan {

    static final String NEW_CERT = "CERT";
    static final String NEW_PROJECT = "PROJECT";
    static final String NEW_SKILL = "SKILL";

    /** 순서를 매길 한 칸 — 기존 단계(existing)이거나 새로 넣을 단계(newType·tier·skillId) */
    record Slot(RoadmapStepDto existing, String newType, String tier, Long skillId) {
        boolean isNew() {
            return existing == null;
        }
    }

    private final List<RoadmapStepDto> kept;
    private final List<RoadmapStepDto> removed;
    private final List<Long> addedSkillIds;

    private RoadmapRefreshPlan(List<RoadmapStepDto> kept, List<RoadmapStepDto> removed, List<Long> addedSkillIds) {
        this.kept = kept;
        this.removed = removed;
        this.addedSkillIds = addedSkillIds;
    }

    /**
     * "길 더 만들기" 전용 — 빼는 것 없이 주어진 기술만 각 티어 맨 뒤에 이어 붙인다.
     * 기존 단계는 하나도 건드리지 않으므로 kept에 그대로 담는다.
     */
    static RoadmapRefreshPlan appendOnly(List<RoadmapStepDto> steps, List<Long> addedSkillIds) {
        return new RoadmapRefreshPlan(new ArrayList<>(steps), List.of(), new ArrayList<>(addedSkillIds));
    }

    /**
     * @param steps                 지금 로드맵의 단계 (step_order 순)
     * @param requiredSkillIds      목표 직무가 지금 요구하는 기술
     * @param rankedMissingSkillIds 새 분석에서 부족한 기술 (우선순위 높은 순)
     * @param previouslyMissing     로드맵이 기준으로 삼았던 이전 분석에서 이미 부족했던 기술
     * @param roundSize             한 번에 새로 넣는 기술 수의 상한 (ROUND_SKILL_COUNT)
     */
    static RoadmapRefreshPlan of(List<RoadmapStepDto> steps, Set<Long> requiredSkillIds,
            List<Long> rankedMissingSkillIds, Set<Long> previouslyMissing, int roundSize) {
        List<RoadmapStepDto> kept = new ArrayList<>();
        List<RoadmapStepDto> removed = new ArrayList<>();
        Set<Long> skillsInRoadmap = new LinkedHashSet<>();
        for (RoadmapStepDto step : steps) {
            boolean ladderSkill = NEW_SKILL.equals(step.getStepType()) && step.getRelatedSkillId() != null;
            if (ladderSkill && !step.isCompleted() && !requiredSkillIds.contains(step.getRelatedSkillId())) {
                removed.add(step);
                continue;
            }
            kept.add(step);
            if (ladderSkill) {
                skillsInRoadmap.add(step.getRelatedSkillId());
            }
        }

        List<Long> added = new ArrayList<>();
        for (Long skillId : rankedMissingSkillIds) {
            if (added.size() >= roundSize) {
                break;
            }
            if (!previouslyMissing.contains(skillId) && !skillsInRoadmap.contains(skillId) && !added.contains(skillId)) {
                added.add(skillId);
            }
        }
        return new RoadmapRefreshPlan(List.copyOf(kept), List.copyOf(removed), List.copyOf(added));
    }

    List<RoadmapStepDto> getRemoved() {
        return removed;
    }

    List<Long> getAddedSkillIds() {
        return addedSkillIds;
    }

    /** 남는 단계 중 아직 안 끝낸 사다리 단계(자격증·프로젝트·기술)가 있는지 */
    boolean hasUnfinishedLadderStep() {
        return kept.stream().anyMatch(step -> isLadder(step) && !step.isCompleted());
    }

    /** 입문 티어에 아직 안 끝낸 단계가 남아 있는지 — 없으면 사용자는 이미 다음 티어를 걷고 있다 */
    boolean hasUnfinishedEntryStep() {
        return kept.stream().anyMatch(step -> isLadder(step) && TIER_ENTRY.equals(step.getTier()) && !step.isCompleted());
    }

    boolean hasUnfinishedCert() {
        return kept.stream().anyMatch(step -> NEW_CERT.equals(step.getStepType()) && !step.isCompleted());
    }

    boolean hasCert(Long certificationId) {
        return kept.stream().anyMatch(step -> NEW_CERT.equals(step.getStepType())
                && certificationId != null && certificationId.equals(step.getCertificationId()));
    }

    boolean hasProjectStep() {
        return kept.stream().anyMatch(step -> NEW_PROJECT.equals(step.getStepType()));
    }

    /** 남는 기술 + 새로 넣는 기술 (로드맵에 나오는 순서) */
    List<Long> skillIdsAfterRefresh() {
        Set<Long> ids = new LinkedHashSet<>();
        for (RoadmapStepDto step : kept) {
            if (NEW_SKILL.equals(step.getStepType()) && step.getRelatedSkillId() != null) {
                ids.add(step.getRelatedSkillId());
            }
        }
        ids.addAll(addedSkillIds);
        return new ArrayList<>(ids);
    }

    /**
     * 다시 매길 순서. 티어마다 [기존 자격증·프로젝트 → 새 자격증 → 새 프로젝트 → 기존 기술 → 새 기술] 순이고,
     * 사다리 밖 단계(복습·업데이트 등)는 원래 순서대로 맨 뒤에 둔다. 새 자격증·프로젝트는 입문 티어에만 들어간다.
     */
    List<Slot> orderedSlots(boolean addCert, boolean addProject) {
        List<Slot> slots = new ArrayList<>();
        for (String tier : SKILL_TIER_ORDER) {
            // 그대로 두는 단계는 지금 순서(kept가 step_order 순)를 그대로 유지한다 — 종류별로 모으면
            // 끝낸 단계가 자리를 옮겨 이미 걸어온 길이 뒤바뀐다(사용자 확인, 2026-10-06).
            for (RoadmapStepDto step : kept) {
                if (isLadder(step) && tier.equals(step.getTier())) {
                    slots.add(new Slot(step, null, tier, null));
                }
            }
            // 새 단계는 그 티어의 맨 뒤에 붙인다 — 앞에 끼워 넣으면 뒤 단계 번호가 전부 밀린다.
            // 새로 넣는 것끼리는 처음 만들 때와 같은 순서(자격증 → 프로젝트 → 기술)로 둔다.
            if (TIER_ENTRY.equals(tier)) {
                if (addCert) {
                    slots.add(new Slot(null, NEW_CERT, tier, null));
                }
                if (addProject) {
                    slots.add(new Slot(null, NEW_PROJECT, tier, null));
                }
            }
            for (Long skillId : addedSkillIds) {
                slots.add(new Slot(null, NEW_SKILL, tier, skillId));
            }
        }
        for (RoadmapStepDto step : kept) {
            if (!isLadder(step)) {
                slots.add(new Slot(step, null, step.getTier(), null));
            }
        }
        return slots;
    }

    private static boolean isLadder(RoadmapStepDto step) {
        return !step.isUpkeep() && SKILL_TIER_ORDER.contains(step.getTier());
    }
}
