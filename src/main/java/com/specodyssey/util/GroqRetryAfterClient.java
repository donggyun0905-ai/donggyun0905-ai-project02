package com.specodyssey.util;

import com.google.gson.Gson;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Groq 호출 1회 — 실패 응답이어도 예외 대신 상태코드·본문·retry-after 헤더를 그대로 돌려준다.
 * 관련 요구사항: FR-54 트렌드 기술 수집 (Groq 429 대기 시간 단축)
 *
 * 공용 ExternalApiClient는 실패 시 상태코드만 넘기고 헤더를 버려서, 429일 때 Groq가 알려주는 대기 시간을 알 수 없다.
 * 뼈대(ExternalApiClient)는 그대로 두고 헤더가 필요한 호출부만 이 클래스를 쓴다. 재시도 판단은 호출부 책임이다.
 * 주의: ExternalApiClient의 Groq 동시 1건 쓰로틀을 거치지 않는다 — 다른 기능과 겹쳐 429가 나면 retry-after만큼 기다려 푼다.
 */
public final class GroqRetryAfterClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private static final Gson GSON = new Gson();

    /** 응답 1건. retryAfter는 헤더가 없거나 해석할 수 없으면 null. */
    public record Response(int statusCode, String body, Duration retryAfter) {
        public boolean isSuccess() {
            return statusCode < 400;
        }
    }

    private GroqRetryAfterClient() {
    }

    /** 네트워크 오류·타임아웃만 예외로 던진다 (상태코드 -1). HTTP 실패 응답은 Response로 돌려준다. */
    public static Response postJson(String url, Object requestBody, Map<String, String> headers, Duration timeout)
            throws ExternalApiException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));
        headers.forEach(builder::header);
        try {
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(),
                    parseRetryAfter(response.headers().firstValue("retry-after").orElse(null)));
        } catch (IOException e) {
            throw new ExternalApiException("외부 API 호출 실패: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("외부 API 호출이 중단됨: " + url, e);
        }
    }

    /** retry-after 헤더(초, 소수 가능)를 해석한다. 없거나 숫자가 아니면(HTTP 날짜 형식 등) null. */
    static Duration parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(value.trim());
            if (seconds < 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
                return null;
            }
            return Duration.ofMillis((long) Math.ceil(seconds * 1000));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
