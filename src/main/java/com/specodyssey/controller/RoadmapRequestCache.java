package com.specodyssey.controller;

import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.service.RoadmapProgress;
import com.specodyssey.service.RoadmapService;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * 한 요청 안에서 로드맵·단계·진행도를 한 번만 읽는다.
 *
 * /roadmap·/dashboard는 위젯 필터(SideWidgetFilter → GlanceService)와 서블릿이 같은 데이터를 각자
 * 읽어서 getPrimaryRoadmap·getSteps가 요청마다 두 번씩 돌고 있었다(2026-10-06 성능 점검). 필터가
 * 먼저 읽어 둔 것을 서블릿이 그대로 쓴다.
 *
 * 화면을 그리는 동안에는 단계가 바뀌지 않으므로 요청 범위에서는 낡을 일이 없다. 단계를 실제로 바꾼
 * 뒤에는(복습·관리 단계 추가 등) invalidate()로 버려야 한다.
 */
public final class RoadmapRequestCache {

    private static final String ATTRIBUTE = RoadmapRequestCache.class.getName();

    /** roadmap이 null이면 아직 로드맵이 없는 사용자 — 이때 steps는 빈 목록이다. */
    public record Snapshot(RoadmapDto roadmap, List<RoadmapStepDto> steps, RoadmapProgress progress) {
    }

    private RoadmapRequestCache() {
    }

    public static Snapshot of(HttpServletRequest req, Long userId, RoadmapService service) throws SQLException {
        if (req.getAttribute(ATTRIBUTE) instanceof Snapshot cached) {
            return cached;
        }
        RoadmapDto roadmap = service.getPrimaryRoadmap(userId);
        List<RoadmapStepDto> steps = roadmap == null
                ? Collections.emptyList()
                : service.getSteps(roadmap.getId());
        Snapshot snapshot = new Snapshot(roadmap, steps, service.computeProgress(steps));
        req.setAttribute(ATTRIBUTE, snapshot);
        return snapshot;
    }

    public static void invalidate(HttpServletRequest req) {
        req.removeAttribute(ATTRIBUTE);
    }
}
