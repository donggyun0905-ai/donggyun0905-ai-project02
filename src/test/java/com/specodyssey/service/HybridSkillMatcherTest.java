package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 하이브리드 기술명 매칭 (2026-10-08).
 *
 * 가장 중요한 성질은 "기존 동작을 바꾸지 않는다"다 — 종속(정확 일치·별칭·편집거리·임베딩)이 뭐라도
 * 찾으면 그대로 돌려주고, 못 찾았을 때만 RRF가 돈다. 그 경계와 글자 유사도 계산을 여기서 고정한다.
 * (RRF 융합 자체는 RankFusionTest, 실제 카탈로그와의 매칭은 SkillMatcherHybridDbTest)
 */
class HybridSkillMatcherTest {

    /** 정해진 답만 돌려주는 종속 */
    private static SkillMatcher delegate(List<SkillMatcher.MatchResult> answer) {
        return new SkillMatcher() {
            @Override
            public MatchResult match(String raw) {
                return answer.isEmpty() ? MatchResult.none() : answer.get(0);
            }

            @Override
            public List<MatchResult> matchAll(String raw) {
                return answer;
            }
        };
    }

    @Test
    void 종속이_찾으면_그대로_돌려준다() throws SQLException {
        SkillMatcher.MatchResult exact = new SkillMatcher.MatchResult(7L, 1.0);
        HybridSkillMatcher matcher = new HybridSkillMatcher(delegate(List.of(exact)));

        assertEquals(List.of(exact), matcher.matchAll("Java"));
        assertEquals(7L, matcher.match("Java").skillId(), "사전 일치를 덮어쓰면 안 된다");
    }

    @Test
    void 종속이_여러_개를_찾아도_그대로_돌려준다() throws SQLException {
        List<SkillMatcher.MatchResult> two = List.of(
                new SkillMatcher.MatchResult(1L, 1.0),
                new SkillMatcher.MatchResult(2L, 1.0));
        HybridSkillMatcher matcher = new HybridSkillMatcher(delegate(two));

        assertEquals(two, matcher.matchAll("Java Spring"), "단어 단위로 찾은 여러 개를 줄이면 안 된다");
    }

    @Test
    void 빈_입력은_종속에_맡기고_융합하지_않는다() throws SQLException {
        HybridSkillMatcher matcher = new HybridSkillMatcher(delegate(List.of()));

        assertTrue(matcher.matchAll(null).isEmpty());
        assertTrue(matcher.matchAll("   ").isEmpty());
        assertNull(matcher.match("").skillId());
    }

    // ---------------------------------------------------------------- 글자 유사도

    @Test
    void 같은_이름은_1점이다() {
        assertEquals(1.0, HybridSkillMatcher.similarity("springboot", "springboot"), 1e-9);
    }

    @Test
    void 공백만_다른_이름은_아주_높다() {
        // 정규화 뒤라 둘 다 springboot가 되지만, 정규화 전 길이 차이가 나는 쌍도 잘 잡혀야 한다
        assertTrue(HybridSkillMatcher.similarity("springbot", "springboot") > 0.8,
                "실제: " + HybridSkillMatcher.similarity("springbot", "springboot"));
    }

    @Test
    void 전혀_다른_이름은_낮다() {
        double score = HybridSkillMatcher.similarity("kubernetes", "javascript");

        assertTrue(score < HybridSkillMatcher.MIN_LEXICAL, "실제: " + score);
    }

    @Test
    void 두글자묶음이_편집거리보다_유리한_쌍도_잡는다() {
        // 앞에 글자가 붙어 편집거리는 깎이지만 2-gram 겹침은 높다
        double edit = 1.0 - (double) com.specodyssey.util.EditDistanceUtil
                .transpositionAwareDistance("myspringboot", "springboot") / 12.0;
        double combined = HybridSkillMatcher.similarity("myspringboot", "springboot");

        assertTrue(combined >= edit, "둘 중 높은 쪽을 쓴다 — 편집 " + edit + " vs 합산 " + combined);
        assertTrue(combined > 0.7, "실제: " + combined);
    }

    @Test
    void 한_글자_입력도_계산이_깨지지_않는다() {
        assertEquals(0.0, HybridSkillMatcher.bigramDice("", "java"), 1e-9);
        assertTrue(HybridSkillMatcher.bigramDice("j", "java") >= 0.0);
        assertEquals(0.0, HybridSkillMatcher.similarity("", "java"), 1e-9);
    }

    @Test
    void 두글자묶음_Dice는_공통_비율로_계산된다() {
        // "abc" → ab, bc / "abd" → ab, bd → 공통 1개 → 2*1/(2+2) = 0.5
        assertEquals(0.5, HybridSkillMatcher.bigramDice("abc", "abd"), 1e-9);
        assertEquals(1.0, HybridSkillMatcher.bigramDice("abc", "abc"), 1e-9);
        assertEquals(0.0, HybridSkillMatcher.bigramDice("ab", "cd"), 1e-9);
    }

    @Test
    void 합의_기준이_너무_느슨하지_않다() {
        // 설계 전제 — 상위 3등 안에서만 합의로 본다. 후보 10개 중 하나만 겹쳐도 받으면 오탐이 늘어난다.
        assertTrue(HybridSkillMatcher.AGREEMENT_RANK < HybridSkillMatcher.CANDIDATES);
        assertEquals(3, HybridSkillMatcher.AGREEMENT_RANK);
        // 각 신호의 바닥은 종속(0.75)보다 낮다 — 합의가 확신을 대신하기 때문이다
        assertTrue(HybridSkillMatcher.MIN_LEXICAL < 0.75);
        assertTrue(HybridSkillMatcher.MIN_SEMANTIC < 0.75);
    }
}
