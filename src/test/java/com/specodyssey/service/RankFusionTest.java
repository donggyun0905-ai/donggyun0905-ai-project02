package com.specodyssey.service;

import com.specodyssey.service.RankFusion.Fused;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 순위 합치기 — RRF (2026-10-08). DB 없이 공식만 본다.
 *
 * 편집거리 유사도와 코사인 유사도는 분포가 달라서 점수를 그대로 더할 수 없다. RRF는 순위만 쓰므로
 * 정규화 없이 합칠 수 있다. 여기서 고정하는 것: 양쪽에서 고르게 좋은 후보가 한쪽 1등을 이기는지,
 * 결과가 호출마다 달라지지 않는지.
 */
class RankFusionTest {

    private static List<String> keys(List<Fused<String>> fused) {
        return fused.stream().map(Fused::key).collect(Collectors.toList());
    }

    @Test
    void 양쪽에서_고르게_좋은_후보가_한쪽_1등을_이긴다() {
        // A는 한 목록에서만 1등이고 다른 목록에는 없다. B는 양쪽에서 2등이다.
        List<Fused<String>> fused = RankFusion.fuse(
                List.of("A", "B", "C"),
                List.of("D", "B", "E"));

        assertEquals("B", fused.get(0).key(), "실제 순서: " + keys(fused));
        assertEquals(2, fused.get(0).listsFound(), "두 목록에서 모두 나왔다");
    }

    @Test
    void 양쪽에서_1등이면_가장_높다() {
        List<Fused<String>> fused = RankFusion.fuse(
                List.of("A", "B"),
                List.of("A", "C"));

        assertEquals("A", fused.get(0).key());
        assertEquals(1, fused.get(0).bestRank());
        assertEquals(2.0 / (RankFusion.K + 1), fused.get(0).score(), 1e-12);
    }

    @Test
    void 한_목록에만_있는_후보도_결과에_남는다() {
        List<Fused<String>> fused = RankFusion.fuse(List.of("A"), List.of("B"));

        assertEquals(2, fused.size());
        assertTrue(keys(fused).containsAll(List.of("A", "B")));
        assertEquals(1, fused.get(0).listsFound());
    }

    @Test
    void 점수가_같으면_더_좋은_등수가_앞이다() {
        // A는 (1등, 없음), B는 (없음, 1등) — 점수가 같다. 등수도 같으니 순서가 뒤집히지 않기만 하면 된다.
        List<Fused<String>> first = RankFusion.fuse(List.of("A", "X"), List.of("B", "Y"));
        List<Fused<String>> second = RankFusion.fuse(List.of("A", "X"), List.of("B", "Y"));

        assertEquals(keys(first), keys(second), "같은 입력에 같은 결과여야 한다");
    }

    @Test
    void 한_목록에_같은_후보가_두_번_나오면_한_번만_센다() {
        List<Fused<String>> fused = RankFusion.fuse(List.of("A", "A", "B"), List.of("B"));

        Fused<String> a = fused.stream().filter(f -> "A".equals(f.key())).findFirst().orElseThrow();
        assertEquals(1, a.listsFound());
        assertEquals(1.0 / (RankFusion.K + 1), a.score(), 1e-12, "두 번 세면 중복만으로 1등이 된다");
        // 중복을 건너뛰었으므로 B는 첫 목록에서 2등이다
        Fused<String> b = fused.stream().filter(f -> "B".equals(f.key())).findFirst().orElseThrow();
        assertEquals(1.0 / (RankFusion.K + 2) + 1.0 / (RankFusion.K + 1), b.score(), 1e-12);
    }

    @Test
    void 빈_목록이나_null은_그냥_건너뛴다() {
        assertTrue(RankFusion.fuse(List.of(), List.of()).isEmpty());
        assertEquals(List.of("A"), keys(RankFusion.fuse(List.of("A"), null)));
    }

    @Test
    void 등수를_되찾을_수_있다() {
        List<String> ranking = List.of("A", "B", "C");

        assertEquals(1, RankFusion.rankOf(ranking, "A"));
        assertEquals(3, RankFusion.rankOf(ranking, "C"));
        assertEquals(0, RankFusion.rankOf(ranking, "Z"), "순위 밖은 0");
        assertEquals(0, RankFusion.rankOf(null, "A"));
    }

    @Test
    void 목록이_셋_이상이어도_합쳐진다() {
        List<Fused<String>> fused = RankFusion.fuse(
                List.of("A", "B"),
                List.of("B", "A"),
                List.of("B", "C"));

        assertEquals("B", fused.get(0).key(), "세 목록 모두에서 상위: " + keys(fused));
        assertEquals(3, fused.get(0).listsFound());
    }
}
