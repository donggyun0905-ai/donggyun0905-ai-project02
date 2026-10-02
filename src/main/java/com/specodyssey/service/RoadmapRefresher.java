package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.service.RoadmapService.NoGapAnalysisException;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.specodyssey.service.RoadmapConstants.TIER_ENTRY;

/**
 * 로드맵 "바뀐 부분만 반영" (FR-37) — 재분석 결과를 지금 로드맵에 덧대어, 달라진 단계만 넣고 뺀다.
 *
 * RoadmapGenerator.generate는 매번 새 버전의 로드맵을 통째로 만들어서, 그대로여야 할 단계까지
 * 문구(LLM이 만든 프로젝트 아이디어 등)와 순서가 달라졌다. 여기서는 같은 로드맵을 그대로 두고
 * RoadmapRefreshPlan이 정한 만큼만 고친다 — 그대로인 단계는 내용·완료 기록·증빙이 전혀 바뀌지 않는다.
 *
 * 통째로 새로 만드는 경우(RoadmapGenerator에 그대로 맡긴다):
 *  - 대표 로드맵이 아직 없다 (처음 만들기)
 *  - 목표 직무가 바뀌었다 (기술 구성이 통째로 다르다)
 *  - 지금 로드맵을 다 끝냈고 새로 넣을 것도 없다 (다음 라운드 — 예전부터 부족했던 나머지 기술은 이때 담긴다)
 */
public class RoadmapRefresher {

    /**
     * @param rebuilt      통째로 새로 만들었으면 true (addedSteps·removedSteps는 0)
     * @param addedSteps   새로 넣은 단계 수
     * @param removedSteps 뺀 단계 수
     */
    public record Result(Long roadmapId, boolean rebuilt, int addedSteps, int removedSteps) {
        public boolean isChanged() {
            return rebuilt || addedSteps > 0 || removedSteps > 0;
        }
    }

    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final JobDao jobDao = new JobDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final RoadmapStepWriter stepWriter = new RoadmapStepWriter();
    private final RoadmapGenerator generator;

    public RoadmapRefresher(RoadmapGenerator generator) {
        this.generator = generator;
    }

