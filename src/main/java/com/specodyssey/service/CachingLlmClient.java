package com.specodyssey.service;

import com.google.gson.Gson;
import com.specodyssey.dao.ExternalApiCacheDao;
import com.specodyssey.dto.ExternalApiCacheDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.ExternalApiClient;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.LlmClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LlmClient에 EXTERNAL_API_CACHE(api_type='LLM') 캐시를 씌운다. 관련 요구사항: FR-111 · 112
 *
 * 순서: 캐시 조회 → (유효기간 안이면 바로 반환) → 호출 → 성공하면 저장 / 실패하면 직전 성공 결과로 대체.
 * 직전 성공 결과도 없으면 FAILED로 기록하고 예외를 그대로 던진다 — 그때 화면 문구는 호출부가 정한다.
 * 화면이 대체 여부를 알아야 하면 completeJsonWithStatus로 LlmResult(상태·시각·안내 문구)를 받는다.
 * 같은 프롬프트·같은 타입이면 같은 캐시 키다. 캐시 DB 오류는 LLM 결과를 막지 않도록 로그만 남긴다.
 *
 * 사용 예: CachingLlmClient llm = new CachingLlmClient(어떤 LlmClient 구현체든);
 */
public class CachingLlmClient implements LlmClient {

    static final String API_TYPE = "LLM";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final int MAX_TYPE_NAME_LENGTH = 100;
    private static final Logger LOG = Logger.getLogger(CachingLlmClient.class.getName());
    private static final Gson GSON = new Gson();

    private final LlmClient delegate;
    private final Duration ttl;
    private final ExternalApiCacheDao cacheDao = new ExternalApiCacheDao();

    public CachingLlmClient(LlmClient delegate) {
        this(delegate, DEFAULT_TTL);
    }

    /** ttl이 0이면 캐시를 바로 쓰지 않고 항상 호출한다 (실패 시 대체용으로만 씀) */
    public CachingLlmClient(LlmClient delegate, Duration ttl) {
        this.delegate = delegate;
        this.ttl = ttl;
    }

    @Override
    public <T> T completeJson(String prompt, Class<T> type) throws ExternalApiException {
        LlmResult<T> result = completeJsonWithStatus(prompt, type);
        if (result.isUnavailable()) {
            throw result.cause();
        }
        return result.getValue();
    }

    /**
     * completeJson과 같지만 예외를 던지지 않고, 결과와 함께 상태(새로 받음·캐시·직전 결과 대체·없음)를 돌려준다.
     * 화면이 "직전 결과로 대체" 안내나 "다시 시도" 버튼을 띄워야 할 때 쓴다 (FR-111).
     */
    public <T> LlmResult<T> completeJsonWithStatus(String prompt, Class<T> type) {
        String key = requestKey(prompt, type);
        ExternalApiCacheDto cached = readCache(key);
        LocalDateTime now = LocalDateTime.now();

        // 재사용 여부는 저장 때 정한 expires_at이 아니라 지금 이 객체의 ttl로 판단한다 — ttl 0이면 항상 새로 호출.
        // ttl 0을 따로 막는 이유: DATETIME이 초 단위로 반올림돼 cached_at이 지금보다 미래일 수 있다
        if (!ttl.isZero() && !ttl.isNegative() && cached != null && cached.getCachedAt() != null
                && cached.getCachedAt().plus(ttl).isAfter(now)) {
            T hit = parseOrNull(cached, type);
            if (hit != null) {
                return LlmResult.cached(hit, cached.getCachedAt());
            }
        }

        try {
            T result = delegate.completeJson(prompt, type);
            writeSuccess(key, GSON.toJson(result), now);
            return LlmResult.fresh(result, now);
        } catch (ExternalApiException e) {
            T fallback = cached == null ? null : parseOrNull(cached, type);
            if (fallback != null) {
                LOG.log(Level.WARNING, "LLM 호출 실패(HTTP " + e.getStatusCode() + ") — 직전 결과로 대체합니다: " + key);
                return LlmResult.fallback(fallback, cached.getCachedAt(), e);
            }
            writeFailure(key, now);
            return LlmResult.unavailable(e);
        }
    }

    // 프롬프트가 길어 그대로 키로 못 쓴다(VARCHAR(255)) — "타입명:SHA-256" 형태로 줄인다
    static String requestKey(String prompt, Class<?> type) {
        String typeName = type.getSimpleName();
        if (typeName.length() > MAX_TYPE_NAME_LENGTH) {
            typeName = typeName.substring(0, MAX_TYPE_NAME_LENGTH);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((type.getName() + "\n" + prompt).getBytes(StandardCharsets.UTF_8));
            return typeName + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    private ExternalApiCacheDto readCache(String key) {
        try {
            ExternalApiCacheDto cached = cacheDao.findByTypeAndKey(API_TYPE, key);
            return cached != null && "SUCCESS".equals(cached.getStatus()) ? cached : null;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "LLM 캐시 조회 실패 — 캐시 없이 진행합니다", e);
            return null;
        }
    }

    private <T> T parseOrNull(ExternalApiCacheDto cached, Class<T> type) {
        if (cached.getResponseBody() == null) {
            return null;
        }
        try {
            return ExternalApiClient.parseJson(cached.getResponseBody(), type);
        } catch (ExternalApiException e) {
            LOG.log(Level.WARNING, "LLM 캐시 내용을 읽지 못했습니다 — 타입이 바뀌었을 수 있습니다: " + cached.getRequestKey(), e);
            return null;
        }
    }

    private void writeSuccess(String key, String json, LocalDateTime now) {
        ExternalApiCacheDto cache = new ExternalApiCacheDto();
        cache.setApiType(API_TYPE);
        cache.setRequestKey(key);
        cache.setResponseBody(json);
        cache.setCachedAt(now);
        cache.setExpiresAt(now.plus(ttl));
        try (Connection conn = DBUtil.getConnection()) {
            cacheDao.upsertSuccess(conn, cache);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "LLM 캐시 저장 실패 — 결과는 그대로 반환합니다", e);
        }
    }

    private void writeFailure(String key, LocalDateTime now) {
        try {
            cacheDao.insertFailedIfAbsent(API_TYPE, key, now);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "LLM 실패 기록 저장 실패", e);
        }
    }
}
