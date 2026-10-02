package com.specodyssey.service;

import java.sql.SQLException;
import java.util.List;

/**
 * 사용자가 입력한 기술명(raw)을 SKILL 마스터의 한 항목으로 매칭한다.
 * 구현체: ExactMatcher(이름 일치) — 임베딩 기반 EmbeddingMatcher가 나오면 교체한다.
 */
public interface SkillMatcher {

    /** 매칭 결과. 매칭 실패 시 skillId는 null, score는 0. */
    record MatchResult(Long skillId, double score) {
        public static MatchResult none() {
            return new MatchResult(null, 0.0);
        }
    }

    MatchResult match(String raw) throws SQLException;

    /**
     * 입력 하나에 기술이 여러 개 들어 있을 때(예: "Java Spring") 찾은 것을 모두 돌려준다. 못 찾으면 빈 목록.
     * 기본 구현은 match() 결과 하나다 — 여러 개를 찾을 수 있는 구현체가 덮어쓴다 (2026-10-02 추가).
     */
    default List<MatchResult> matchAll(String raw) throws SQLException {
        MatchResult result = match(raw);
        return result.skillId() == null ? List.of() : List.of(result);
    }
}
