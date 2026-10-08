package com.specodyssey.service;

import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.specodyssey.service.RoadmapConstants.SKILL_TIER_ORDER;

/**
 * D-day까지의 계획을 로드맵에서 만들어 준다 (2026-10-08).
 * 관련 요구사항: FR-71 · 72 D-day
 *
 * 알고리즘은 DdayPlanner(0/1 배낭)가 맡고, 여기서는 "배낭에 넣을 후보"를 모은다.
 * 후보 조건이 중요하다 — <b>지금 할 수 있는 단계만</b> 넣는다. 로드맵은 앞 티어를 끝내야 다음 티어가
 * 열리는 계단식 잠금이라, 잠긴 단계를 추천하면 "할 수 없는 일"을 알려 주는 셈이 된다.
 */
public class DdayPlanService {

    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final SkillDao skillDao = new SkillDao();
    private final RoadmapProgressCalculator progressCalculator = new RoadmapProgressCalculator();
    private final StepPointCalculator pointCalculator = new StepPointCalculator();

    /**
     * @param daysLeft D-day까지 남은 일수. 0 이하면 빈 계획
     * @return 남은 기간에 점수를 가장 많이 올리는 조합. 로드맵이 없으면 빈 계획
     */
    public DdayPlanner.Plan plan(Long userId, int daysLeft) throws SQLException {
        if (daysLeft <= 0) {
            return DdayPlanner.plan(List.of(), daysLeft);
        }
        RoadmapDto primary = roadmapDao.findPrimaryByUserId(userId);
        if (primary == null) {
            return DdayPlanner.plan(List.of(), daysLeft);
        }
        List<RoadmapStepDto> steps = roadmapStepDao.findByRoadmapId(primary.getId());
        RoadmapProgress progress = progressCalculator.computeProgress(steps);

        List<DdayPlanner.Candidate> candidates = new ArrayList<>();
        // 연결을 하나만 열어 전 단계의 점수를 계산한다 — 단계마다 열면 화면 하나에 수십 번이 된다
        try (Connection conn = DBUtil.getConnection()) {
            Map<Long, String> skillNames = new HashMap<>();
            for (RoadmapStepDto step : steps) {
                if (step.isCompleted() || !isDoableNow(progress, step.getTier())) {
                    continue;
                }
                candidates.add(new DdayPlanner.Candidate(
                        step.getId(),
                        label(step, skillNames),
                        step.getStepType(),
                        step.getTier(),
                        DdayPlanner.effortDays(step.getStepType(), step.getTier()),
                        pointCalculator.pointsFor(conn, userId, step)));
            }
        }
        return DdayPlanner.plan(candidates, daysLeft);
    }

    /**
     * 지금 열려 있는 티어인지. 사다리 티어(입문·핵심·심화·전문가)는 잠금 계산을 따르고,
     * 그 밖(복습 등)은 사다리에 속하지 않아 언제든 할 수 있다.
     */
    static boolean isDoableNow(RoadmapProgress progress, String tier) {
        if (tier == null || !SKILL_TIER_ORDER.contains(tier)) {
            return true;
        }
        TierProgress tierProgress = progress.getTier(tier);
        return tierProgress == null || tierProgress.isUnlocked();
    }

    /** 화면에 보여 줄 한 줄. 기술 단계는 기술 이름, 나머지는 단계 종류 + 이유 앞부분. */
    private String label(RoadmapStepDto step, Map<Long, String> skillNames) throws SQLException {
        String typeLabel = GlanceService.stepTypeLabel(step.getStepType());
        Long skillId = step.getRelatedSkillId();
        if (skillId != null) {
            String name = skillNames.get(skillId);
            if (name == null) {
                SkillDto skill = skillDao.findById(skillId);
                name = skill == null || skill.getSkillName() == null ? "기술" : skill.getSkillName();
                skillNames.put(skillId, name);
            }
            return name + " " + typeLabel;
        }
        String reason = GlanceService.shortReason(step.getReason());
        return reason == null || reason.isBlank() ? typeLabel : typeLabel + " — " + reason;
    }
}
