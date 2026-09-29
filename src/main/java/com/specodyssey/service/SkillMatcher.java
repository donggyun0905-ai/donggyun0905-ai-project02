package com.specodyssey.service;

import java.sql.SQLException;

/** 사용자가 입력한 기술 원문을 표준 스킬(SKILL)에 연결한다. */
public interface SkillMatcher {
    /** 못 찾으면 null. score는 0~1 (정확 일치는 1.0) */
    MatchResult match(String rawInput) throws SQLException;

    record MatchResult(Long skillId, double score) {}
}
