package com.specodyssey.service;

import com.specodyssey.service.SkillCooccurrenceService.Ranked;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 함께 익힌 기술 추천 — 자카드 + 동시 출현 (2026-10-08). DB 없이 계산만 본다.
 *
 * 여기서 고정하는 것: 인기만 높은 기술보다 "나와 비슷한 사람이 가진 기술"이 앞서는지,
 * 표본이 모자라면 아무것도 추천하지 않는지(개인정보·신뢰도), 이미 가진 기술은 빼는지.
 */
class SkillCooccurrenceServiceTest {

    private static final long JAVA = 1L;
    private static final long SPRING = 2L;
    private static final long SQL = 3L;
    private static final long DOCKER = 4L;
    private static final long PHOTOSHOP = 5L;

    private static Map<Long, Set<Long>> peers(Set<Long>... skillSets) {
        Map<Long, Set<Long>> map = new LinkedHashMap<>();
        for (int i = 0; i < skillSets.length; i++) {
            map.put(100L + i, skillSets[i]);
        }
        return map;
    }

    private static List<Long> ids(List<Ranked> ranked) {
        return ranked.stream().map(Ranked::skillId).collect(Collectors.toList());
    }

    @Test
    void 나와_비슷한_사람이_가진_기술이_인기만_높은_기술보다_앞선다() {
        // 나: Java, Spring
        // 또래 1·2: Java, Spring + SQL  → 나와 매우 비슷하다(자카드 2/3)
        // 또래 3·4·5: Photoshop만 + Photoshop·Docker → 나와 겹치는 게 없다
        Map<Long, Set<Long>> peers = peers(
                Set.of(JAVA, SPRING, SQL),
                Set.of(JAVA, SPRING, SQL),
                Set.of(PHOTOSHOP),
                Set.of(PHOTOSHOP, DOCKER),
                Set.of(PHOTOSHOP, DOCKER));

        List<Ranked> ranked = SkillCooccurrenceService.rank(Set.of(JAVA, SPRING), peers);

        assertEquals(List.of(SQL), ids(ranked),
                "겹치는 게 없는 사람들이 가진 Photoshop·Docker는 추천이 아니다. 실제: " + ids(ranked));
        assertEquals(2, ranked.get(0).holders());
    }

    @Test
    void 이미_가진_기술은_추천하지_않는다() {
        Map<Long, Set<Long>> peers = peers(
                Set.of(JAVA, SPRING),
                Set.of(JAVA, SPRING),
                Set.of(JAVA, SPRING));

        List<Ranked> ranked = SkillCooccurrenceService.rank(Set.of(JAVA, SPRING), peers);

        assertTrue(ranked.isEmpty(), "새로 배울 게 없으면 빈 목록이다");
    }

    @Test
    void 또래가_세_명보다_적으면_아무것도_추천하지_않는다() {
        Map<Long, Set<Long>> twoPeers = peers(
                Set.of(JAVA, SQL),
                Set.of(JAVA, SQL));

        assertTrue(SkillCooccurrenceService.rank(Set.of(JAVA), twoPeers).isEmpty(),
                "표본이 적으면 '많이 익혔다'가 거짓말이고, 특정 개인의 프로필이 드러날 수 있다");
        assertEquals(3, SkillCooccurrenceService.MIN_PEERS);
    }

    @Test
    void 한_사람만_가진_기술은_추천하지_않는다() {
        Map<Long, Set<Long>> peers = peers(
                Set.of(JAVA, SQL),     // SQL 1명
                Set.of(JAVA, DOCKER),  // Docker 1명
                Set.of(JAVA));

        assertTrue(SkillCooccurrenceService.rank(Set.of(JAVA), peers).isEmpty(),
                "한 사람의 선택은 추천 근거가 못 된다");
        assertEquals(2, SkillCooccurrenceService.MIN_HOLDERS);
    }

    @Test
    void 두_사람_이상_가진_기술은_추천한다() {
        Map<Long, Set<Long>> peers = peers(
                Set.of(JAVA, SQL),
                Set.of(JAVA, SQL),
                Set.of(JAVA, DOCKER));

        List<Ranked> ranked = SkillCooccurrenceService.rank(Set.of(JAVA), peers);

        assertEquals(List.of(SQL), ids(ranked), "Docker는 1명뿐이라 빠진다. 실제: " + ids(ranked));
    }

