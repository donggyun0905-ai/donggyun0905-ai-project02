package com.specodyssey.service;

import com.specodyssey.dto.RoadmapStepDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "바뀐 부분만 반영" 계산 — DB 없이 단계 목록만으로 검증한다. */
class RoadmapRefreshPlanTest {

    private long nextId = 1;

    private RoadmapStepDto step(String type, String tier, Long skillId, boolean completed) {
        RoadmapStepDto s = new RoadmapStepDto();
        s.setId(nextId);
        s.setStepOrder((int) nextId);
        nextId++;
        s.setStepType(type);
        s.setTier(tier);
        s.setRelatedSkillId(skillId);
        s.setCompleted(completed);
        return s;
    }

    /** 자격증·프로젝트 + 기술 skillIds를 네 티어에 하나씩 가진 로드맵, 뒤에 복습 단계 하나 */
    private List<RoadmapStepDto> roadmap(long... skillIds) {
        List<RoadmapStepDto> steps = new ArrayList<>();
        for (String tier : RoadmapConstants.SKILL_TIER_ORDER) {
            if (RoadmapConstants.TIER_ENTRY.equals(tier)) {
                steps.add(step("CERT", tier, null, false));
                steps.add(step("PROJECT", tier, null, false));
            }
            for (long skillId : skillIds) {
                steps.add(step("SKILL", tier, skillId, false));
            }
        }
        steps.add(step("REVIEW", "REVIEW", skillIds[0], false));
        return steps;
    }

    private static List<Long> existingIds(List<RoadmapRefreshPlan.Slot> slots) {
        return slots.stream().filter(s -> !s.isNew()).map(s -> s.existing().getId()).collect(Collectors.toList());
    }

    @Test
    void 바뀐_것이_없으면_아무것도_넣거나_빼지_않고_순서도_그대로다() {
        List<RoadmapStepDto> steps = roadmap(10, 11, 12, 13, 14);
        RoadmapRefreshPlan plan = RoadmapRefreshPlan.of(steps, Set.of(10L, 11L, 12L, 13L, 14L),
                List.of(10L, 11L, 12L, 13L, 14L), Set.of(10L, 11L, 12L, 13L, 14L), 5);

        assertTrue(plan.getRemoved().isEmpty());
        assertTrue(plan.getAddedSkillIds().isEmpty());
        List<RoadmapRefreshPlan.Slot> slots = plan.orderedSlots(false, false);
        assertEquals(steps.stream().map(RoadmapStepDto::getId).collect(Collectors.toList()), existingIds(slots));
    }

    @Test
    void 직무_요구에서_빠진_기술은_안_끝낸_단계만_빼고_끝낸_단계는_남긴다() {
        List<RoadmapStepDto> steps = roadmap(10, 11);
        RoadmapStepDto doneEntry = steps.stream()
                .filter(s -> "SKILL".equals(s.getStepType()) && s.getRelatedSkillId() == 11L
                        && RoadmapConstants.TIER_ENTRY.equals(s.getTier()))
                .findFirst().orElseThrow();
        doneEntry.setCompleted(true);

        RoadmapRefreshPlan plan = RoadmapRefreshPlan.of(steps, Set.of(10L), List.of(10L), Set.of(10L, 11L), 5);

        assertEquals(3, plan.getRemoved().size()); // 기술 11의 핵심·심화·전문가
        assertTrue(plan.getRemoved().stream().allMatch(s -> s.getRelatedSkillId() == 11L && !s.isCompleted()));
        assertTrue(existingIds(plan.orderedSlots(false, false)).contains(doneEntry.getId()));
    }

    @Test
    void 새로_부족해진_기술은_각_티어의_맨_뒤에_붙고_기존_단계의_순서는_유지된다() {
        List<RoadmapStepDto> steps = roadmap(10, 11);
        RoadmapRefreshPlan plan = RoadmapRefreshPlan.of(steps, Set.of(10L, 11L, 20L), List.of(20L, 10L, 11L), Set.of(10L, 11L), 5);

        assertEquals(List.of(20L), plan.getAddedSkillIds());
        List<RoadmapRefreshPlan.Slot> slots = plan.orderedSlots(false, false);
        assertEquals(4, slots.stream().filter(RoadmapRefreshPlan.Slot::isNew).count()); // 티어마다 하나
        // 기존 단계끼리의 순서는 그대로
        assertEquals(steps.stream().map(RoadmapStepDto::getId).collect(Collectors.toList()), existingIds(slots));
        // 입문 티어: 자격증, 프로젝트, 기술 10, 기술 11, 그다음에 새 기술 20
        assertTrue(slots.get(4).isNew());
        assertEquals(20L, slots.get(4).skillId());
        assertEquals(RoadmapConstants.TIER_ENTRY, slots.get(4).tier());
        // 복습 단계는 맨 뒤
        assertEquals("REVIEW", slots.get(slots.size() - 1).existing().getStepType());
    }

