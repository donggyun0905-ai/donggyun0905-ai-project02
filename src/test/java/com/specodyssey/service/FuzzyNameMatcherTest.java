package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FuzzyNameMatcher 통합테스트 — 실제 SKILL 테이블에 대고 검증한다(TD-1 임베딩 전 중간 단계, 2026-09-30).
 */
class FuzzyNameMatcherTest {

    private final FuzzyNameMatcher matcher = new FuzzyNameMatcher();
    private Long skillId;
    private String skillName;

    @BeforeEach
    void setUp() throws Exception {
        skillName = "리액트_" + System.nanoTime();
        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, skillName);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "SKILL", skillId);
        }
    }

    @Test
    void 정확히_같은_이름은_score_1점으로_매칭된다() throws Exception {
        SkillMatcher.MatchResult result = matcher.match(skillName);

        assertEquals(skillId, result.skillId());
        assertEquals(1.0, result.score());
    }

    @Test
    void 오타가_한_글자면_그래도_매칭되고_점수는_1보다_낮다() throws Exception {
        // 이름 끝 글자 하나만 다르게(오타 흉내) — 길이의 40% 문턱 안에 들어온다.
        String typo = skillName.substring(0, skillName.length() - 1) + "X";

        SkillMatcher.MatchResult result = matcher.match(typo);

        assertEquals(skillId, result.skillId());
        assertTrue(result.score() < 1.0 && result.score() > 0.5);
    }

    @Test
    void 완전히_다른_문자열은_매칭되지_않는다() throws Exception {
        SkillMatcher.MatchResult result = matcher.match("전혀 상관없는 임의의 긴 문자열 완전히 다름");

        assertNull(result.skillId());
        assertEquals(0.0, result.score());
    }

    @Test
    void 빈_문자열이나_null은_매칭되지_않는다() throws Exception {
        assertNull(matcher.match("").skillId());
        assertNull(matcher.match(null).skillId());
        assertNull(matcher.match("   ").skillId());
    }

    // SKILL_ALIAS 사전(2026-09-30, "이름 일치라도") — 정확 일치하는 별칭은 score 1.0으로 취급한다.
    @Test
    void 등록된_별칭과_정확히_일치하면_score_1점으로_매칭된다() throws Exception {
        String aliasName = "리엑트별칭_" + System.nanoTime();
        Long aliasId;
        try (Connection conn = DBUtil.getConnection()) {
            aliasId = TestFixtures.insertSkillAlias(conn, skillId, aliasName);
        }
        try {
            SkillMatcher.MatchResult result = matcher.match(aliasName);

            assertEquals(skillId, result.skillId());
            assertEquals(1.0, result.score());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SKILL_ALIAS", aliasId);
            }
        }
    }

    @Test
    void 별칭에_오타가_있어도_퍼지_매칭으로_잡힌다() throws Exception {
        String aliasName = "리엑트별칭투_" + System.nanoTime();
        Long aliasId;
        try (Connection conn = DBUtil.getConnection()) {
            aliasId = TestFixtures.insertSkillAlias(conn, skillId, aliasName);
        }
        try {
            String typoAlias = aliasName.substring(0, aliasName.length() - 1) + "Z";

            SkillMatcher.MatchResult result = matcher.match(typoAlias);

            assertEquals(skillId, result.skillId());
            assertTrue(result.score() < 1.0 && result.score() > 0.5);
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SKILL_ALIAS", aliasId);
            }
        }
    }
}