    @Test
    void 내_기술이_없으면_비교할_수_없다() {
        Map<Long, Set<Long>> peers = peers(Set.of(JAVA), Set.of(JAVA), Set.of(JAVA));

        assertTrue(SkillCooccurrenceService.rank(Set.of(), peers).isEmpty());
        assertTrue(SkillCooccurrenceService.rank(null, peers).isEmpty());
    }

    @Test
    void 또래가_없거나_null이어도_깨지지_않는다() {
        assertTrue(SkillCooccurrenceService.rank(Set.of(JAVA), Map.of()).isEmpty());
        assertTrue(SkillCooccurrenceService.rank(Set.of(JAVA), null).isEmpty());
    }

    @Test
    void 기술이_비어_있는_또래는_건너뛴다() {
        Map<Long, Set<Long>> peers = new LinkedHashMap<>();
        peers.put(1L, Set.of(JAVA, SQL));
        peers.put(2L, Set.of(JAVA, SQL));
        peers.put(3L, Set.of());
        peers.put(4L, null);

        List<Ranked> ranked = SkillCooccurrenceService.rank(Set.of(JAVA), peers);

        assertEquals(List.of(SQL), ids(ranked), "null·빈 집합에 걸려 계산이 멈추면 안 된다");
    }

    @Test
    void 추천은_다섯_개까지만() {
        Map<Long, Set<Long>> peers = peers(
                Set.of(JAVA, 10L, 11L, 12L, 13L, 14L, 15L),
                Set.of(JAVA, 10L, 11L, 12L, 13L, 14L, 15L),
                Set.of(JAVA, 10L, 11L, 12L, 13L, 14L, 15L));

        List<Ranked> ranked = SkillCooccurrenceService.rank(Set.of(JAVA), peers);

        assertEquals(SkillCooccurrenceService.TOP_N, ranked.size());
    }

    @Test
    void 같은_점수면_항상_같은_순서가_나온다() {
        Map<Long, Set<Long>> peers = peers(
                Set.of(JAVA, SQL, DOCKER),
                Set.of(JAVA, SQL, DOCKER),
                Set.of(JAVA, SQL, DOCKER));

        List<Long> first = ids(SkillCooccurrenceService.rank(Set.of(JAVA), peers));
        List<Long> second = ids(SkillCooccurrenceService.rank(Set.of(JAVA), peers));

        assertEquals(first, second, "순서가 호출마다 바뀌면 사용자가 혼란스럽다");
        assertEquals(List.of(SQL, DOCKER), first, "동점이면 skillId 순");
    }

    // ---------------------------------------------------------------- 자카드

    @Test
    void 자카드는_교집합_나누기_합집합이다() {
        // {1,2} ∩ {2,3} = {2}, ∪ = {1,2,3} → 1/3
        assertEquals(1.0 / 3, SkillCooccurrenceService.jaccard(Set.of(1L, 2L), Set.of(2L, 3L)), 1e-9);
        assertEquals(1.0, SkillCooccurrenceService.jaccard(Set.of(1L, 2L), Set.of(1L, 2L)), 1e-9);
        assertEquals(0.0, SkillCooccurrenceService.jaccard(Set.of(1L), Set.of(2L)), 1e-9);
    }

    @Test
    void 자카드는_집합_크기_차이를_보정한다() {
        // 기술을 아주 많이 등록한 사람은 교집합이 커도 유사도가 높지 않다
        Set<Long> mine = Set.of(1L, 2L);
        double similar = SkillCooccurrenceService.jaccard(mine, Set.of(1L, 2L, 3L));
        double manySkills = SkillCooccurrenceService.jaccard(mine,
                Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));

        assertTrue(similar > manySkills,
                "비슷한 사람 " + similar + " vs 아무거나 많이 넣은 사람 " + manySkills);
    }

    @Test
    void 빈_집합이나_null은_0이다() {
        assertEquals(0.0, SkillCooccurrenceService.jaccard(Set.of(), Set.of(1L)), 1e-9);
        assertEquals(0.0, SkillCooccurrenceService.jaccard(Set.of(1L), Set.of()), 1e-9);
        assertEquals(0.0, SkillCooccurrenceService.jaccard(null, Set.of(1L)), 1e-9);
        assertEquals(0.0, SkillCooccurrenceService.jaccard(Set.of(1L), null), 1e-9);
    }
}
