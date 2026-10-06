package com.specodyssey.service.simulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimPersonaTest {

    // 70일 동안 [하는 날, 맞힌 문제 수]를 늘어놓은 것
    private static List<String> play(SimPersona persona, long userId) {
        List<String> days = new ArrayList<>();
        for (int day = 0; day < 70; day++) {
            Random r = SimPersona.randomFor(userId, day);
            boolean active = persona.isActive(day, r);
            int solved = 0;
            if (active) {
                for (int i = 0; i < 3; i++) {
                    solved += persona.solves(r) ? 1 : 0;
                }
            }
            days.add(active + ":" + solved);
        }
        return days;
    }

    @Test
    void 같은_판_안에서는_이어_해도_흐름이_같다() {
        assertEquals(play(SimPersona.STEADY, 4321L), play(SimPersona.STEADY, 4321L));
    }

    @Test
    void 판이_다르면_결과가_다르다() {
        assertNotEquals(play(SimPersona.STEADY, 4321L), play(SimPersona.STEADY, 4322L));
    }

    @Test
    void 작심삼일형은_닷새마다_이틀_쉰다() {
        for (int day = 0; day < 70; day++) {
            if (day % 5 >= 3) {
                assertFalse(SimPersona.ON_OFF.isActive(day, SimPersona.randomFor(1L, day)), "day " + day);
            }
        }
    }

    @Test
    void 성실형이_보통형보다_많이_한다() {
        long diligent = play(SimPersona.DILIGENT, 99L).stream().filter(d -> d.startsWith("true")).count();
        long onOff = play(SimPersona.ON_OFF, 99L).stream().filter(d -> d.startsWith("true")).count();
        assertTrue(diligent > onOff, diligent + " vs " + onOff);
    }

    @Test
    void 고른_성향을_읽고_비우면_무작위() {
        assertEquals(SimPersona.DILIGENT, SimPersona.parse("DILIGENT"));
        assertEquals(null, SimPersona.parse(""), "비우면 무작위");
        assertEquals(null, SimPersona.parse("RANDOM"));
        assertEquals(null, SimPersona.parse(null));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> SimPersona.parse("없는값"));
        assertEquals(SimPersona.STEADY, SimPersona.fromName("없는값"));
    }

    @Test
    void 무작위_성향은_세_가지가_다_나온다() {
        java.util.Set<SimPersona> seen = java.util.EnumSet.noneOf(SimPersona.class);
        for (int i = 0; i < 200; i++) {
            seen.add(SimPersona.random());
        }
        assertEquals(3, seen.size());
    }

    @Test
    void 목표까지_날_수를_하루_평균_점수로_어림잡는다() {
        int d500 = SimPersona.DILIGENT.estimateDays(0, 500, 365);
        assertTrue(d500 >= 15 && d500 <= 25, "성실형 0→500점은 실제로 21일 — 어림값 " + d500);
        int d3500 = SimPersona.DILIGENT.estimateDays(0, 3500, 365);
        assertTrue(d3500 >= 60 && d3500 <= 85, "성실형 0→3,500점은 실제로 약 70일 — 어림값 " + d3500);
        assertTrue(SimPersona.DILIGENT.estimateDays(0, 1000, 365) < SimPersona.STEADY.estimateDays(0, 1000, 365));
        assertTrue(SimPersona.STEADY.estimateDays(0, 1000, 365) < SimPersona.ON_OFF.estimateDays(0, 1000, 365));
        assertEquals(1, SimPersona.STEADY.estimateDays(2000, 1000, 365), "이미 넘었으면 최소 1일");
        assertEquals(365, SimPersona.ON_OFF.estimateDays(0, 100_000, 365), "최대 날 수를 넘지 않는다");
    }

    @Test
    void 로드맵_단계는_정해진_간격마다() {
        assertTrue(SimPersona.DILIGENT.completesStep(2));
        assertFalse(SimPersona.DILIGENT.completesStep(3));
        assertTrue(SimPersona.STEADY.completesStep(4));
    }
}
