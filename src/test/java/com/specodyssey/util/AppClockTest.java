package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppClockTest {

    @Test
    void 평소에는_실제_오늘이다() {
        assertEquals(LocalDate.now(AppClock.ZONE), AppClock.today());
        assertFalse(AppClock.isSimulating());
    }

    @Test
    void runOn_안에서만_날짜가_바뀌고_끝나면_돌아온다() throws Exception {
        LocalDate past = LocalDate.of(2026, 7, 1);
        LocalDate seen = AppClock.runOn(past, () -> {
            assertTrue(AppClock.isSimulating());
            assertEquals(past, AppClock.now().toLocalDate());
            return AppClock.today();
        });
        assertEquals(past, seen);
        assertEquals(LocalDate.now(AppClock.ZONE), AppClock.today());
    }

    @Test
    void 예외가_나도_날짜가_돌아온다() {
        assertThrows(IllegalStateException.class, () -> AppClock.runOn(LocalDate.of(2026, 7, 1), () -> {
            throw new IllegalStateException("x");
        }));
        assertFalse(AppClock.isSimulating());
    }

    @Test
    void 다른_스레드에는_영향이_없다() throws Exception {
        AtomicReference<LocalDate> other = new AtomicReference<>();
        AppClock.runOn(LocalDate.of(2026, 7, 1), () -> {
            Thread t = new Thread(() -> other.set(AppClock.today()));
            t.start();
            t.join();
            return null;
        });
        assertEquals(LocalDate.now(AppClock.ZONE), other.get());
    }
}
