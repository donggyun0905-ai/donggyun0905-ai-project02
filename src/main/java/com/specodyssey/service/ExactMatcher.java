package com.specodyssey.service;

import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.SkillDto;

import java.sql.SQLException;

/**
 * 스킬 이름 정확 일치 매처. EmbeddingMatcher가 완성되기 전까지 쓰는 기본 구현.
 * 관련 요구사항: FR-31 (격차 분석의 보유/요구 스킬 대조)
 * 앞뒤 공백은 여기서 trim한다. 대소문자는 SKILL 테이블 콜레이션(utf8mb4_unicode_ci)이 무시해 주므로
 * 따로 바꾸지 않는다 — 콜레이션이 바뀌면 ExactMatcherTest의 대소문자 케이스가 깨져서 알려준다.
 */
public class ExactMatcher implements SkillMatcher {

    private static final double EXACT_SCORE = 1.0;

    private final SkillDao skillDao = new SkillDao();

    @Override
    public MatchResult match(String rawInput) throws SQLException {
        if (rawInput == null || rawInput.isBlank()) {
            return null;
        }
        SkillDto skill = skillDao.findByName(rawInput.trim());
        return skill == null ? null : new MatchResult(skill.getId(), EXACT_SCORE);
    }
}
