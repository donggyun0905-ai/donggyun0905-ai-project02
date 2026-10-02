package com.specodyssey.controller;

import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewCheckGateTest {

    @Test
    void 같은_날_같은_세션에서는_한_번만_검사한다() {
        FakeWeb.Session session = new FakeWeb.Session();
        LocalDate day = LocalDate.of(2026, 10, 2);
        assertTrue(ReviewCheckGate.shouldCheck(session.http(), day));
        assertFalse(ReviewCheckGate.shouldCheck(session.http(), day));
        assertFalse(ReviewCheckGate.shouldCheck(session.http(), day));
    }

    @Test
    void 날짜가_바뀌거나_새_세션이면_다시_검사한다() {
        FakeWeb.Session session = new FakeWeb.Session();
        assertTrue(ReviewCheckGate.shouldCheck(session.http(), LocalDate.of(2026, 10, 2)));
        assertTrue(ReviewCheckGate.shouldCheck(session.http(), LocalDate.of(2026, 10, 3)));
        assertTrue(ReviewCheckGate.shouldCheck(new FakeWeb.Session().http(), LocalDate.of(2026, 10, 3)));
    }

    @Test
    void reset하면_같은_날에도_다시_검사하고_세션이_없으면_매번_검사한다() {
        FakeWeb.Session session = new FakeWeb.Session();
        LocalDate day = LocalDate.of(2026, 10, 2);
        ReviewCheckGate.shouldCheck(session.http(), day);
        ReviewCheckGate.reset(session.http());
        assertTrue(ReviewCheckGate.shouldCheck(session.http(), day));
        assertTrue(ReviewCheckGate.shouldCheck(null, day));
        ReviewCheckGate.reset(null); // 예외 없이 지나간다
    }
}
