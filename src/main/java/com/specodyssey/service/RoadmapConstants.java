package com.specodyssey.service;

import java.util.List;

/**
 * 로드맵 서비스들이 함께 쓰는 상수 — 티어 이름과 순서, 완료 점수, 증빙 종류.
 * RoadmapService 한 파일에 있던 것을 그대로 옮겼다(값은 그대로).
 */
final class RoadmapConstants {

    // 시간이 지나 이어 붙는 "유지·성장" 단계 종류 — 사다리(입문→전문가) 밖에서 tier는 모두 REVIEW다
    static final String STEP_TYPE_PROJECT_UPDATE = "PROJECT_UPDATE";
    static final String STEP_TYPE_ARTICLE_UPDATE = "ARTICLE_UPDATE";
    static final String STEP_TYPE_TREND_STUDY = "TREND_STUDY";
    static final java.util.Set<String> UPKEEP_STEP_TYPES = java.util.Set.of(
            "REVIEW", STEP_TYPE_PROJECT_UPDATE, STEP_TYPE_ARTICLE_UPDATE, STEP_TYPE_TREND_STUDY);

    private RoadmapConstants() {
    }

    static final String TIER_ENTRY = "ENTRY";

    static final String TIER_CORE = "CORE";

    static final String TIER_ADVANCED = "ADVANCED";

    static final String TIER_EXPERT = "EXPERT";

    // 기술 하나가 입문(공부노트) → 핵심(프로젝트) → 심화(프로젝트 업그레이드) → 전문가(기술 설명 글)를
    // 차례로 거치는 "기술별 사다리" 구조(2026-10-01 팀 결정). 티어는 우선순위 묶음이 아니라 숙련 단계다.
    // 한 번에 다 펼치면 단계가 너무 많이 쏟아지므로, 한 로드맵(라운드)엔 우선순위 상위 기술
    // ROUND_SKILL_COUNT(SCORING_RULE)개만 담는다. 다 끝낸 기술은 프로필에 반영돼 다음 재분석에서 빠지므로
    // "재분석하면 다음 라운드가 이어진다"(db-design.md의 끝없는 여정).
    static final List<String> SKILL_TIER_ORDER = List.of(TIER_ENTRY, TIER_CORE, TIER_ADVANCED, TIER_EXPERT);

    // 로드맵 단계 완료 점수는 StepPointCalculator가 정한다(직무 사다리 총점을 가중치로 나눔 — SCORING_RULE)

    static final String SIGNAL_TYPE_ROADMAP = "ROADMAP";

    // SKILL 단계 학습 검증(2026-09-30 팀 결정) — tier별 증빙 방식. ROADMAP_STEP.proof_type에 저장.
    static final String PROOF_NOTE = "NOTE";

    static final String PROOF_PROJECT_LINK = "PROJECT_LINK";

    static final String PROOF_TEACHING_POST = "TEACHING_POST";

    static final String PROOF_CERT_DOCUMENT = "CERT_DOCUMENT";
}
