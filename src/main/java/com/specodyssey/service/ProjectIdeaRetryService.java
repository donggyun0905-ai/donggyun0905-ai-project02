package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.specodyssey.service.RoadmapConstants.TIER_ENTRY;

/**
 * AI 프로젝트 추천 다시 시도 (FR-111 "재시도 버튼", 2026-10-06).
 *
 * 로드맵을 만들 때 AI 추천이 실패하면 PROJECT 단계에 기본 문구(RoadmapGenerator.PROJECT_FALLBACK_PREFIX)가 들어간다.
 * "로드맵 다시 만들기"는 바뀐 부분만 반영하므로 이미 있는 PROJECT 단계의 문구는 다시 만들지 않는다 —
 * 그래서 배너의 "다시 시도"는 이 클래스로 와서 그 단계의 문구만 다시 받는다. 다른 단계와 완료 기록은 그대로다.
 * 추천 기술은 처음 만들 때처럼 같은 로드맵의 입문(ENTRY) 기술 단계를 순서대로 쓴다.
 * 또 실패하면 RoadmapGenerator가 안내를 다시 남기고 아무것도 바꾸지 않는다.
 */
public class ProjectIdeaRetryService {

    private final RoadmapGenerator generator;
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final JobDao jobDao = new JobDao();

    public ProjectIdeaRetryService(RoadmapGenerator generator) {
        this.generator = generator;
    }

    /** @return 문구를 바꾼 단계 수. 바꿀 단계가 없거나 또 실패하면 0 */
    public int retry(Long userId) throws SQLException {
        RoadmapDto primary = roadmapDao.findPrimaryByUserId(userId);
        if (primary == null) {
            return 0;
        }
        List<RoadmapStepDto> steps = roadmapStepDao.findByRoadmapId(primary.getId());
        List<Long> targets = new ArrayList<>();
        Set<Long> entrySkillIds = new LinkedHashSet<>();
        for (RoadmapStepDto step : steps) {
            if ("PROJECT".equals(step.getStepType()) && !step.isCompleted() && step.getReason() != null
                    && step.getReason().startsWith(RoadmapGenerator.PROJECT_FALLBACK_PREFIX)) {
                targets.add(step.getId());
            } else if ("SKILL".equals(step.getStepType()) && TIER_ENTRY.equals(step.getTier())
                    && step.getRelatedSkillId() != null) {
                entrySkillIds.add(step.getRelatedSkillId());
            }
        }
        if (targets.isEmpty() || entrySkillIds.isEmpty()) {
            return 0;
        }

        // LLM 호출은 DB 트랜잭션을 열기 전에 끝낸다
        GapAnalysisDto analysis = primary.getGapAnalysisId() == null ? null : gapAnalysisDao.findById(primary.getGapAnalysisId());
        JobDto job = analysis == null ? null : jobDao.findById(analysis.getJobId());
        String reason = generator.buildProjectReason(job, new ArrayList<>(entrySkillIds));
        if (reason.startsWith(RoadmapGenerator.PROJECT_FALLBACK_PREFIX)) {
            return 0;
        }
        return TransactionUtil.runInTransaction(conn -> {
            int updated = 0;
            for (Long stepId : targets) {
                updated += roadmapStepDao.updateReason(conn, stepId, userId, reason);
            }
            return updated;
        });
    }
}
