package com.specodyssey.controller;

import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.service.ScoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;

/**
 * 점수 등급(LEVEL_TIER)이 방금 올랐을 때 한 번만 보여주는 환영 창 처리. RoadmapServlet 안에 있던 것을 그대로 옮겼다.
 */
class TierCelebration {

    private final ScoreService scoreService = new ScoreService();

    private static final String CELEBRATION_COMPLETED_KEY = "roadmapCelebrateTier";

    // 티어는 로드맵 단계(입문·핵심·심화·전문가)가 아니라 점수로 오르는 LEVEL_TIER(비기너→…→취뽀)다.
    // 요약 행이 없는(점수를 한 번도 못 쌓은) 계정은 0점 기준 등급으로 본다.
    Long currentScoreTierId(Long userId) throws SQLException {
        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        if (summary != null && summary.getCurrentTierId() != null) {
            return summary.getCurrentTierId();
        }
        LevelTierDto base = scoreService.getTierForScore(summary == null || summary.getTotalScore() == null
                ? 0 : summary.getTotalScore());
        return base == null ? null : base.getId();
    }

    // 이번 요청으로 점수 등급(LEVEL_TIER)이 방금 올랐으면 새 등급을 세션에 한 번만 쓸 수 있게 실어둔다.
    void recordTierCelebration(HttpServletRequest req, Long userId, Long tierIdBefore) throws SQLException {
        Long tierIdAfter = currentScoreTierId(userId);
        if (tierIdBefore == null || tierIdAfter == null || tierIdBefore.equals(tierIdAfter)) {
            return;
        }
        LevelTierDto before = scoreService.getTier(tierIdBefore);
        LevelTierDto after = scoreService.getTier(tierIdAfter);
        if (before == null || after == null || after.getMinScore() <= before.getMinScore()) {
            return;
        }
        req.getSession(false).setAttribute(CELEBRATION_COMPLETED_KEY, tierIdAfter);
    }

    void consumeTierCelebration(HttpServletRequest req) throws SQLException {
        HttpSession session = req.getSession(false);
        Object tierId = session.getAttribute(CELEBRATION_COMPLETED_KEY);
        if (!(tierId instanceof Long id)) {
            return;
        }
        session.removeAttribute(CELEBRATION_COMPLETED_KEY);
        LevelTierDto tier = scoreService.getTier(id);
        String logoPath = scoreService.getTierLogoPath(id);
        if (tier == null || logoPath == null) {
            return;
        }
        req.setAttribute("celebrateTierName", tier.getTierName());
        req.setAttribute("celebrateTierImage", logoPath);
    }
}
