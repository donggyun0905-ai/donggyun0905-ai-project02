package com.specodyssey.service;

import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LLM 결과를 화면에 넘기는 공통 형식 (FR-111 공통 처리). 결과와 함께 "어떤 상태의 결과인지"를 알려준다.
 *
 * - FRESH: 방금 새로 받은 결과
 * - CACHED: 유효기간 안의 캐시를 재사용 (화면에는 FRESH와 같게 보여도 됨)
 * - FALLBACK: 호출이 실패해서 직전 성공 결과로 대체 — 화면은 "AI 응답을 받지 못해 ○○에 만든 결과" 안내를 띄운다
 * - UNAVAILABLE: 호출 실패 + 대체할 결과도 없음 — value는 null, 화면은 안내 문구(와 재시도 버튼)를 띄운다
 *
 * JSP의 EL이 getter로 읽으므로 record 대신 클래스로 둔다 (Tomcat 10.1의 EL 5.0은 record 접근자를 못 읽음).
 * 예: ${ai.fallback}, ${ai.cachedAtText}, ${ai.message}, ${ai.retryable}
 */
public final class LlmResult<T> {

    public enum Status { FRESH, CACHED, FALLBACK, UNAVAILABLE }

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final T value;
    private final Status status;
    private final LocalDateTime cachedAt;
    private final ExternalApiException cause;

    private LlmResult(T value, Status status, LocalDateTime cachedAt, ExternalApiException cause) {
        this.value = value;
        this.status = status;
        this.cachedAt = cachedAt;
        this.cause = cause;
    }

    static <T> LlmResult<T> fresh(T value, LocalDateTime savedAt) {
        return new LlmResult<>(value, Status.FRESH, savedAt, null);
    }

    static <T> LlmResult<T> cached(T value, LocalDateTime cachedAt) {
        return new LlmResult<>(value, Status.CACHED, cachedAt, null);
    }

    static <T> LlmResult<T> fallback(T value, LocalDateTime cachedAt, ExternalApiException cause) {
        return new LlmResult<>(value, Status.FALLBACK, cachedAt, cause);
    }

    static <T> LlmResult<T> unavailable(ExternalApiException cause) {
        return new LlmResult<>(null, Status.UNAVAILABLE, null, cause);
    }

    public T getValue() {
        return value;
    }

    public Status getStatus() {
        return status;
    }

    /** 결과가 만들어진 시각. UNAVAILABLE이면 null */
    public LocalDateTime getCachedAt() {
        return cachedAt;
    }

    /** 화면 표시용 "yyyy-MM-dd HH:mm". 시각이 없으면 빈 문자열 */
    public String getCachedAtText() {
        return cachedAt == null ? "" : cachedAt.format(DISPLAY);
    }

    /** 보여줄 결과가 있는지 (FRESH·CACHED·FALLBACK) */
    public boolean isAvailable() {
        return value != null;
    }

    public boolean isFallback() {
        return status == Status.FALLBACK;
    }

    public boolean isUnavailable() {
        return status == Status.UNAVAILABLE;
    }

    /** 실패했을 때 "다시 시도"를 보여줄 만한지 — 시간이 지나면 풀리는 실패(429·5xx·타임아웃)만 true */
    public boolean isRetryable() {
        if (cause == null) {
            return false;
        }
        int code = cause.getStatusCode();
        return code == 429 || code >= 500 || code == -1;
    }

    /** 화면 안내 문구. FRESH·CACHED는 안내가 필요 없어 null */
    public String getMessage() {
        return switch (status) {
            case FALLBACK -> "AI 응답을 받지 못해 " + getCachedAtText() + "에 만든 직전 결과를 보여드립니다.";
            case UNAVAILABLE -> isRetryable()
                    ? "AI 응답을 일시적으로 받을 수 없습니다. 잠시 후 다시 시도해 주세요."
                    : "AI 기능을 지금 사용할 수 없습니다.";
            default -> null;
        };
    }

    // CachingLlmClient.completeJson이 예외를 원래 상태 코드 그대로 다시 던질 때만 쓴다 — 화면에는 노출하지 않는다
    ExternalApiException cause() {
        return cause;
    }
}