    public Result refresh(Long userId) throws SQLException, NoGapAnalysisException {
        List<GapAnalysisDto> analyses = gapAnalysisDao.findByUserId(userId);
        if (analyses.isEmpty()) {
            throw new NoGapAnalysisException("격차 분석 결과가 없어 로드맵을 만들 수 없습니다. 먼저 격차 분석을 진행해주세요.");
        }
        GapAnalysisDto latest = analyses.get(0); // analyzed_at DESC — 첫 번째가 최신
        RoadmapDto primary = roadmapDao.findPrimaryByUserId(userId);
        if (primary == null) {
            return rebuild(userId);
        }
        if (latest.getId().equals(primary.getGapAnalysisId())) {
            return new Result(primary.getId(), false, 0, 0); // 이미 최신 분석 기준 — 바뀐 것이 없다
        }
        GapAnalysisDto base = primary.getGapAnalysisId() == null ? null : gapAnalysisDao.findById(primary.getGapAnalysisId());
        if (base == null || !Objects.equals(base.getJobId(), latest.getJobId())
                || roadmapDao.findByGapAnalysisId(latest.getId()) != null) {
            // 목표 직무가 바뀌었거나, 최신 분석으로 만든 로드맵이 따로 있다(gap_analysis_id UNIQUE) — 기존 방식에 맡긴다
            return rebuild(userId);
        }

        JobDto job = jobDao.findById(latest.getJobId());
        Map<Long, String> importanceBySkillId = generator.importanceMap(latest.getJobId());
        List<Long> rankedMissing = new ArrayList<>();
        for (GapAnalysisItemDto item : generator.rankMissingSkills(latest)) {
            rankedMissing.add(item.getSkillId());
        }
        Set<Long> previouslyMissing = new HashSet<>();
        for (GapAnalysisItemDto item : generator.rankMissingSkills(base)) {
            previouslyMissing.add(item.getSkillId());
        }
        List<RoadmapStepDto> steps = roadmapStepDao.findByRoadmapId(primary.getId());
        RoadmapRefreshPlan plan = RoadmapRefreshPlan.of(steps, importanceBySkillId.keySet(), rankedMissing, previouslyMissing,
                ScoringRules.get(ScoringRules.ROUND_SKILL_COUNT));

        // 자격증: 진행 중인 자격증 단계가 없을 때만 다음 자격증을 하나 넣는다(이미 로드맵에 있던 것은 다시 넣지 않는다).
        // 자격증 단계는 입문 티어에 들어가는데, 앞 티어를 다 끝내야 다음 티어가 열리는 구조(RoadmapProgressCalculator)라
        // 입문을 이미 끝낸 사람에게 넣으면 걷고 있던 뒤 티어가 통째로 다시 잠긴다. 그래서 입문이 아직 열려 있거나,
        // 새 기술이 들어와 어차피 입문이 다시 열릴 때만 넣는다.
        boolean entryOpen = plan.hasUnfinishedEntryStep() || !plan.getAddedSkillIds().isEmpty();
        CertificationDto suggested = !entryOpen || plan.hasUnfinishedCert()
                ? null : generator.findSuggestedCertification(userId, job);
        CertificationDto newCert = suggested != null && !plan.hasCert(suggested.getId()) ? suggested : null;

        if (!plan.hasUnfinishedLadderStep() && plan.getAddedSkillIds().isEmpty() && newCert == null) {
            return rebuild(userId); // 다 끝냈고 넣을 것도 없다 — 다음 라운드는 통째로 새로 만든다
        }

        // 프로젝트 단계는 있던 것을 그대로 쓴다. 하나도 없을 때만 새로 넣는다.
        // LLM 호출이 들어갈 수 있어 DB 트랜잭션을 열기 전에 끝낸다(실패해도 고정 문구로 대체 — FR-111).
        List<Long> skillIdsAfter = plan.skillIdsAfterRefresh();
        boolean addProject = !plan.hasProjectStep() && !skillIdsAfter.isEmpty();
        String projectReason = addProject ? generator.buildProjectReason(job, skillIdsAfter) : null;

        List<RoadmapRefreshPlan.Slot> slots = plan.orderedSlots(newCert != null, addProject);
        int added = (int) slots.stream().filter(RoadmapRefreshPlan.Slot::isNew).count();
        int removed = plan.getRemoved().size();
        boolean changed = added > 0 || removed > 0;
        int version = changed ? nextVersion(userId) : primary.getVersion();
        // 새 기술 단계의 안내 문구도 미리 만들어 둔다(SkillDao 조회 — 트랜잭션 커넥션과 섞지 않는다)
        List<String> reasons = new ArrayList<>();
        for (RoadmapRefreshPlan.Slot slot : slots) {
            reasons.add(slot.isNew() && RoadmapRefreshPlan.NEW_SKILL.equals(slot.newType())
                    ? generator.buildSkillReason(slot.skillId(), importanceBySkillId.get(slot.skillId()), slot.tier())
                    : null);
        }

        TransactionUtil.runInTransaction(conn -> {
            for (RoadmapStepDto step : plan.getRemoved()) {
                roadmapStepDao.softDeleteIncomplete(conn, step.getId(), userId);
            }
            int order = 1;
            for (int i = 0; i < slots.size(); i++) {
                RoadmapRefreshPlan.Slot slot = slots.get(i);
                if (!slot.isNew()) {
                    RoadmapStepDto step = slot.existing();
                    if (step.getStepOrder() == null || step.getStepOrder() != order) {
                        roadmapStepDao.updateStepOrder(conn, step.getId(), userId, order);
                    }
                    order++;
                } else if (RoadmapRefreshPlan.NEW_CERT.equals(slot.newType())) {
                    order = stepWriter.insertStep(conn, primary.getId(), order, "CERT", TIER_ENTRY, newCert.getId(), null,
                            generator.buildCertReason(job, newCert));
                } else if (RoadmapRefreshPlan.NEW_PROJECT.equals(slot.newType())) {
                    order = stepWriter.insertStep(conn, primary.getId(), order, "PROJECT", TIER_ENTRY, null, null, projectReason);
                } else {
                    // 같은 기술의 같은 단계를 예전에 이미 끝냈으면 완료로 승계한다(점수는 다시 안 줌) — 생성과 같은 규칙
                    boolean alreadyLearned = roadmapStepDao.countCompletedByUserSkillAndTier(
                            conn, userId, slot.skillId(), slot.tier()) > 0;
                    order = stepWriter.insertStep(conn, primary.getId(), order, "SKILL", slot.tier(), null, slot.skillId(),
                            reasons.get(i), alreadyLearned);
                }
            }
            // 기준 분석을 최신으로 옮긴다 — 바뀐 단계가 없어도 "요구 기술이 바뀌었어요" 배너는 사라져야 한다
            roadmapDao.updateGapAnalysisAndVersion(conn, primary.getId(), userId, latest.getId(), version);
            return null;
        });
        return new Result(primary.getId(), false, added, removed);
    }

    private Result rebuild(Long userId) throws SQLException, NoGapAnalysisException {
        return new Result(generator.generate(userId), true, 0, 0);
    }

    private int nextVersion(Long userId) throws SQLException {
        List<RoadmapDto> existing = roadmapDao.findByUserId(userId);
        return existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;
    }
}
