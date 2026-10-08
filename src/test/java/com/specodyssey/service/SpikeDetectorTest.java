package com.specodyssey.service;

import com.specodyssey.service.SpikeDetector.Spike;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 급상승 감지 — EWMA + z-score (2026-10-08). DB 없이 계산만 본다.
 *
 * 트렌드가 "언급 비율 높은 순"이면 원래 항상 높은 기술이 늘 1등이라 "요즘 뜨는 기술"을 알 수 없다.
 * 여기서 고정하는 것: 평소 높은 기술을 급상승으로 보지 않는지, 작은 숫자의 큰 배수를 걸러내는지,
 * 과거가 모자라면 판단을 보류하는지.
 */
class SpikeDetectorTest {

    @Test
    void 평소보다_크게_튀면_급상승이다() {
        // 1% 근처로 평평했는데 이번 달 5%
        Spike spike = SpikeDetector.detect(List.of(1.0, 1.1, 0.9, 1.0, 5.0));

        assertTrue(spike.rising(), "z=" + spike.zScore() + " 기준선=" + spike.baseline());
        assertTrue(spike.zScore() >= SpikeDetector.MIN_Z);
        assertEquals(5.0, spike.latest());
    }

    @Test
    void 원래_항상_높은_기술은_급상승이_아니다() {
        // Java처럼 늘 30%대인 기술 — 절대값은 1등이지만 "뜨는" 것은 아니다
        Spike spike = SpikeDetector.detect(List.of(30.0, 31.0, 29.5, 30.5, 31.0));

        assertFalse(spike.rising(), "z=" + spike.zScore());
        assertTrue(spike.baseline() > 29, "기준선이 평소 수준을 잡아야 한다: " + spike.baseline());
    }

    @Test
    void 작은_숫자가_몇_배_되는_것은_급상승으로_보지_않는다() {
        // 0.1% → 0.4%. 배수로는 4배지만 공고 몇 건 차이다
        Spike spike = SpikeDetector.detect(List.of(0.1, 0.1, 0.1, 0.1, 0.4));

        assertFalse(spike.rising(), "비율 바닥(" + SpikeDetector.MIN_RATIO + "%)을 못 넘었다: " + spike.latest());
    }

    @Test
    void 떨어지는_달은_급상승이_아니다() {
        Spike spike = SpikeDetector.detect(List.of(10.0, 11.0, 10.5, 11.0, 2.0));

        assertFalse(spike.rising());
        assertTrue(spike.zScore() < 0, "아래로 튄 것도 z로는 큰 값이라 부호를 봐야 한다: " + spike.zScore());
    }

    @Test
    void 과거가_세_달보다_적으면_판단을_보류한다() {
        // 수집을 막 시작했을 때 — "평소"를 모른다
        assertFalse(SpikeDetector.detect(List.of(1.0, 9.0)).rising());
        assertFalse(SpikeDetector.detect(List.of(1.0, 1.0, 9.0)).rising());
        assertTrue(SpikeDetector.detect(List.of(1.0, 1.0, 1.0, 9.0)).rising(), "네 달이면 판단한다");
        assertEquals(3, SpikeDetector.MIN_HISTORY);
    }

    @Test
    void 값이_하나뿐이거나_비어_있어도_깨지지_않는다() {
        assertFalse(SpikeDetector.detect(List.of()).rising());
        assertFalse(SpikeDetector.detect(null).rising());
        assertFalse(SpikeDetector.detect(List.of(5.0)).rising());
        assertEquals(5.0, SpikeDetector.detect(List.of(5.0)).latest());
    }

    @Test
    void 중간에_빠진_달이_있어도_계산이_멈추지_않는다() {
        Spike spike = SpikeDetector.detect(Arrays.asList(1.0, null, 1.0, 1.0, 5.0));

        assertTrue(spike.rising(), "빠진 달은 0건으로 본다. z=" + spike.zScore());
    }

    @Test
    void 완전히_평평했던_기술의_작은_변화를_과장하지_않는다() {
        // 표준편차가 0이면 z가 무한대가 된다 — 바닥(MIN_STDDEV)이 그걸 막는다
        Spike spike = SpikeDetector.detect(List.of(2.0, 2.0, 2.0, 2.0, 2.3));

        assertFalse(spike.rising(), "0.3%p 오른 것을 급상승이라고 하면 배지가 의미를 잃는다: z=" + spike.zScore());
        assertTrue(Double.isFinite(spike.zScore()));
    }

    @Test
    void 기준선은_최근_값에_더_큰_가중을_준다() {
        // 뒤로 갈수록 커지는 시계열 — 단순 평균(3.0)보다 EWMA가 커야 한다
        double simpleAverage = (1.0 + 2.0 + 3.0 + 4.0 + 5.0) / 5;
        double ewma = SpikeDetector.ewma(List.of(1.0, 2.0, 3.0, 4.0, 5.0), SpikeDetector.ALPHA);

        assertTrue(ewma > simpleAverage, "EWMA " + ewma + " vs 단순 평균 " + simpleAverage);
    }

    @Test
    void 이번_달_값은_기준선에_넣지_않는다() {
        // 넣으면 자기 자신 때문에 기준선이 올라가 z가 작아진다
        List<Double> series = List.of(1.0, 1.0, 1.0, 1.0, 9.0);
        Spike spike = SpikeDetector.detect(series);
        double baselineWithLatest = SpikeDetector.ewma(series, SpikeDetector.ALPHA);

        assertTrue(spike.baseline() < baselineWithLatest,
                "과거만 " + spike.baseline() + " vs 이번 달까지 " + baselineWithLatest);
    }

    @Test
    void 평소의_몇_배인지_문구용_값을_준다() {
        Spike spike = SpikeDetector.detect(List.of(1.0, 1.0, 1.0, 1.0, 5.0));

        assertTrue(spike.multiple() >= 4.0, "실제: " + spike.multiple() + "배 (기준선 " + spike.baseline() + ")");
        assertEquals(0.0, SpikeDetector.detect(List.of(0.0, 0.0, 0.0, 0.0, 0.0)).multiple(),
                "기준선이 0이면 배수를 말할 수 없다");
    }

    @Test
    void EWMA는_빈_입력에_0을_준다() {
        assertEquals(0.0, SpikeDetector.ewma(List.of(), 0.4));
        assertEquals(0.0, SpikeDetector.ewma(null, 0.4));
        assertEquals(3.0, SpikeDetector.ewma(List.of(3.0), 0.4), "값이 하나면 그 값");
    }
}
