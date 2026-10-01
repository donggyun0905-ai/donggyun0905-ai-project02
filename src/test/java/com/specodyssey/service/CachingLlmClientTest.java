package com.specodyssey.service;

import com.specodyssey.dao.ExternalApiCacheDao;
import com.specodyssey.dto.ExternalApiCacheDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** EXTERNAL_API_CACHE를 실제로 쓰는 DB 통합테스트. 테스트마다 고유한 프롬프트를 쓰고 끝나면 그 행을 지운다. */
class CachingLlmClientTest {

    static class Reason {
        String reason;
    }

    private final ExternalApiCacheDao cacheDao = new ExternalApiCacheDao();
    private final String prompt = "캐시 테스트 프롬프트 " + System.nanoTime();
    private final String key = CachingLlmClient.requestKey(prompt, Reason.class);

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement(
                     "DELETE FROM EXTERNAL_API_CACHE WHERE api_type = ? AND request_key = ?")) {
            p.setString(1, CachingLlmClient.API_TYPE);
            p.setString(2, key);
            p.executeUpdate();
        }
    }

    @Test
    void 성공하면_저장하고_다음_실패_때_직전_결과로_대체한다() throws Exception {
        LlmClient ok = new StubLlmClient().register(Reason.class, "{\"reason\":\"처음 성공\"}");
        assertEquals("처음 성공", new CachingLlmClient(ok).completeJson(prompt, Reason.class).reason);

        ExternalApiCacheDto saved = cacheDao.findByTypeAndKey(CachingLlmClient.API_TYPE, key);
        assertNotNull(saved);
        assertEquals("SUCCESS", saved.getStatus());

        // ttl 0 = 캐시를 바로 쓰지 않고 호출 → 503 실패 → 직전 결과로 대체
        LlmClient down = new CachingLlmClient(StubLlmClient.failing(503), Duration.ZERO);
        assertEquals("처음 성공", down.completeJson(prompt, Reason.class).reason);
    }

    @Test
    void 유효기간_안의_캐시가_있으면_호출하지_않는다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LlmClient counting = new LlmClient() {
            @Override
            public <T> T completeJson(String p, Class<T> type) throws ExternalApiException {
                calls.incrementAndGet();
                return new StubLlmClient().register(Reason.class, "{\"reason\":\"호출됨\"}").completeJson(p, type);
            }
        };
        LlmClient cached = new CachingLlmClient(counting, Duration.ofHours(1));

        cached.completeJson(prompt, Reason.class);
        cached.completeJson(prompt, Reason.class);

        assertEquals(1, calls.get());
    }

    @Test
    void 직전_결과도_없으면_FAILED를_남기고_예외를_그대로_던진다() throws Exception {
        LlmClient down = new CachingLlmClient(StubLlmClient.failing(429));

        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> down.completeJson(prompt, Reason.class));

        assertEquals(429, e.getStatusCode());
        ExternalApiCacheDto failed = cacheDao.findByTypeAndKey(CachingLlmClient.API_TYPE, key);
        assertNotNull(failed);
        assertEquals("FAILED", failed.getStatus());
    }

    @Test
    void FAILED_뒤에_성공하면_SUCCESS로_덮어쓴다() throws Exception {
        assertThrows(ExternalApiException.class,
                () -> new CachingLlmClient(StubLlmClient.failing(500)).completeJson(prompt, Reason.class));

        LlmClient ok = new StubLlmClient().register(Reason.class, "{\"reason\":\"복구\"}");
        assertEquals("복구", new CachingLlmClient(ok).completeJson(prompt, Reason.class).reason);
        assertEquals("SUCCESS", cacheDao.findByTypeAndKey(CachingLlmClient.API_TYPE, key).getStatus());
    }

    @Test
    void 캐시_키는_타입명과_해시로_255자를_넘지_않는다() {
        String longPrompt = "가".repeat(10_000);
        String k = CachingLlmClient.requestKey(longPrompt, Reason.class);

        assertTrue(k.startsWith("Reason:"));
        assertTrue(k.length() <= 255);
        assertTrue(!k.equals(CachingLlmClient.requestKey(longPrompt + "!", Reason.class)));
    }
}
