package com.specodyssey.service;

import com.specodyssey.service.SpacedRepetition.Next;
import com.specodyssey.service.SpacedRepetition.Recall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 간격 반복 복습(SM-2) 계산 — DB 없이 공식만 본다 (2026-10-08).
 *
 * 복습 주기가 티어로만 고정이어서 쉬운 기술과 어려운 기술을 같은 주기로 복습했다.
 * 여기서 고정하는 것: 티어 주기에서 출발하는지, 평가에 따라 간격이 늘고 줄는지, 상·하한을 지키는지.
 */
class SpacedRepetitionTest {

    private static final int ENTRY_DAYS = 30; // 입문 티어 기본 주기

    @Test
    void 첫_복습은_티어_기본_주기를_그대로_쓴다() {
        Next next = SpacedRepetition.next(Recall.NORMAL, 0, SpacedRepetition.DEFAULT_EASE, 0, ENTRY_DAYS);

        assertEquals(30, next.intervalDays(), "팀이 정한 티어 주기가 출발점이다");
        assertEquals(1, next.repetitions());
        assertEquals(2.5, next.easeFactor(), 0.001, "보통은 EF를 바꾸지 않는다");
    }

    @Test
    void 두_번째부터_EF만큼_간격이_늘어난다() {
        Next next = SpacedRepetition.next(Recall.NORMAL, 30, 2.5, 1, ENTRY_DAYS);

        assertEquals(75, next.intervalDays(), "30 × 2.5");
        assertEquals(2, next.repetitions());
    }

    @Test
    void 쉬웠다고_하면_EF가_올라가고_다음_간격이_더_길어진다() {
        Next easy = SpacedRepetition.next(Recall.EASY, 30, 2.5, 1, ENTRY_DAYS);
        Next normal = SpacedRepetition.next(Recall.NORMAL, 30, 2.5, 1, ENTRY_DAYS);

        assertEquals(2.6, easy.easeFactor(), 0.001, "q=5면 +0.1");
        assertTrue(easy.intervalDays() > normal.intervalDays(),
                "쉬움 " + easy.intervalDays() + "일 vs 보통 " + normal.intervalDays() + "일");
    }

    @Test
    void 어려웠다고_하면_EF가_내려가_다음_간격이_짧아진다() {
        Next hard = SpacedRepetition.next(Recall.HARD, 60, 2.5, 2, ENTRY_DAYS);

        assertEquals(2.36, hard.easeFactor(), 0.001, "q=3이면 -0.14");
        assertEquals(142, hard.intervalDays(), "60 × 2.36 = 141.6 → 142");
        assertEquals(3, hard.repetitions(), "통과이므로 연속 기록은 이어진다");
    }

    @Test
    void 거의_잊었으면_연속_기록을_지우고_일주일_뒤에_다시_본다() {
        Next forgot = SpacedRepetition.next(Recall.FORGOT, 120, 2.5, 4, ENTRY_DAYS);

        assertEquals(SpacedRepetition.RELEARN_DAYS, forgot.intervalDays());
        assertEquals(0, forgot.repetitions(), "처음부터 다시 쌓는다");
        assertEquals(2.18, forgot.easeFactor(), 0.001, "q=2면 -0.32");
    }

    @Test
    void EF는_1_3_아래로_내려가지_않는다() {
        double ease = SpacedRepetition.DEFAULT_EASE;
        for (int i = 0; i < 20; i++) { // "거의 잊었다"를 계속 눌러도
            ease = SpacedRepetition.next(Recall.FORGOT, 10, ease, 0, ENTRY_DAYS).easeFactor();
        }

        assertEquals(SpacedRepetition.MIN_EASE, ease, 0.001,
                "하한이 없으면 간격이 거의 안 늘어 복습만 쌓인다");
    }

    @Test
    void EF는_2_8_위로_올라가지_않는다() {
        double ease = SpacedRepetition.DEFAULT_EASE;
        for (int i = 0; i < 20; i++) { // "쉬웠다"를 계속 눌러도
            ease = SpacedRepetition.next(Recall.EASY, 10, ease, 1, ENTRY_DAYS).easeFactor();
        }

        assertEquals(SpacedRepetition.MAX_EASE, ease, 0.001,
                "상한이 없으면 몇 번 누르는 것만으로 간격이 몇 년이 된다");
    }

    @Test
    void 간격은_1년을_넘기지_않는다() {
        Next next = SpacedRepetition.next(Recall.EASY, 300, 2.8, 5, ENTRY_DAYS);

        assertEquals(SpacedRepetition.MAX_INTERVAL_DAYS, next.intervalDays(),
                "1년을 넘기면 사실상 다시 안 보게 된다");
    }

    @Test
    void 티어가_높은_기술은_첫_간격부터_더_길다() {
        Next entry = SpacedRepetition.next(Recall.NORMAL, 0, 2.5, 0, 30);
        Next expert = SpacedRepetition.next(Recall.NORMAL, 0, 2.5, 0, 120);

        assertEquals(30, entry.intervalDays());
        assertEquals(120, expert.intervalDays(), "깊이 익힌 기술은 오래 간다 — 기존 설계를 그대로 잇는다");
    }

    @Test
    void 자기_평가_값이_이상하면_보통으로_본다() {
        assertEquals(Recall.NORMAL, Recall.of(null));
        assertEquals(Recall.NORMAL, Recall.of(""));
        assertEquals(Recall.NORMAL, Recall.of("몰라요"), "평가 때문에 복습 제출이 막히면 안 된다");
        assertEquals(Recall.EASY, Recall.of("easy"));
        assertEquals(Recall.FORGOT, Recall.of(" FORGOT "));
    }

    @Test
    void 자기_평가_네_가지는_SM2_품질_2에서_5에_대응한다() {
        assertEquals(2, Recall.FORGOT.getQuality());
        assertEquals(3, Recall.HARD.getQuality());
        assertEquals(4, Recall.NORMAL.getQuality());
        assertEquals(5, Recall.EASY.getQuality());
        for (Recall recall : Recall.values()) {
            assertTrue(recall.getLabel() != null && !recall.getLabel().isBlank(), recall.name());
        }
    }

    @Test
    void 쉬운_기술과_어려운_기술의_간격이_실제로_갈라진다() {
        // 같은 입문 기술을 세 번 복습했을 때 — 이게 이 기능의 목적이다
        int easyInterval = 0;
        int hardInterval = 0;
        double easyEase = SpacedRepetition.DEFAULT_EASE;
        double hardEase = SpacedRepetition.DEFAULT_EASE;
        int easyReps = 0;
        int hardReps = 0;
        for (int i = 0; i < 3; i++) {
            Next easy = SpacedRepetition.next(Recall.EASY, easyInterval, easyEase, easyReps, ENTRY_DAYS);
            easyInterval = easy.intervalDays();
            easyEase = easy.easeFactor();
            easyReps = easy.repetitions();

            Next hard = SpacedRepetition.next(Recall.HARD, hardInterval, hardEase, hardReps, ENTRY_DAYS);
            hardInterval = hard.intervalDays();
            hardEase = hard.easeFactor();
            hardReps = hard.repetitions();
        }

        assertTrue(easyInterval > hardInterval * 1.5,
                "쉬운 기술 " + easyInterval + "일 vs 어려운 기술 " + hardInterval + "일 — 충분히 갈라져야 의미가 있다");
    }
}
