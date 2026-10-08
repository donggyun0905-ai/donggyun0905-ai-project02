package com.specodyssey.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여러 순위를 하나로 합친다 — RRF(Reciprocal Rank Fusion, 2026-10-08).
 * 관련 요구사항: FR-23 · 25 기술 입력 매칭 (TD-1 시맨틱 매칭)
 *
 * 기술명 매칭은 "정확 일치 → 편집거리 → 임베딩"을 차례로 시도한다. 각 단계가 자기 임계값을 못 넘으면
 * 거기서 끝이라, <b>두 신호가 모두 "조금 비슷하다"고 말하는 경우</b>를 놓친다. 점수를 그대로 더할 수도
 * 없다 — 편집거리 유사도(0~1)와 코사인 유사도(0~1)는 분포가 전혀 달라서 한쪽이 항상 이긴다.
 *
 * RRF는 점수가 아니라 <b>순위</b>만 쓴다: score(d) = Σ 1/(k + rank(d)). 척도가 다른 신호를 합칠 때
 * 정규화 없이 쓸 수 있는 것이 장점이고, 정보 검색에서 널리 쓰는 방법이다.
 * k는 관례대로 60 — 작으면 1등에 너무 쏠리고 크면 순위 차이가 뭉개진다.
 *
 * 상태가 없는 순수 계산이라 DB 없이 테스트한다.
 */
public final class RankFusion {

    /** RRF 관례값. 1등과 2등의 차이를 적당히 남긴다. */
    static final int K = 60;

    private RankFusion() {
    }

    /** 합친 결과 한 줄 — 어느 순위에서 몇 등이었는지까지 남긴다(왜 이게 1등인지 설명하려고). */
    public record Fused<T>(T key, double score, int bestRank, int listsFound) {

        public T getKey() {
            return key;
        }

        public double getScore() {
            return score;
        }

        public int getBestRank() {
            return bestRank;
        }

        public int getListsFound() {
            return listsFound;
        }
    }

    /**
     * 순위 목록들을 RRF로 합쳐 점수 높은 순으로 돌려준다.
     * 같은 점수면 더 좋은 등수를 받은 쪽이 앞 — 결과가 호출마다 달라지지 않게 한다.
     *
     * @param rankings 각 목록은 1등부터 순서대로. 같은 key가 한 목록에 두 번 나오면 첫 번째만 센다
     */
    @SafeVarargs
    public static <T> List<Fused<T>> fuse(List<T>... rankings) {
        Map<T, double[]> accumulated = new LinkedHashMap<>(); // [점수, 최고 등수, 나온 목록 수]
        for (List<T> ranking : rankings) {
            if (ranking == null) {
                continue;
            }
            int rank = 0;
            Map<T, Boolean> seenInThisList = new LinkedHashMap<>();
            for (T key : ranking) {
                if (key == null || seenInThisList.put(key, Boolean.TRUE) != null) {
                    continue;
                }
                rank++;
                double[] slot = accumulated.computeIfAbsent(key,
                        k -> new double[] {0.0, Double.MAX_VALUE, 0.0});
                slot[0] += 1.0 / (K + rank);
                slot[1] = Math.min(slot[1], rank);
                slot[2] += 1;
            }
        }

        List<Fused<T>> fused = new ArrayList<>();
        accumulated.forEach((key, slot) ->
                fused.add(new Fused<>(key, slot[0], (int) slot[1], (int) slot[2])));
        fused.sort(Comparator.comparingDouble((Fused<T> f) -> -f.score())
                .thenComparingInt(Fused::bestRank));
        return fused;
    }

    /** 그 key가 이 목록에서 몇 등인지 — 1부터. 없으면 0 (호출부가 "순위 밖"으로 본다). */
    static <T> int rankOf(List<T> ranking, T key) {
        if (ranking == null) {
            return 0;
        }
        for (int i = 0; i < ranking.size(); i++) {
            if (key.equals(ranking.get(i))) {
                return i + 1;
            }
        }
        return 0;
    }
}
