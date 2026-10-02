package com.specodyssey.service;

import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dto.RoadmapStepDto;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * ROADMAP_STEP 한 줄을 만들어 넣는 공용 헬퍼 — 로드맵 생성(RoadmapGenerator)과 복습 단계 생성(RoadmapReviewService)이 같이 쓴다.
 */
final class RoadmapStepWriter {

    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();

    int insertStep(Connection conn, Long roadmapId, int order, String stepType, String tier,
                            Long certificationId, Long relatedSkillId, String reason) throws SQLException {
        return insertStep(conn, roadmapId, order, stepType, tier, certificationId, relatedSkillId, reason, false);
    }

    // alreadyDone: 재분석으로 새 버전이 만들어질 때 예전에 이미 완료했던 스킬을 다시 미완료로 되돌리지
    // 않기 위한 승계 플래그(팀 합의, 2026-09-23) — "딴 걸 또 따라고 시키면 안 된다". CERT는
    // findSuggestedCertification이 이미 보유한 자격증을 애초에 후보에서 빼기 때문에 여기서 따로
    // 다룰 필요가 없다. 승계된 단계는 새로 완료한 게 아니라서 점수를 다시 주지 않는다(호출부 참고).
    int insertStep(Connection conn, Long roadmapId, int order, String stepType, String tier,
                            Long certificationId, Long relatedSkillId, String reason, boolean alreadyDone)
            throws SQLException {
        return insertStep(conn, roadmapId, order, stepType, tier, certificationId, relatedSkillId, reason, alreadyDone, null);
    }

    // 프로젝트에 매달린 단계(프로젝트 업데이트)는 evidence_project_id로 어느 프로젝트인지 가리킨다
    int insertStep(Connection conn, Long roadmapId, int order, String stepType, String tier,
                            Long certificationId, Long relatedSkillId, String reason, boolean alreadyDone,
                            Long evidenceProjectId)
            throws SQLException {
        RoadmapStepDto step = new RoadmapStepDto();
        step.setRoadmapId(roadmapId);
        step.setStepOrder(order);
        step.setStepType(stepType);
        step.setTier(tier);
        step.setCertificationId(certificationId);
        step.setRelatedSkillId(relatedSkillId);
        step.setReason(reason);
        step.setCompleted(alreadyDone);
        step.setCompletedAt(alreadyDone ? LocalDateTime.now() : null);
        step.setEvidenceProjectId(evidenceProjectId);
        roadmapStepDao.insert(conn, step);
        return order + 1;
    }
}
