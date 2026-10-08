package com.specodyssey.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * D-day까지 무엇을 할지 — 0/1 배낭 문제 (2026-10-08).
 * 관련 요구사항: FR-71 · 72 D-day · FR-36 로드맵 진행
 *
 * D-day 화면은 "며칠 남았다"만 보여 줬다. 남은 기간에 무엇부터 하면 좋은지는 사용자가 직접 골라야 했다.
 * 남은 일수를 <b>배낭의 용량</b>, 단계의 소요 일수를 <b>무게</b>, 그 단계로 받는 점수를 <b>가치</b>로 보면
 * "이 기간에 점수를 가장 많이 올리는 조합"은 0/1 배낭 문제가 된다.
 *
 * <b>왜 그리디가 아니라 DP인가</b>: 점수/일수가 높은 것부터 담는 그리디는 용량을 남긴다. 예를 들어 7일이
 * 남았고 (6일 100점) · (4일 60점) · (3일 55점)이 있으면 그리디는 100점을 담고 1일을 버리지만, DP는
 * 4일+3일을 담아 115점을 찾는다. 남은 기간이 짧을수록 이 차이가 커진다.
 *
 * 한 단계는 쪼갤 수 없고(절반만 완료는 없다) 한 번만 담을 수 있어 0/1 배낭이 맞다.
 * 표 크기는 (단계 수 × 남은 일수)라서 둘 다 상한을 둔다 — 아래 MAX_* 참고.
 */
public final class DdayPlanner {

    /** 계획을 세워 주는 최대 기간. 1년을 넘는 D-day에 계획을 세우는 것은 의미가 없고 표만 커진다. */
    static final int MAX_DAYS = 365;
    /**
     * DP에 넣는 최대 후보 수. 로드맵이 길어지면 미완료 단계가 수백 개가 된다 —
     * 점수/일수가 높은 것부터 이만큼만 넣는다(최적해를 놓칠 수 있지만 상위 후보 안에 거의 들어온다).
     */
    static final int MAX_CANDIDATES = 60;

    private DdayPlanner() {
    }

    /** 배낭에 넣을 후보 하나. */
    public record Candidate(Long stepId, String label, String stepType, String tier, int effortDays, int points) {

        public Long getStepId() {
            return stepId;
        }

        public String getLabel() {
            return label;
        }

        public String getStepType() {
            return stepType;
        }

        public String getTier() {
            return tier;
        }

        public int getEffortDays() {
            return effortDays;
        }

        public int getPoints() {
            return points;
        }
    }

    /** 고른 조합. */
    public record Plan(List<Candidate> picked, int totalPoints, int usedDays, int availableDays) {

        public List<Candidate> getPicked() {
            return picked;
        }

        public int getTotalPoints() {
            return totalPoints;
        }

        public int getUsedDays() {
            return usedDays;
        }

        public int getAvailableDays() {
            return availableDays;
        }

        public int getLeftoverDays() {
            return Math.max(0, availableDays - usedDays);
        }

        /** 구성요소가 아닌 파생 값 — Tomcat 11의 RecordELResolver는 getX()를 안 찾는다 */
        public int leftoverDays() {
            return getLeftoverDays();
        }

        /**
         * 자바 코드용 — 화면(EL)은 이 값을 읽을 수 없다. empty는 EL 예약어라 ${x.empty}가 속성 접근이
         * 아니라 파싱 오류가 된다(2026-10-07 면접관 화면이 그래서 500이었다). 화면은 picked가 빈지 본다.
         */
        public boolean isEmpty() {
            return picked.isEmpty();
        }
    }

    /**
     * 남은 일수 안에서 점수 합이 가장 큰 조합을 고른다.
     *
     * @param candidates 지금 할 수 있는(잠기지 않은) 미완료 단계들
     * @param daysLeft   D-day까지 남은 일수
     */
    public static Plan plan(List<Candidate> candidates, int daysLeft) {
        int capacity = Math.max(0, Math.min(MAX_DAYS, daysLeft));
        if (capacity == 0 || candidates == null || candidates.isEmpty()) {
            return new Plan(List.of(), 0, 0, capacity);
        }

        // 하루 안에 못 끝내는 것은 애초에 담을 수 없고, 소요 일수가 0 이하인 잘못된 값은 1일로 본다
        List<Candidate> items = candidates.stream()
                .filter(c -> c.effortDays() <= capacity && c.points() > 0)
                .map(c -> c.effortDays() > 0 ? c
                        : new Candidate(c.stepId(), c.label(), c.stepType(), c.tier(), 1, c.points()))
                // 점수 밀도가 높은 것부터 — 후보를 잘라야 할 때 좋은 것이 남는다
                .sorted(Comparator.comparingDouble((Candidate c) -> -((double) c.points() / c.effortDays()))
                        .thenComparing(Candidate::points, Comparator.reverseOrder()))
                .limit(MAX_CANDIDATES)
                .toList();
        if (items.isEmpty()) {
            return new Plan(List.of(), 0, 0, capacity);
        }

        // best[i][d] = 앞의 i개만 쓰고 용량 d일 때의 최대 점수 (표준 0/1 배낭)
        int n = items.size();
        int[][] best = new int[n + 1][capacity + 1];
        for (int i = 1; i <= n; i++) {
            Candidate item = items.get(i - 1);
            int weight = item.effortDays();
            int value = item.points();
            for (int d = 0; d <= capacity; d++) {
                int without = best[i - 1][d];
                best[i][d] = d < weight ? without : Math.max(without, best[i - 1][d - weight] + value);
            }
        }

        // 표를 거꾸로 따라가며 무엇을 담았는지 되살린다
        Deque<Candidate> chosen = new ArrayDeque<>();
        int remaining = capacity;
        for (int i = n; i > 0; i--) {
            if (best[i][remaining] != best[i - 1][remaining]) {
                Candidate item = items.get(i - 1);
                chosen.push(item);
                remaining -= item.effortDays();
            }
        }

        List<Candidate> picked = new ArrayList<>(chosen);
        // 화면에는 짧게 끝나는 것부터 — "오늘 뭐부터" 순서로 읽힌다
        picked.sort(Comparator.comparingInt(Candidate::effortDays).thenComparing(Candidate::points,
                Comparator.reverseOrder()));
        int usedDays = picked.stream().mapToInt(Candidate::effortDays).sum();
        return new Plan(List.copyOf(picked), best[n][capacity], usedDays, capacity);
    }

    /**
     * 단계 하나의 소요 일수 추정. SKILL은 티어별로, 나머지는 종류별로 본다.
     * 모르는 종류는 기본값 — 새 단계 종류가 생겨도 계획이 멈추지 않는다.
     */
    static int effortDays(String stepType, String tier) {
        String type = stepType == null ? "" : stepType;
        String key = switch (type) {
            case "SKILL" -> "SKILL_" + (tier == null || tier.isBlank() ? "ENTRY" : tier);
            case "CERT", "PROJECT", "REVIEW" -> type;
            default -> "DEFAULT";
        };
        String ruleKey = ScoringRules.EFFORT_DAYS_PREFIX + key;
        return ScoringRules.isKnown(ruleKey) ? ScoringRules.get(ruleKey)
                : ScoringRules.get(ScoringRules.EFFORT_DAYS_PREFIX + "DEFAULT");
    }
}
