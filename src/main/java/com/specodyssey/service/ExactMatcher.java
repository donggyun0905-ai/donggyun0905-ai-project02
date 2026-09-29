package com.specodyssey.service;

import java.sql.SQLException;

import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.SkillDto;

/**
 * SKILL.skill_name과 정확히 일치하는지만 본다 (utf8mb4_unicode_ci라 대소문자 무시).
 * 임베딩 매처가 준비되기 전까지 쓰는 기본 구현.
 */
public class ExactMatcher implements SkillMatcher {

    private final SkillDao skillDao = new SkillDao();

    @Override
    public MatchResult match(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return MatchResult.none();
        }
        SkillDto skill = skillDao.findByName(raw.trim());
        return skill == null ? MatchResult.none() : new MatchResult(skill.getId(), 1.0);
    }
}
