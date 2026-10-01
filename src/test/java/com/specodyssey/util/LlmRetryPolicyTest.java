package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmRetryPolicyTest {

    private final LlmRetryPolicy policy = new LlmRetryPolicy(3, 1_000, Duration.ofSeconds(60));
    private static final Duration SHORT = Duration.ofSeconds(1);

    @Test
    void 일시적_실패는_최대_시도_횟수까지_재시도한다() {
        for (int status : new int[] {-1, 429, 498, 500, 502, 503}) {
            assertTrue(policy.shouldRetry(status, 1, SHORT), "1번째 실패 " + status);
            assertTrue(policy.shouldRetry(status, 2, SHORT), "2번째 실패 " + status);
            assertFalse(policy.shouldRetry(status, 3, SHORT), "3번째 실패 " + status);
        }
    }

    @Test
    void 형식_오류_400과_422는_한_번만_재시도한다() {
        for (int status : new int[] {400, LlmRetryPolicy.FORMAT_ERROR}) {
            assertTrue(policy.shouldRetry(status, 1, SHORT));
            assertFalse(policy.shouldRetry(status, 2, SHORT));
        }
    }

    @Test
    void 인증_권한_없음_등은_재시도하지_않는다() {
        for (int status : new int[] {401, 403, 404, 413}) {
            assertFalse(policy.shouldRetry(status, 1, SHORT), "status " + status);
        }
    }

    @Test
    void 시간_예산을_넘으면_재시도하지_않는다() {
        assertFalse(policy.shouldRetry(503, 1, Duration.ofSeconds(60)));
    }

    @Test
    void 대기_시간은_시도마다_늘어난다() {
        assertTrue(policy.backoffMillis(1) == 1_000 && policy.backoffMillis(2) == 2_000);
    }
}