    @Test
    void 예전부터_부족했던_기술은_바뀐_것이_아니라서_넣지_않는다() {
        List<RoadmapStepDto> steps = roadmap(10, 11);
        // 20은 이전 분석에서도 부족했다(라운드에 안 담겼을 뿐), 21은 이번에 새로 부족해졌다
        RoadmapRefreshPlan plan = RoadmapRefreshPlan.of(steps, Set.of(10L, 11L, 20L, 21L), List.of(20L, 21L, 10L, 11L),
                Set.of(10L, 11L, 20L), 5);

        assertEquals(List.of(21L), plan.getAddedSkillIds());
        assertEquals(List.of(10L, 11L, 21L), plan.skillIdsAfterRefresh());
    }

    @Test
    void 새로_부족해진_기술이_많아도_한_번에_라운드_크기까지만_넣는다() {
        List<RoadmapStepDto> steps = roadmap(10);
        RoadmapRefreshPlan plan = RoadmapRefreshPlan.of(steps, Set.of(10L, 20L, 21L, 22L), List.of(20L, 21L, 22L),
                Set.of(10L), 2);

        assertEquals(List.of(20L, 21L), plan.getAddedSkillIds());
    }

    @Test
    void 새_자격증과_프로젝트는_입문_티어의_맨_뒤에_붙는다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        steps.add(step("SKILL", RoadmapConstants.TIER_ENTRY, 10L, false));
        steps.add(step("SKILL", RoadmapConstants.TIER_CORE, 10L, false));
        RoadmapRefreshPlan plan = RoadmapRefreshPlan.of(steps, Set.of(10L), List.of(10L), Set.of(10L), 5);

        assertFalse(plan.hasProjectStep());
        assertFalse(plan.hasUnfinishedCert());
        assertTrue(plan.hasUnfinishedEntryStep());
        List<RoadmapRefreshPlan.Slot> slots = plan.orderedSlots(true, true);
        // 입문: 있던 기술 → 새 자격증 → 새 프로젝트, 그 뒤에 핵심의 있던 기술
        assertEquals(1L, slots.get(0).existing().getId());
        assertEquals(RoadmapRefreshPlan.NEW_CERT, slots.get(1).newType());
        assertEquals(RoadmapRefreshPlan.NEW_PROJECT, slots.get(2).newType());
        assertEquals(2L, slots.get(3).existing().getId());
        assertEquals(4, slots.size());
    }

    @Test
    void 끝낸_단계는_새_단계가_들어와도_자리와_순서가_그대로다() {
        List<RoadmapStepDto> steps = new ArrayList<>();
        RoadmapStepDto doneCert = step("CERT", RoadmapConstants.TIER_ENTRY, null, true);
        RoadmapStepDto doneSkill = step("SKILL", RoadmapConstants.TIER_ENTRY, 10L, true);
        RoadmapStepDto openProject = step("PROJECT", RoadmapConstants.TIER_ENTRY, null, false);
        steps.add(doneCert);
        steps.add(doneSkill);
        steps.add(openProject);
        // 11번 기술이 이번에 새로 부족해졌다
        RoadmapRefreshPlan plan = RoadmapRefreshPlan.of(steps, Set.of(10L, 11L), List.of(11L, 10L), Set.of(10L), 5);

        assertEquals(List.of(11L), plan.getAddedSkillIds());
        List<RoadmapRefreshPlan.Slot> slots = plan.orderedSlots(false, false);
        // 있던 세 단계가 원래 순서 그대로 앞에 오고, 새 기술은 그 뒤
        assertEquals(doneCert.getId(), slots.get(0).existing().getId());
        assertEquals(doneSkill.getId(), slots.get(1).existing().getId());
        assertEquals(openProject.getId(), slots.get(2).existing().getId());
        assertEquals(RoadmapRefreshPlan.NEW_SKILL, slots.get(3).newType());
        assertEquals(11L, slots.get(3).skillId());
    }

    @Test
    void 안_끝낸_사다리_단계가_있는지와_자격증_보유를_알려준다() {
        List<RoadmapStepDto> steps = roadmap(10);
        steps.forEach(s -> s.setCompleted(true));
        steps.get(0).setCertificationId(77L);
        RoadmapRefreshPlan done = RoadmapRefreshPlan.of(steps, Set.of(10L), List.of(), Set.of(10L), 5);

        assertFalse(done.hasUnfinishedLadderStep());
        assertFalse(done.hasUnfinishedEntryStep());
        assertFalse(done.hasUnfinishedCert());
        assertTrue(done.hasCert(77L));
        assertFalse(done.hasCert(78L));
        assertTrue(done.hasProjectStep());
    }
}
