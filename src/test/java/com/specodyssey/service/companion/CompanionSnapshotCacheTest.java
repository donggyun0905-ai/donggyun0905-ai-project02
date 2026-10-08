package com.specodyssey.service.companion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 캐릭터의 "지금 할 말"을 바뀐 게 없으면 다시 만들지 않는지 (2026-10-08, DB 없이) */
class CompanionSnapshotCacheTest {

    private final AtomicInteger loads = new AtomicInteger();
    private final AtomicReference<String> fingerprint = new AtomicReference<>("v1");
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final CompanionSnapshotCache cache = new CompanionSnapshotCache(
            (userId, hour) -> {
                loads.incrementAndGet();
                return new CompanionMessageService.Snapshot("이름" + loads.get(), null, List.of(), List.of(), null);
            },
            userId -> fingerprint.get(),
            clock::get);

    @Test
    @DisplayName("바뀐 게 없으면 직전에 만든 결과를 그대로 쓴다")
    void 바뀐_게_없으면_다시_만들지_않는다() throws Exception {
        CompanionMessageService.Snapshot first = cache.load(1L, 20);
        clock.addAndGet(10_000); // 10초 뒤 다시 묻는다
        assertSame(first, cache.load(1L, 20));
        assertEquals(1, loads.get());
    }

    @Test
    @DisplayName("사이트에서 무언가 바뀌면(지문이 달라지면) 바로 새로 만든다")
    void 바뀌면_새로_만든다() throws Exception {
        cache.load(1L, 20);
        fingerprint.set("v2");
        cache.load(1L, 20);
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("바뀐 게 없어도 1분이 지나면 새로 만든다 — 트렌드처럼 남이 바꾸는 데이터")
    void 오래되면_새로_만든다() throws Exception {
        cache.load(1L, 20);
        clock.addAndGet(CompanionSnapshotCache.MAX_AGE_MS);
        cache.load(1L, 20);
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("사람마다, 저녁 경고 시각마다 따로 기억한다")
    void 사람과_설정마다_따로() throws Exception {
        cache.load(1L, 20);
        cache.load(2L, 20);
        cache.load(1L, 21);
        assertEquals(3, loads.get());
    }
}
