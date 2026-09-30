package com.specodyssey.service.work24;

import com.specodyssey.util.AppConfig;
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

        String body = ExternalApiClient.get(BASE_URL + apiCode + ".do?" + encode(query), Map.of(), TIMEOUT);
        checkApiError(apiCode, body);
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
