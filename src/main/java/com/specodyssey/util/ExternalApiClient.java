package com.specodyssey.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 외부 API(워크넷·LLM·임베딩 등) 호출 공용 유틸.
 * 관련 요구사항: FR-111·112(실패 시 오류 처리), NFR-6(JSON 파싱 실패 예외처리)
 * 관련 규칙: claude.md "AI 호출은 반드시 서버(서블릿)에서" — 이 클래스는 서버 쪽 코드에서만 쓴다.
 *
 * "호출하고 JSON으로 파싱한다"는 가장 바깥 껍데기만 제공한다.
 * EXTERNAL_API_CACHE 확인/저장, 실패 시 직전 캐시로 대체하는 로직은 호출부(각 기능의 Service)의 책임이다 —
 * 캐시 정책은 기능마다(워크넷은 TTL 7일, LLM은 수동 트리거 등) 다르기 때문에 여기서 강제하지 않는다.
 */
public final class ExternalApiClient {

    public static class ExternalApiException extends Exception {
        // HTTP 응답 실패가 아니면 -1 (타임아웃·네트워크 오류 등). 429/400 등을 구분해 재시도 여부(FR-111)를 판단하는 데 쓴다.
        private final int statusCode;

        public ExternalApiException(String message, Throwable cause) {
            this(message, cause, -1);
        }

        public ExternalApiException(String message, Throwable cause, int statusCode) {
            super(message, cause);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    // 워크넷 같은 일반 API에는 충분하지만 LLM 응답처럼 느린 호출에는 짧을 수 있다 —
    // 그런 호출은 get/postJson의 timeout 인자로 개별 지정할 것.
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_TIMEOUT)
            .build();
    private static final Gson GSON = new Gson();

    private ExternalApiClient() {
    }

    public static String get(String url) throws ExternalApiException {
        return get(url, Map.of(), DEFAULT_TIMEOUT);
    }

    public static String get(String url, Map<String, String> headers) throws ExternalApiException {
        return get(url, headers, DEFAULT_TIMEOUT);
    }

    public static String get(String url, Map<String, String> headers, Duration timeout) throws ExternalApiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET();
        headers.forEach(builder::header);
        return send(builder, url);
    }

    public static String postJson(String url, Object requestBody) throws ExternalApiException {
        return postJson(url, requestBody, Map.of(), DEFAULT_TIMEOUT);
    }

    public static String postJson(String url, Object requestBody, Map<String, String> headers)
            throws ExternalApiException {
        return postJson(url, requestBody, headers, DEFAULT_TIMEOUT);
    }

    public static String postJson(String url, Object requestBody, Map<String, String> headers, Duration timeout)
            throws ExternalApiException {
        String json = GSON.toJson(requestBody);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(builder::header);
        return send(builder, url);
    }

    // 응답 JSON 문자열을 지정한 타입으로 파싱 — 실패 시 예외로 알린다 (NFR-6)
    public static <T> T parseJson(String json, Class<T> type) throws ExternalApiException {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new ExternalApiException("JSON 파싱 실패", e);
        }
    }

    private static String send(HttpRequest.Builder requestBuilder, String url) throws ExternalApiException {
        try {
            HttpResponse<String> response =
                    CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ExternalApiException(
                        "외부 API 응답 실패: HTTP " + response.statusCode() + " (" + maskQuery(url) + ")",
                        null, response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new ExternalApiException("외부 API 호출 실패: " + maskQuery(url), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("외부 API 호출이 중단됨: " + maskQuery(url), e);
        }
    }

    // 워크넷 인증키 등이 쿼리 문자열(authKey=...)로 넘어오는 경우가 있어, 예외 메시지에는 쿼리를 빼고
    // 경로까지만 남긴다 (claude.md "API 키를 소스에 하드코딩하지 않는다" 취지 — 로그에도 남기지 않는다).
    private static String maskQuery(String url) {
        int idx = url.indexOf('?');
        return idx == -1 ? url : url.substring(0, idx);
    }
}
