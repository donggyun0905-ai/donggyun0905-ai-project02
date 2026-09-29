package com.specodyssey.service;

import java.sql.SQLException;

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
}
