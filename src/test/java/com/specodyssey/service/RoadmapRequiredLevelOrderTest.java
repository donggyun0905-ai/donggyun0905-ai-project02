package com.specodyssey.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 부족 기술을 "직무가 얼마나 깊이 요구하는가" 순으로 줄 세운다 (2026-10-14).
 *
 * 2026-10-14에 JOB_REQUIRED_SKILL.required_level 이 로드맵 티어와 같은 4단계가 되면서(sql/40)
 * 이 값을 순위에 쓸 수 있게 됐다. 그 전에는 BASIC·INTERMEDIATE·ADVANCED 3단계로 따로 놀아
 * 중요도(필수/우대)만 봤다.
 *
 * 여기서는 깊이 계산만 고정한다 — 실제 정렬은 DB를 쓰는 RoadmapServiceTest 가 덮는다.
 */
class RoadmapRequiredLevelOrderTest {

    @Test
    @DisplayName("깊이는 입문 < 핵심 < 심화 < 전문가 순으로 커진다")
    void 깊이가_티어_순서를_따른다() {
        assertEquals(1, RoadmapGenerator.depthOf("ENTRY"));
        assertEquals(2, RoadmapGenerator.depthOf("CORE"));
        assertEquals(3, RoadmapGenerator.depthOf("ADVANCED"));
        assertEquals(4, RoadmapGenerator.depthOf("EXPERT"));

        assertTrue(RoadmapGenerator.depthOf("EXPERT") > RoadmapGenerator.depthOf("ADVANCED"));
        assertTrue(RoadmapGenerator.depthOf("CORE") > RoadmapGenerator.depthOf("ENTRY"));
    }

    @Test
    @DisplayName("수준이 없으면 0 — 맨 뒤로 간다. 없는 것을 깊다고 보지 않는다")
    void 수준이_없으면_맨_뒤다() {
        // null 은 실제로 들어온다 — 직무 요구 기술 목록에 없는 기술(프로필에서 들어온 것)은
        // 수준을 모른다. SKILL_TIER_ORDER 가 List.of 라 indexOf(null) 이 NPE 를 던지므로
        // 따로 걸러야 한다(이 테스트가 그 회귀를 막는다).
        assertEquals(0, RoadmapGenerator.depthOf(null));
        assertTrue(RoadmapGenerator.depthOf(null) < RoadmapGenerator.depthOf("ENTRY"));
    }

    @Test
    @DisplayName("옛 어휘가 남아 있어도 터지지 않고 맨 뒤로 간다")
    void 모르는_값도_안전하다() {
        // sql/40 을 아직 안 돌린 DB, 또는 손으로 넣은 값
        for (String old : new String[] {"BASIC", "INTERMEDIATE", "심화", ""}) {
            assertEquals(0, RoadmapGenerator.depthOf(old), old);
        }
    }
}
