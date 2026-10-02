package com.specodyssey.service;

import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.RoadmapStepDto;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 로드맵 단계 하나를 끝냈을 때 받는 점수.
 *
 * 기술(SKILL) 단계는 "직무 사다리 전체 = LADDER_BUDGET점"을 중요도(필수/우대) × 단계(입문→전문가) 가중치
 * 비율로 나눈 값이다. 그래서 기술이 10개든 13개든 사다리를 끝까지 하면 받는 총점이 같고, 티어는 사다리 끝까지
 * 간 뒤에도 일일 문제·복습·업데이트로 시간을 들여 올려야 한다. 자격증·프로젝트 단계는 사다리 밖이라 고정 점수다.
 *
 * 신기술 처리: 단가는 "사용자가 이 직무로 처음 로드맵을 만든 시점에 이미 있던 기술"만으로 정한다. 그 뒤에
 * 직무에 새로 추가된 기술은 같은 단가로 점수를 더 받을 뿐 단가를 깎지 않는다 — 새 기술이 생겼다고 이미 쌓은
 * 단계의 점수 가치가 떨어지지 않는다.
 */
final class StepPointCalculator {

    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();

    int pointsFor(Connection conn, Long userId, RoadmapStepDto step) throws SQLException {
        int fixed = ScoringRules.get(ScoringRules.FIXED_STEP_POINTS);
        if (step == null || !"SKILL".equals(step.getStepType()) || step.getRelatedSkillId() == null
                || step.getRoadmapId() == null) {
            return fixed;
        }
        Long jobId = roadmapDao.findJobIdByRoadmapId(conn, step.getRoadmapId());
        if (jobId == null) {
            return fixed;
        }
        List<JobRequiredSkillDto> skills = jobRequiredSkillDao.findByJobId(conn, jobId);
        LocalDateTime start = roadmapDao.findFirstCreatedAtByUserAndJob(conn, userId, jobId);
        return skillStepPoints(skills, start, step.getRelatedSkillId(), step.getTier());
    }

    /** DB 없이 계산만 — skills: 직무의 현재 요구 기술, start: 사용자의 첫 로드맵 시각(null이면 전부 기준) */
    static int skillStepPoints(List<JobRequiredSkillDto> skills, LocalDateTime start, Long skillId, String tier) {
        int baseWeight = 0;
        int allWeight = 0;
        int ownImportanceWeight = importanceWeight(null);
        for (JobRequiredSkillDto s : skills) {
            int w = importanceWeight(s.getImportance()) * ladderWeight();
            allWeight += w;
            if (start == null || s.getCreatedAt() == null || !s.getCreatedAt().isAfter(start)) {
                baseWeight += w;
            }
            if (skillId.equals(s.getSkillId())) {
                ownImportanceWeight = importanceWeight(s.getImportance());
            }
        }
        int denominator = baseWeight > 0 ? baseWeight : allWeight;
        int min = ScoringRules.get(ScoringRules.STEP_POINTS_MIN);
        if (denominator <= 0) {
            return ScoringRules.get(ScoringRules.FIXED_STEP_POINTS);
        }
        double unit = (double) ScoringRules.get(ScoringRules.LADDER_BUDGET) / denominator;
        return Math.max(min, (int) Math.round(unit * ownImportanceWeight * tierWeight(tier)));
    }

    static int tierWeight(String tier) {
        return ScoringRules.get(ScoringRules.WEIGHT_TIER_PREFIX + (tier == null ? "ENTRY" : tier));
    }

    static int importanceWeight(String importance) {
        return "REQUIRED".equals(importance)
                ? ScoringRules.get(ScoringRules.WEIGHT_IMPORTANCE_REQUIRED)
                : ScoringRules.get(ScoringRules.WEIGHT_IMPORTANCE_PREFERRED);
    }

    // 기술 하나가 입문→핵심→심화→전문가를 모두 거칠 때의 단계 가중치 합
    private static int ladderWeight() {
        int sum = 0;
        for (String tier : RoadmapConstants.SKILL_TIER_ORDER) {
            sum += tierWeight(tier);
        }
        return sum;
    }
}
