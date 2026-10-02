package com.specodyssey.service;

import com.specodyssey.dao.ScoringRuleDao;
import com.specodyssey.dto.JobRequiredSkillDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 단계 점수 계산(StepPointCalculator)과 규칙 테이블(ScoringRules) 검증 — DB 없이 되는 순수 계산 위주.
 */
class StepPointCalculatorTest {

    private static final String[] TIERS = {"ENTRY", "CORE", "ADVANCED", "EXPERT"};

    private static JobRequiredSkillDto skill(long id, String importance, LocalDateTime createdAt) {
        JobRequiredSkillDto s = new JobRequiredSkillDto();
        s.setSkillId(id);
        s.setImportance(importance);
        s.setCreatedAt(createdAt);
        return s;
    }

    private static List<JobRequiredSkillDto> skills(int count, LocalDateTime createdAt) {
        List<JobRequiredSkillDto> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(skill(i, i % 2 == 0 ? "REQUIRED" : "PREFERRED", createdAt));
        }
        return list;
    }

    private static int ladderTotal(List<JobRequiredSkillDto> list) {
        int total = 0;
        for (JobRequiredSkillDto s : list) {
            for (String tier : TIERS) {
                total += StepPointCalculator.skillStepPoints(list, null, s.getSkillId(), tier);
            }
        }
        return total;
    }

    @Test
    void 기술이_몇_개든_사다리를_끝까지_하면_받는_총점이_거의_같다() {
        int budget = ScoringRules.get(ScoringRules.LADDER_BUDGET);
        for (int count : new int[] {6, 10, 13, 20}) {
            int total = ladderTotal(skills(count, null));
            // 단계별 반올림 오차만 허용한다(단계 수 × 0.5점)
            assertTrue(Math.abs(total - budget) <= count * TIERS.length,
                    count + "개일 때 총점 " + total + " (목표 " + budget + ")");
        }
    }

    @Test
    void 뒤_단계일수록_필수_기술일수록_점수가_크다() {
        List<JobRequiredSkillDto> list = skills(10, null);
        long required = 2;  // 짝수 id = REQUIRED
        long preferred = 1;
        int entry = StepPointCalculator.skillStepPoints(list, null, required, "ENTRY");
        int core = StepPointCalculator.skillStepPoints(list, null, required, "CORE");
        int expert = StepPointCalculator.skillStepPoints(list, null, required, "EXPERT");
        assertTrue(entry < core && core < expert, entry + " " + core + " " + expert);
        assertTrue(StepPointCalculator.skillStepPoints(list, null, required, "CORE")
                > StepPointCalculator.skillStepPoints(list, null, preferred, "CORE"));
    }

    @Test
    void 사용자가_시작한_뒤_직무에_추가된_신기술은_기존_단계의_점수를_깎지_않는다() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        List<JobRequiredSkillDto> base = skills(10, start.minusDays(30));
        int before = StepPointCalculator.skillStepPoints(base, start, 2L, "CORE");

        List<JobRequiredSkillDto> withNew = new ArrayList<>(base);
        withNew.add(skill(99, "REQUIRED", start.plusDays(20))); // 시작 뒤에 생긴 신기술
        int after = StepPointCalculator.skillStepPoints(withNew, start, 2L, "CORE");
        int newTech = StepPointCalculator.skillStepPoints(withNew, start, 99L, "CORE");

        assertEquals(before, after, "기존 기술의 점수는 그대로");
        assertTrue(newTech > 0, "신기술 단계도 같은 단가로 점수를 받는다");
        assertEquals(StepPointCalculator.skillStepPoints(withNew, start, 2L, "CORE"), newTech,
                "같은 필수·같은 단계라 점수도 같다");
    }

    @Test
    void 시작_시점에_이미_있던_기술이_하나도_없으면_전체를_기준으로_삼는다() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        List<JobRequiredSkillDto> allNew = skills(5, start.plusDays(1));
        assertTrue(StepPointCalculator.skillStepPoints(allNew, start, 2L, "ENTRY") > 0);
    }

    @Test
    void 아무리_기술이_많아도_단계_점수는_최소값_밑으로_내려가지_않는다() {
        int min = ScoringRules.get(ScoringRules.STEP_POINTS_MIN);
        assertTrue(StepPointCalculator.skillStepPoints(skills(500, null), null, 1L, "ENTRY") >= min);
    }

    @Test
    void 규칙_테이블이_없거나_값이_이상해도_기본값으로_돌아간다() {
        assertEquals(2500, ScoringRules.get(ScoringRules.LADDER_BUDGET));
        assertThrows(IllegalArgumentException.class, () -> ScoringRules.get("NOT_A_KEY"));
    }

    @Test
    void 규칙_테이블의_값을_바꾸면_점수와_주기에_반영되고_0이나_음수는_무시된다() throws Exception {
        ScoringRuleDao dao = new ScoringRuleDao();
        try {
            dao.upsert("REVIEW_DAYS_ENTRY", 45);
            dao.upsert("LADDER_BUDGET", 0);
            ScoringRules.refresh();
            assertEquals(45, RoadmapReviewService.reviewIntervalDays("ENTRY"));
            assertEquals(2500, ScoringRules.get(ScoringRules.LADDER_BUDGET), "0은 기본값으로");
            dao.upsert("LADDER_BUDGET", -5);
            ScoringRules.refresh();
            assertEquals(2500, ScoringRules.get(ScoringRules.LADDER_BUDGET), "음수는 기본값으로");
        } finally {
            dao.upsert("REVIEW_DAYS_ENTRY", 30);
            dao.upsert("LADDER_BUDGET", 2500);
            ScoringRules.refresh();
        }
        assertEquals(30, RoadmapReviewService.reviewIntervalDays("ENTRY"));
    }

    @Test
    void 일일_문제_점수도_규칙_테이블_값을_따른다() throws Exception {
        ScoringRuleDao dao = new ScoringRuleDao();
        try {
            dao.upsert("DAILY_POINTS_1", 7);
            ScoringRules.refresh();
            assertEquals(7, MissionSubmitService.pointsForTierOrder(0));
            assertEquals(15, MissionSubmitService.pointsForTierOrder(1));
        } finally {
            dao.upsert("DAILY_POINTS_1", 15);
            ScoringRules.refresh();
        }
        assertEquals(15, MissionSubmitService.pointsForTierOrder(0));
    }
}
