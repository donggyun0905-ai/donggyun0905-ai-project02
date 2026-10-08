package com.specodyssey.service;

import com.specodyssey.util.AppClock;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.SkillReviewScheduleDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillReviewScheduleDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.TransactionUtil;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static com.specodyssey.service.RoadmapConstants.*;

/**
 * 기술 복습 단계 — 끝낸 기술의 주기가 지나면 여정 뒤에 이어 붙이고, 복습 기록으로 완료한다.
 * RoadmapService 안에 있던 복습 부분을 그대로 옮겼다.
 */
public class RoadmapReviewService {

    private final SkillDao skillDao = new SkillDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final SkillReviewScheduleDao reviewScheduleDao = new SkillReviewScheduleDao();
    private final ScoreService scoreService = new ScoreService();
    private final RoadmapStepWriter stepWriter = new RoadmapStepWriter();

    // ---------------------------------------------------------------- 기술 복습 (끝없는 로드맵, 2026-10-01)
    // 한 번 익힌 기술도 시간이 지나면 잊으니, 끝낸 기술마다 주기가 지나면 "복습" 단계를 기존 여정 뒤에 이어 붙인다.
    // 주기는 단계별 차등 — 입문 30일, 핵심 60일, 심화 90일, 전문가 120일(깊이 익힌 기술일수록 오래 간다).
    // 기준 시각은 그 기술의 가장 최근 완료(학습 단계 또는 복습)이고, 주기는 지금까지 도달한 가장 높은 단계로 정한다.
    // 점수는 감쇠 — 같은 기술을 복습할수록 줄어든다(40→30→20→10→5, 최저 5). 같은 걸 반복해서 점수만 올리는 걸 막는다.
    static final String STEP_TYPE_REVIEW = "REVIEW";

    static final String TIER_REVIEW = "REVIEW";

    static final int MAX_OPEN_REVIEWS = 3;

    static final int REVIEW_NOTE_MIN_LENGTH = 20;

    static final int REVIEW_NOTE_MAX_LENGTH = 1000;

    private static final String PROOF_REVIEW_NOTE = "REVIEW_NOTE";

    static int reviewIntervalDays(String highestTier) {
        String tier = highestTier == null ? "" : highestTier;
        switch (tier) {
            case TIER_CORE:
            case TIER_ADVANCED:
            case TIER_EXPERT:
                return ScoringRules.get(ScoringRules.REVIEW_DAYS_PREFIX + tier);
            default:
                return ScoringRules.get(ScoringRules.REVIEW_DAYS_PREFIX + TIER_ENTRY);
        }
    }

    // priorReviews: 같은 기술로 이미 끝낸 복습 횟수
    static int reviewPoints(int priorReviews) {
        return Math.max(ScoringRules.get(ScoringRules.REVIEW_POINTS_MIN),
                ScoringRules.get(ScoringRules.REVIEW_POINTS_BASE)
                        - ScoringRules.get(ScoringRules.REVIEW_POINTS_DECAY) * Math.max(0, priorReviews));
    }

