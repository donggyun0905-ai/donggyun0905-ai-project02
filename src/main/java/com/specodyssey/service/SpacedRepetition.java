package com.specodyssey.service;

/**
 * 간격 반복 복습 — SM-2 (2026-10-08).
 * 관련 요구사항: FR-39 로드맵 복습
 *
 * 복습 주기는 지금까지 티어로만 고정이었다(입문 30일 · 핵심 60 · 심화 90 · 전문가 120). 그래서 같은
 * 사람이 쉬운 기술과 어려운 기술을 같은 주기로 복습했다. SM-2는 Anki가 쓰는 공식으로, "얼마나
 * 기억났는지"(q)에 따라 기술마다 다음 간격을 다르게 잡는다 — 잊어버리는 속도에 맞춘다.
 *
 * <b>원래 SM-2와 다르게 한 곳</b>: 첫 간격을 1일·6일이 아니라 <b>티어 기본 주기</b>로 쓴다.
 * 원래 공식은 매일 카드를 보는 플래시카드용이라 1일부터 시작하는데, 여기서 복습은 "기술 하나를
 * 다시 정리해 글로 내는" 일이라 하루 뒤에 또 하라고 하면 쓸 수 없다. 팀이 정한 티어 주기를
 * 출발점으로 두고, 두 번째부터 ease_factor로 늘리거나 줄인다. 이 결정 덕분에 기존 동작과도 이어진다.
 *
 * 상태가 없는 순수 계산이라 DB 없이 테스트한다.
 */
public final class SpacedRepetition {

    /** SM-2 권장 시작값 */
    public static final double DEFAULT_EASE = 2.5;
    /** SM-2 권장 하한 — 더 내려가면 간격이 거의 안 늘어 복습만 쌓인다 */
    static final double MIN_EASE = 1.3;
    /** 상한은 원래 공식에 없다. 없으면 "쉬웠다"를 몇 번 누르는 것만으로 간격이 몇 년이 된다 */
    static final double MAX_EASE = 2.8;

    /** q < 3이면 "다시 배워야 한다"로 본다 — 원래 SM-2와 같은 경계 */
    static final int PASS_QUALITY = 3;
    /** 못 외운 기술을 다시 보는 간격. 1일은 글로 내는 복습에는 너무 짧다 */
    static final int RELEARN_DAYS = 7;
    static final int MIN_INTERVAL_DAYS = 1;
    /** 1년을 넘기면 사실상 "다시 안 본다"라서 상한을 둔다 */
    static final int MAX_INTERVAL_DAYS = 365;

    private SpacedRepetition() {
    }

    /** 화면이 보여 주는 자기 평가 네 가지. q는 SM-2의 응답 품질(0~5). */
    public enum Recall {
        FORGOT("거의 잊었다", 2),
        HARD("어려웠다", 3),
        NORMAL("보통", 4),
        EASY("쉬웠다", 5);

        private final String label;
        private final int quality;

        Recall(String label, int quality) {
            this.label = label;
            this.quality = quality;
        }

        public String getLabel() {
            return label;
        }

        public int getQuality() {
            return quality;
        }

        /** 폼 값 → 자기 평가. 모르는 값이면 보통으로 본다 — 평가 때문에 복습 제출이 막히면 안 된다. */
        public static Recall of(String raw) {
            if (raw != null) {
                for (Recall value : values()) {
                    if (value.name().equalsIgnoreCase(raw.trim())) {
                        return value;
                    }
                }
            }
            return NORMAL;
        }
    }

    /** 다음 복습까지의 간격과 갱신된 EF. */
    public record Next(int intervalDays, double easeFactor, int repetitions) {

        public int getIntervalDays() {
            return intervalDays;
        }

        public double getEaseFactor() {
            return easeFactor;
        }

        public int getRepetitions() {
            return repetitions;
        }
    }

    /**
     * 복습을 한 번 끝냈을 때의 다음 일정.
     *
     * @param recall           사용자의 자기 평가
     * @param previousInterval 지난번에 적용한 간격(일). 첫 복습이면 0 이하를 넘긴다
     * @param previousEase     지난번 EF. 첫 복습이면 DEFAULT_EASE
     * @param repetitions      지금까지 연속 통과 횟수
     * @param tierDefaultDays  이 기술의 티어 기본 주기 — 첫 간격으로 쓴다
     */
    public static Next next(Recall recall, int previousInterval, double previousEase, int repetitions,
                            int tierDefaultDays) {
        int quality = recall.getQuality();
        double ease = updateEase(previousEase, quality);

        if (quality < PASS_QUALITY) {
            // 원래 SM-2와 같다: 연속 기록을 0으로 돌리고 짧게 다시 본다. EF는 이미 내려갔다.
            return new Next(RELEARN_DAYS, ease, 0);
        }

        int nextRepetitions = Math.max(0, repetitions) + 1;
        int base = previousInterval > 0 ? previousInterval : Math.max(MIN_INTERVAL_DAYS, tierDefaultDays);
        // 첫 통과는 티어 기본 주기를 그대로 쓴다(위 주석의 "원래 SM-2와 다르게 한 곳").
        int interval = nextRepetitions <= 1 ? base : (int) Math.round(base * ease);
        return new Next(clampInterval(interval), ease, nextRepetitions);
    }

    /**
     * SM-2의 EF 갱신식. EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))
     * q=5면 +0.1, q=4면 변화 없음, q=3이면 -0.14, q=2면 -0.32.
     */
    static double updateEase(double previousEase, int quality) {
        double q = 5 - quality;
        double updated = previousEase + (0.1 - q * (0.08 + q * 0.02));
        return round2(Math.max(MIN_EASE, Math.min(MAX_EASE, updated)));
    }

    private static int clampInterval(int days) {
        return Math.max(MIN_INTERVAL_DAYS, Math.min(MAX_INTERVAL_DAYS, days));
    }

    /** DECIMAL(4,2)에 넣으므로 소수 둘째 자리까지만 — 읽고 쓸 때 값이 달라지지 않게 */
    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
