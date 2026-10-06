package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * GroqRetryAfterClient의 retry-after 헤더 해석 단위테스트 (FR-54 수집 대기 시간 단축). 외부 호출 없음.
 */
class GroqRetryAfterClientTest {

    @Test
    void parseRetryAfter_초_단위_정수와_소수를_읽는다() {
        assertEquals(Duration.ofSeconds(7), GroqRetryAfterClient.parseRetryAfter("7"));
        assertEquals(Duration.ofMillis(2340), GroqRetryAfterClient.parseRetryAfter(" 2.34 "));
        assertEquals(Duration.ZERO, GroqRetryAfterClient.parseRetryAfter("0"));
    }

    @Test
    void parseRetryAfter_없거나_숫자가_아니면_null() {
        assertNull(GroqRetryAfterClient.parseRetryAfter(null));
        assertNull(GroqRetryAfterClient.parseRetryAfter(" "));
        assertNull(GroqRetryAfterClient.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"));
        assertNull(GroqRetryAfterClient.parseRetryAfter("-3"));
    }
}