    /**
     * 주기가 지난 기술의 복습 단계를 대표 로드맵 맨 뒤에 만든다. 이미 열려 있는(안 끝낸) 복습이 있는 기술은 건너뛰고,
     * 한꺼번에 쏟아지지 않게 열린 복습은 MAX_OPEN_REVIEWS개까지만 둔다(주기가 오래 지난 기술부터).
     * 로드맵 화면을 열 때마다 불러도 같은 결과(멱등)다 — 별도 스케줄러 없이 DB 조회만 하고 LLM 비용은 없다.
     * @return 새로 만든 복습 단계 수
     */
    public int appendDueReviews(Long userId, LocalDateTime now) throws SQLException {
        RoadmapDto primary = roadmapDao.findPrimaryByUserId(userId);
        if (primary == null) {
            return 0;
        }
        List<RoadmapStepDto> steps = roadmapStepDao.findByRoadmapId(primary.getId());
        java.util.Set<Long> openReviewSkills = new java.util.HashSet<>();
        int maxOrder = 0;
        for (RoadmapStepDto step : steps) {
            maxOrder = Math.max(maxOrder, step.getStepOrder() == null ? 0 : step.getStepOrder());
            if (STEP_TYPE_REVIEW.equals(step.getStepType()) && !step.isCompleted() && step.getRelatedSkillId() != null) {
                openReviewSkills.add(step.getRelatedSkillId());
            }
        }
        int room = MAX_OPEN_REVIEWS - openReviewSkills.size();
        if (room <= 0) {
            return 0;
        }

        // 기술별: 도달한 가장 높은 단계 + 가장 최근 완료 시각
        Map<Long, String> highestTier = new HashMap<>();
        Map<Long, LocalDateTime> lastDone = new HashMap<>();
        for (RoadmapStepDto row : roadmapStepDao.findCompletedSkillRowsByUser(userId)) {
            Long skillId = row.getRelatedSkillId();
            if (STEP_TYPE_REVIEW.equals(row.getStepType()) == false) {
                String current = highestTier.get(skillId);
                if (current == null || SKILL_TIER_ORDER.indexOf(row.getTier()) > SKILL_TIER_ORDER.indexOf(current)) {
                    highestTier.put(skillId, row.getTier());
                }
            }
            if (row.getCompletedAt() != null) {
                lastDone.merge(skillId, row.getCompletedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }

        // 간격 반복(SM-2) 일정이 있으면 그 due_at을 쓴다. 한 번도 복습하지 않은 기술은 일정이 없고,
        // 그때는 예전처럼 "마지막으로 익힌 날 + 티어 기본 주기"로 본다 (2026-10-08).
        Map<Long, SkillReviewScheduleDto> schedules = reviewScheduleDao.findByUserId(userId);
        Map<Long, LocalDateTime> dueAt = new HashMap<>();
        for (Map.Entry<Long, String> entry : highestTier.entrySet()) {
            LocalDateTime done = lastDone.get(entry.getKey());
            if (done == null || openReviewSkills.contains(entry.getKey())) {
                continue;
            }
            SkillReviewScheduleDto schedule = schedules.get(entry.getKey());
            LocalDateTime due = schedule != null && schedule.getDueAt() != null
                    ? schedule.getDueAt()
                    : done.plusDays(reviewIntervalDays(entry.getValue()));
            if (!now.isBefore(due)) {
                dueAt.put(entry.getKey(), due);
            }
        }
        List<Long> dueSkills = dueAt.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(room)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (dueSkills.isEmpty()) {
            return 0;
        }

        final int startOrder = maxOrder + 1;
        return TransactionUtil.runInTransaction(conn -> {
            int order = startOrder;
            for (Long skillId : dueSkills) {
                SkillDto skill = skillDao.findById(skillId);
                String name = skill == null ? "기술" : skill.getSkillName();
                long days = java.time.Duration.between(lastDone.get(skillId), now).toDays();
                String reason = name + " 복습 — 마지막으로 익힌 지 " + days
                        + "일이 지났어요. 핵심 개념을 다시 떠올려 짧게 정리해 보세요.";
                order = stepWriter.insertStep(conn, primary.getId(), order, STEP_TYPE_REVIEW, TIER_REVIEW, null, skillId, reason);
            }
            return dueSkills.size();
        });
    }

    /**
     * 자기 평가로 다음 복습일을 다시 잡는다(SM-2). 복습 단계에 기술이 없으면(이론상 없다) 아무것도 안 한다.
     * 같은 트랜잭션 안에서 쓴다 — 점수는 들어갔는데 다음 일정이 안 잡히면 그 기술이 영원히 안 돌아온다.
     */
    private void updateSchedule(java.sql.Connection conn, Long userId, Long skillId,
                                SpacedRepetition.Recall recall, LocalDateTime reviewedAt) throws SQLException {
        if (skillId == null) {
            return;
        }
        SkillReviewScheduleDto previous = reviewScheduleDao.find(conn, userId, skillId);
        String tier = highestCompletedTier(userId, skillId);
        SpacedRepetition.Next next = SpacedRepetition.next(recall,
                previous == null ? 0 : previous.getIntervalDays(),
                previous == null ? SpacedRepetition.DEFAULT_EASE : previous.getEaseFactor(),
                previous == null ? 0 : previous.getRepetitions(),
                reviewIntervalDays(tier));

        SkillReviewScheduleDto schedule = new SkillReviewScheduleDto();
        schedule.setUserId(userId);
        schedule.setSkillId(skillId);
        schedule.setEaseFactor(next.easeFactor());
        schedule.setIntervalDays(next.intervalDays());
        schedule.setRepetitions(next.repetitions());
        schedule.setLastQuality(recall.getQuality());
        schedule.setLastReviewedAt(reviewedAt);
        schedule.setDueAt(reviewedAt.plusDays(next.intervalDays()));
        reviewScheduleDao.upsert(conn, schedule);
    }

    /** 그 기술로 도달한 가장 높은 티어 — 첫 복습의 간격(티어 기본 주기)을 정한다 */
    private String highestCompletedTier(Long userId, Long skillId) throws SQLException {
        String highest = null;
        for (RoadmapStepDto row : roadmapStepDao.findCompletedSkillRowsByUser(userId)) {
            if (!java.util.Objects.equals(skillId, row.getRelatedSkillId())
                    || STEP_TYPE_REVIEW.equals(row.getStepType())) {
                continue;
            }
            if (highest == null || SKILL_TIER_ORDER.indexOf(row.getTier()) > SKILL_TIER_ORDER.indexOf(highest)) {
                highest = row.getTier();
            }
        }
        return highest;
    }

    /**
     * 복습 단계를 끝낸다 — 복습 기록(REVIEW_NOTE_MIN_LENGTH자 이상)을 내야 하고, 점수는 reviewPoints로 감쇠한다.
     * @return 받은 점수. 이미 끝났거나 소유자가 아니면 0
     */
    public int completeReview(Long userId, Long stepId, String note) throws SQLException {
        return completeReview(userId, stepId, note, SpacedRepetition.Recall.NORMAL);
    }

    /**
     * @param recall 얼마나 기억났는지 — 이 값으로 다음 복습일을 기술마다 다르게 잡는다(SM-2, 2026-10-08).
     *               예전 두 인자 버전은 "보통"으로 본다.
     */
    public int completeReview(Long userId, Long stepId, String note, SpacedRepetition.Recall recall)
            throws SQLException {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() < REVIEW_NOTE_MIN_LENGTH) {
            throw new IllegalArgumentException("복습 기록을 " + REVIEW_NOTE_MIN_LENGTH + "자 이상 적어주세요.");
        }
        if (trimmed.length() > REVIEW_NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("복습 기록은 " + REVIEW_NOTE_MAX_LENGTH + "자 이내로 적어주세요.");
        }
        return TransactionUtil.runInTransaction(conn -> {
            RoadmapStepDto step = roadmapStepDao.findByIdForUser(conn, stepId, userId);
            if (step == null) {
                return 0;
            }
            if (!STEP_TYPE_REVIEW.equals(step.getStepType())) {
                throw new IllegalArgumentException("복습 단계가 아닙니다.");
            }
            if (step.isCompleted()) {
                return 0;
            }
            int prior = (int) roadmapStepDao.findCompletedSkillRowsByUser(userId).stream()
                    .filter(r -> STEP_TYPE_REVIEW.equals(r.getStepType())
                            && java.util.Objects.equals(r.getRelatedSkillId(), step.getRelatedSkillId()))
                    .count();
            int points = reviewPoints(prior);
            LocalDateTime completedAt = AppClock.now();
            roadmapStepDao.updateProof(conn, stepId, userId, PROOF_REVIEW_NOTE, trimmed, null,
                    SkillProofGrader.PASSED, "복습 기록 제출", true, completedAt);
            scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId, points);
            updateSchedule(conn, userId, step.getRelatedSkillId(), recall, completedAt);
            return points;
        });
    }
}
