package com.specodyssey.service.work24;

import com.specodyssey.util.AppConfig;
import com.specodyssey.util.CircuitBreaker;
import com.specodyssey.util.ExternalApiClient;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 고용24 Open API 호출 공용 클라이언트.
 * 인증키는 API마다 따로 발급되므로 .env의 키 이름을 호출부가 지정한다 (소스 하드코딩 금지, NFR-3).
 *
 * 고용24는 인증키 오류·미신청 서비스도 HTTP 200으로 돌려주고 본문에만 오류를 담는다
 * (XML: &lt;error&gt;…&lt;/error&gt;, JSON: message_cd/message). 이런 응답은 성공으로 캐시하면 안 되므로 예외로 바꾼다.
 */
public class Work24Client {

    /**
     * 연속 3번 실패하면 5분 끊는다. 고용24는 공고·직무·전공 정보를 여러 API로 나눠 받는데 장애는 보통
     * 전체에 걸리므로 클라이언트 하나에 서킷 하나를 둔다. JVM 전체 공유 — 스케줄러와 화면이 같이 쓴다.
     */
    private static final CircuitBreaker BREAKER = new CircuitBreaker("고용24", 3, 5 * 60_000L);

    private static final String BASE_URL = "https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo";
    // 학과 전체 목록(약 180KB)처럼 응답이 큰 호출이 있어 기본 10초보다 넉넉하게 잡는다
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern XML_ERROR = Pattern.compile("<error>(.*?)</error>", Pattern.DOTALL);
    private static final Pattern JSON_ERROR = Pattern.compile("\"message_cd\"\\s*:");

    /**
     * @param apiCode    예: "213L01"
     * @param apiKeyName .env 키 이름 (예: WORK24_MAJOR_INFO_API_KEY)
     * @param params     authKey를 뺀 요청 파라미터
     */
    public String call(String apiCode, String apiKeyName, Map<String, String> params) throws ExternalApiException {
        String authKey = AppConfig.get(apiKeyName);
        if (authKey == null) {
            throw new ExternalApiException(apiKeyName + "가 .env에 없습니다", null);
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("authKey", authKey);
        query.putAll(params);

        // 고용24가 장애일 때 매 호출이 같은 실패를 반복하며 타임아웃만큼 기다리는 것을 막는다 (2026-10-08).
        // 끊긴 동안에는 바로 예외를 던져 호출부가 캐시된 직전 결과로 넘어간다(FR-112, Work24Cache).
        if (!BREAKER.allowRequest()) {
            throw new ExternalApiException("고용24 호출이 연속 실패해 잠시 끊었습니다 ("
                    + BREAKER.millisUntilRetry() / 1000 + "초 뒤 다시 시도)", null, 503);
        }
        String body;
        try {
            body = ExternalApiClient.get(BASE_URL + apiCode + ".do?" + encode(query), Map.of(), TIMEOUT);
        } catch (ExternalApiException e) {
            BREAKER.recordFailure();
            throw e;
        }
        // 응답은 왔는데 API가 오류 코드를 준 경우도 실패로 센다 — 키 만료·한도 초과가 여기로 온다
        try {
            checkApiError(apiCode, body);
        } catch (ExternalApiException e) {
            BREAKER.recordFailure();
            throw e;
        }
        BREAKER.recordSuccess();
        return body;
    }

    static void checkApiError(String apiCode, String body) throws ExternalApiException {
        if (body == null || body.isBlank()) {
            throw new ExternalApiException("고용24 " + apiCode + " 응답이 비어 있습니다", null);
        }
        Matcher xml = XML_ERROR.matcher(body);
        if (xml.find()) {
            throw new ExternalApiException("고용24 " + apiCode + " 오류: " + xml.group(1).trim(), null);
        }
        if (JSON_ERROR.matcher(body).find()) {
            throw new ExternalApiException("고용24 " + apiCode + " 오류 응답: " + abbreviate(body), null);
        }
    }

    private static String encode(Map<String, String> query) {
        StringBuilder sb = new StringBuilder();
        query.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(k).append('=').append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
