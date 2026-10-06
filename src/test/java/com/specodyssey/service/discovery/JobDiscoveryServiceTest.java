package com.specodyssey.service.discovery;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR-37 추천 이후 프로필 변경 안내 판단. DB 없이 시각 비교만 확인한다.
 */
class JobDiscoveryServiceTest {

    private static final LocalDateTime SUBMITTED = LocalDateTime.of(2026, 10, 6, 11, 0);

    @Test
    void 제출_뒤에_프로필을_바꾸면_안내() {
        assertTrue(JobDiscoveryService.changedAfter(SUBMITTED.plusMinutes(5), SUBMITTED));
    }

    @Test
    void 프로필을_바꾸고_나서_다시_제출했으면_안내하지_않는다() {
        assertFalse(JobDiscoveryService.changedAfter(SUBMITTED.minusMinutes(5), SUBMITTED));
        assertFalse(JobDiscoveryService.changedAfter(SUBMITTED, SUBMITTED));
    }

    @Test
    void 설문_전이거나_프로필_수정_기록이_없으면_안내하지_않는다() {
        assertFalse(JobDiscoveryService.changedAfter(SUBMITTED, null));
        assertFalse(JobDiscoveryService.changedAfter(null, SUBMITTED));
    }
}
