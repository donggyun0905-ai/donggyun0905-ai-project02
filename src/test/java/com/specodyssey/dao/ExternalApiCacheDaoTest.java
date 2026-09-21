package com.specodyssey.dao;

import com.specodyssey.dto.ExternalApiCacheDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExternalApiCacheDaoTest {

    private final ExternalApiCacheDao dao = new ExternalApiCacheDao();

    @Test
    void insert_findByTypeAndKey() throws Exception {
        String requestKey = "백엔드 개발자_" + System.nanoTime();
        ExternalApiCacheDto cache = new ExternalApiCacheDto();
        cache.setApiType("WORKNET");
        cache.setRequestKey(requestKey);
        cache.setResponseBody("{\"result\":\"ok\"}");
        cache.setStatus("SUCCESS");
        cache.setCachedAt(LocalDateTime.now());
        cache.setExpiresAt(LocalDateTime.now().plusDays(7));

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, cache);
        }
        try {
            assertNotNull(id);
            ExternalApiCacheDto found = dao.findByTypeAndKey("WORKNET", requestKey);
            assertNotNull(found);
            assertEquals("SUCCESS", found.getStatus());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EXTERNAL_API_CACHE", id);
            }
        }
    }
}
