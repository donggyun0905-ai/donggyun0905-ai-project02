package com.specodyssey.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Judge0 CE 코드 실행 API 호출 유틸. 관련 요구사항: FR-53 (일일 미션 "정답 입력하기" 컴파일 확인)
 * 관련 규칙: claude.md "외부 API 호출은 타임아웃을 직접 지정", "재시도는 429·5xx·-1만"
 *
 * 설정(.env 또는 환경변수, AppConfig):
 *   JUDGE0_API_URL  기본 https://ce.judge0.com (Judge0 공식 무료 공개 서버 — 키 불필요, 호출 횟수 제한 있음)
 *                   RapidAPI를 쓰면 https://judge0-ce.p.rapidapi.com, 직접 띄웠다면 http://localhost:2358 등
 *   JUDGE0_API_KEY  RapidAPI 주소를 쓸 때만 필요. 다른 주소에는 보내지 않는다.
 *
 * 여기서는 "제출하고 결과를 받는다"까지만 한다. 결과를 컴파일 성공/실패로 해석하는 건 CodeCompileService의 몫이다.
 */
public final class Judge0Client {

    private static final String DEFAULT_URL = "https://ce.judge0.com";
    private static final String FIELDS = "token,status,stdout,stderr,compile_output,message";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_POLLS = 5;
    private static final long POLL_INTERVAL_MS = 1000;

    // Judge0 status id — 1 대기, 2 처리 중
    private static final int STATUS_IN_QUEUE = 1;
    private static final int STATUS_PROCESSING = 2;

    /** Judge0 실행 결과. 출력 문자열은 base64를 풀어 둔 상태다. */
    public record Result(String token, int statusId, String statusDescription, String stdout, String stderr,
                         String compileOutput, String message) {
    }

    private Judge0Client() {
    }

    /** 설정이 없어 호출할 수 없는 상태인지 — RapidAPI 주소인데 키가 없으면 false. */
    public static boolean isConfigured() {
        return !isRapidApi() || apiKey() != null;
    }

    private static boolean isRapidApi() {
        return baseUrl().contains("rapidapi.com");
    }

    public static Result submit(int languageId, String sourceCode) throws ExternalApiException {
        if (!isConfigured()) {
            throw new ExternalApiException("JUDGE0_API_KEY가 설정되지 않았습니다 (.env 또는 환경변수)", null);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language_id", languageId);
        body.put("source_code", encode(sourceCode));
        body.put("stdin", encode(""));
        body.put("cpu_time_limit", 2);
        body.put("wall_time_limit", 5);

        String url = baseUrl() + "/submissions?base64_encoded=true&wait=true&fields=" + FIELDS;
        Result result = parse(withRetry(() -> ExternalApiClient.postJson(url, body, headers(), TIMEOUT)));

        // wait=true여도 서버가 바쁘면 대기 상태로 돌아올 수 있어 token으로 몇 번 더 조회한다
        String token = result.token();
        for (int i = 0; i < MAX_POLLS && isPending(result) && token != null; i++) {
            sleep();
            String pollUrl = baseUrl() + "/submissions/" + token + "?base64_encoded=true&fields=" + FIELDS;
            result = parse(withRetry(() -> ExternalApiClient.get(pollUrl, headers(), TIMEOUT)));
        }
        if (isPending(result)) {
            throw new ExternalApiException("컴파일 확인이 제한 시간 안에 끝나지 않았습니다", null);
        }
        return result;
    }

    private static Result parse(String json) throws ExternalApiException {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonObject status = obj.has("status") && obj.get("status").isJsonObject()
                    ? obj.getAsJsonObject("status") : null;
            return new Result(
                    text(obj, "token"),
                    status == null ? -1 : status.get("id").getAsInt(),
                    status == null ? null : text(status, "description"),
                    decode(text(obj, "stdout")),
                    decode(text(obj, "stderr")),
                    decode(text(obj, "compile_output")),
                    decode(text(obj, "message")));
        } catch (RuntimeException e) {
            throw new ExternalApiException("Judge0 응답 JSON 파싱 실패", e);
        }
    }

    private interface Call {
        String run() throws ExternalApiException;
    }

    // 429·5xx·-1(타임아웃·네트워크)만 한 번 더 시도한다. 400·401 등은 다시 해도 같으므로 바로 실패.
    private static String withRetry(Call call) throws ExternalApiException {
        ExternalApiException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.run();
            } catch (ExternalApiException e) {
                last = e;
                int code = e.getStatusCode();
                boolean retryable = code == -1 || code == 429 || code >= 500;
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                sleep();
            }
        }
        throw last;
    }

    private static boolean isPending(Result r) {
        return r.statusId() == STATUS_IN_QUEUE || r.statusId() == STATUS_PROCESSING;
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        // 키는 RapidAPI 주소에만 보낸다 — 공개 서버나 직접 띄운 서버로 키가 새지 않게
        String key = apiKey();
        if (key != null && isRapidApi()) {
            headers.put("X-RapidAPI-Key", key);
            headers.put("X-RapidAPI-Host", URI.create(baseUrl()).getHost());
        }
        return headers;
    }

    private static String apiKey() {
        return AppConfig.get("JUDGE0_API_KEY");
    }

    private static String baseUrl() {
        String url = AppConfig.get("JUDGE0_API_URL");
        url = url == null ? DEFAULT_URL : url;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String text(JsonObject obj, String name) {
        JsonElement el = obj.get(name);
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }

    private static String encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    // Judge0는 base64를 76자마다 줄바꿈해서 돌려주므로 MIME 디코더로 푼다
    private static String decode(String s) {
        return s == null ? null : new String(Base64.getMimeDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static void sleep() throws ExternalApiException {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("컴파일 확인이 중단됨", e);
        }
    }
}
