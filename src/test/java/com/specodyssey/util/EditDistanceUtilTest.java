package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditDistanceUtilTest {

    @Test
    void 기본_거리는_순서_바뀜을_2로_센다() {
        assertEquals(2, EditDistanceUtil.distance("pyhton", "python"));
        assertEquals(0, EditDistanceUtil.distance("java", "java"));
        assertEquals(1, EditDistanceUtil.distance("jenkin", "jenkins"));
    }

    @Test
    void 순서_바뀜을_고려한_거리는_이웃_두_글자_교환을_1로_센다() {
        assertEquals(1, EditDistanceUtil.transpositionAwareDistance("pyhton", "python"));
        assertEquals(1, EditDistanceUtil.transpositionAwareDistance("djnago", "django"));
        assertEquals(1, EditDistanceUtil.transpositionAwareDistance("jenkin", "jenkins"));
        assertEquals(0, EditDistanceUtil.transpositionAwareDistance("java", "java"));
        assertEquals(3, EditDistanceUtil.transpositionAwareDistance("javaspring", "javascript"));
    }
}
