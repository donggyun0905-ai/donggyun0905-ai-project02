package com.specodyssey.service;

import java.util.List;

/**
 * 급상승 감지 — EWMA 기준선 + z-score (2026-10-08).
 * 관련 요구사항: FR-47 기술 트렌드 · FR-48 급상승
 *
 * 트렌드는 지금까지 "이번 달 언급 비율이 높은 순"으로만 보여 줬다. 그러면 <b>원래 항상 높은 기술</b>이
 * 늘 1등이라 "요즘 뜨는 기술"을 알 수 없다. 알고 싶은 것은 절대값이 아니라 <b>평소보다 얼마나 튀었는지</b>다.
 *
 * <b>EWMA(지수가중이동평균)를 기준선으로 쓰는 이유</b>: 단순 평균은 1년 전 값과 지난달 값을 같게 본다.
 * 채용 시장은 최근이 더 중요해서, 최근 값에 더 큰 가중을 주는 EWMA가 "평소"에 가깝다.
 *   mean_t = α·x_t + (1-α)·mean_(t-1)
 *   var_t  = α·(x_t - mean_(t-1))² + (1-α)·var_(t-1)
 * z = (이번 달 값 - 지난달까지의 기준선) / 지난달까지의 표준편차.
 * 이번 달 값은 기준선에 넣지 않는다 — 넣으면 자기 자신 때문에 기준선이 올라가 z가 작아진다.
 *
 * 상태가 없는 순수 계산이라 DB 없이 테스트한다.
 */
public final class SpikeDetector {

    /**
     * EWMA 가중치. 월 단위 데이터이고 쌓인 달이 많지 않아(수집 시작 2026-09) 0.4로 둔다 —
     * 더 작으면 과거에 눌려 최근 변화가 안 보이고, 더 크면 기준선이 지난달 하나에 휘둘린다.
     */
    static final double ALPHA = 0.4;
    /** 이보다 크면 "급상승". 정규분포라면 상위 약 2% — 배지를 아껴 붙이려고 2가 아니라 2.0 이상으로 잡았다 */
    static final double MIN_Z = 2.0;
    /**
     * 비율이 이보다 낮으면 급상승으로 보지 않는다(%). 0.1%가 0.3%가 되는 것도 z는 크게 나오지만
     * 공고 몇 건의 차이일 뿐이라 "뜨는 기술"이라고 말할 수 없다.
     */
    static final double MIN_RATIO = 1.0;
    /** 기준선을 만들려면 최소 이만큼의 과거 달이 필요하다 — 한 달로는 "평소"를 모른다 */
    static final int MIN_HISTORY = 3;
    /** 표준편차가 0에 가까우면 z가 무한대로 튄다. 바닥을 둬서 평평한 시계열의 작은 변화를 과장하지 않는다 */
    static final double MIN_STDDEV = 0.2;

    private SpikeDetector() {
    }

    /** 감지 결과. rising이 true일 때만 화면에 배지를 붙인다. */
    public record Spike(boolean rising, double zScore, double baseline, double latest) {

        public boolean isRising() {
            return rising;
        }

        public double getZScore() {
            return zScore;
        }

        public double getBaseline() {
            return baseline;
        }

        public double getLatest() {
            return latest;
        }

        /** 기준선 대비 몇 배인지 — "평소의 2.4배" 문구에 쓴다. 기준선이 0이면 0 */
        public double getMultiple() {
            return baseline <= 0 ? 0 : Math.round(latest / baseline * 10.0) / 10.0;
        }

        public double multiple() {
            return getMultiple();
        }

        static Spike none(double latest) {
            return new Spike(false, 0.0, 0.0, latest);
        }
    }

    /**
     * 시계열의 마지막 값이 평소보다 튀었는지 본다.
     *
     * @param monthlyRatios 오래된 달부터 이번 달까지의 언급 비율(%). 마지막 원소가 이번 달
     */
    public static Spike detect(List<Double> monthlyRatios) {
        if (monthlyRatios == null || monthlyRatios.isEmpty()) {
            return Spike.none(0.0);
        }
        double latest = value(monthlyRatios.get(monthlyRatios.size() - 1));
        // 이번 달을 뺀 과거만으로 기준선을 만든다
        List<Double> history = monthlyRatios.subList(0, monthlyRatios.size() - 1);
        if (history.size() < MIN_HISTORY) {
            return Spike.none(latest); // 아직 "평소"를 모른다
        }

        double mean = value(history.get(0));
        double variance = 0.0;
        for (int i = 1; i < history.size(); i++) {
            double x = value(history.get(i));
            double previousMean = mean;
            mean = ALPHA * x + (1 - ALPHA) * previousMean;
            variance = ALPHA * Math.pow(x - previousMean, 2) + (1 - ALPHA) * variance;
        }

        double stddev = Math.max(MIN_STDDEV, Math.sqrt(variance));
        double z = (latest - mean) / stddev;
        boolean rising = z >= MIN_Z && latest >= MIN_RATIO && latest > mean;
        return new Spike(rising, round2(z), round2(mean), latest);
    }

    /** 과거 값들의 EWMA — 기준선 자체를 보여 줄 때도 쓴다 */
    static double ewma(List<Double> values, double alpha) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double mean = value(values.get(0));
        for (int i = 1; i < values.size(); i++) {
            mean = alpha * value(values.get(i)) + (1 - alpha) * mean;
        }
        return round2(mean);
    }

    /** null이 섞여 있어도 계산이 멈추지 않게 — 그 달은 0건으로 본다 */
    private static double value(Double raw) {
        return raw == null ? 0.0 : raw;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
