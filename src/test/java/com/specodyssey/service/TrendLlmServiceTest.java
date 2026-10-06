package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TrendLlmService 429 재시도 대기 시간 단위테스트 (FR-54 수집 대기 시간 단축). 외부 호출 없음.
 */
class TrendLlmServiceTest {

    @Test
    void retryWait_retry_after가_있으면_그만큼에_여유분만_더한다() {
        assertEquals(Duration.ofMillis(7500), TrendLlmService.retryWait(Duration.ofSeconds(7), 1));
        assertEquals(Duration.ofMillis(2840), TrendLlmService.retryWait(Duration.ofMillis(2340), 3));
    }

    @Test
    void retryWait_retry_after가_너무_길면_상한으로_자른다() {
        assertEquals(Duration.ofSeconds(60), TrendLlmService.retryWait(Duration.ofSeconds(3600), 1));
    }

    @Test
    void retryWait_retry_after가_없으면_예전처럼_시도횟수만큼_늘린다() {
        assertEquals(Duration.ofSeconds(20), TrendLlmService.retryWait(null, 1));
        assertEquals(Duration.ofSeconds(60), TrendLlmService.retryWait(null, 3));
    }
}
