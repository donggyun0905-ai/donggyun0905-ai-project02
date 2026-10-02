package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagerTest {

    private static List<Integer> numbers(int n) {
        return IntStream.rangeClosed(1, n).boxed().toList();
    }

    @Test
    void 쪽마다_정해진_개수로_자르고_마지막_쪽은_나머지를_담는다() {
        Pager<Integer> p1 = Pager.of(numbers(25), 1, 10);
        assertEquals(numbers(10), p1.getItems());
        assertEquals(3, p1.getTotalPages());
        assertEquals(25, p1.getTotal());
        assertTrue(p1.isNeeded() && p1.isHasNext() && !p1.isHasPrevious());

        Pager<Integer> p3 = Pager.of(numbers(25), 3, 10);
        assertEquals(List.of(21, 22, 23, 24, 25), p3.getItems());
        assertTrue(p3.isHasPrevious() && !p3.isHasNext());
    }

    @Test
    void 범위를_벗어난_쪽은_가장_가까운_쪽으로_맞춘다() {
        assertEquals(3, Pager.of(numbers(25), 99, 10).getPage());
        assertEquals(1, Pager.of(numbers(25), 0, 10).getPage());
        assertEquals(1, Pager.of(numbers(25), -5, 10).getPage());
    }

    @Test
    void 항목이_없거나_한_쪽이면_쪽_이동이_필요_없다() {
        Pager<Integer> empty = Pager.of(List.of(), 1, 10);
        assertTrue(empty.getItems().isEmpty());
        assertEquals(1, empty.getTotalPages());
        assertFalse(empty.isNeeded());
        assertFalse(Pager.of(numbers(10), 1, 10).isNeeded());
        assertTrue(Pager.of(numbers(11), 1, 10).isNeeded());
    }

    @Test
    void 쪽_번호_파라미터는_이상한_값이면_1쪽이다() {
        assertEquals(2, Pager.parsePage("2"));
        assertEquals(2, Pager.parsePage(" 2 "));
        assertEquals(1, Pager.parsePage(null));
        assertEquals(1, Pager.parsePage("abc"));
        assertEquals(1, Pager.parsePage(""));
        assertThrows(IllegalArgumentException.class, () -> Pager.of(numbers(3), 1, 0));
    }
}
