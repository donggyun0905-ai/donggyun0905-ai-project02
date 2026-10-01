package com.specodyssey.service.work24;

import com.specodyssey.dao.ExternalApiCacheDao;
import com.specodyssey.dto.ExternalApiCacheDto;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 고용24 응답을 EXTERNAL_API_CACHE에 남기는 규칙 모음.
 * 관련 요구사항: FR-111 (실패 시 직전 결과로 대체), FR-112 (결과가 없으면 안내), NFR-1 (중복 호출 최소화)
 *
 * - 성공: (WORKNET, request_key) 행을 새 응답으로 덮어쓴다.
 * - 실패: 직전 성공 행이 있으면 건드리지 않는다. 한 번도 성공한 적 없는 키만 FAILED로 남긴다.
 */
public final class Work24Cache {

    public static final String API_TYPE = "WORKNET";
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    // docs/db-design.md EXTERNAL_API_CACHE — 워크넷 캐시 TTL 7일. 매일 갱신하므로 정상이면 만료 전에 새 값으로 바뀐다.
    private static final int TTL_DAYS = 7;
    // response_body는 TEXT(최대 65,535바이트). 잘린 JSON이 캐시되면 읽는 쪽이 파싱에 실패하므로 넘으면 저장하지 않는다.
    static final int MAX_BODY_BYTES = 65_000;

    private final ExternalApiCacheDao cacheDao = new ExternalApiCacheDao();

    public void saveSuccess(Connection conn, String requestKey, String body) throws SQLException {
        int bytes = body.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BODY_BYTES) {
            throw new SQLException("캐시 본문이 TEXT 한도를 넘습니다 (" + requestKey + ", " + bytes + "바이트)");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        ExternalApiCacheDto cache = new ExternalApiCacheDto();
        cache.setApiType(API_TYPE);
        cache.setRequestKey(requestKey);
        cache.setResponseBody(body);
        cache.setCachedAt(now);
        cache.setExpiresAt(now.plusDays(TTL_DAYS));
        cacheDao.upsertSuccess(conn, cache);
    }

    public void saveFailure(String requestKey) throws SQLException {
        cacheDao.insertFailedIfAbsent(API_TYPE, requestKey, LocalDateTime.now(ZONE));
    }

    public boolean collectedToday(String requestKeyPrefix) throws SQLException {
        return cacheDao.existsSuccessSince(API_TYPE, requestKeyPrefix, LocalDateTime.now(ZONE).toLocalDate().atStartOfDay());
    }
}
