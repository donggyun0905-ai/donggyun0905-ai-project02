package com.specodyssey.companion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 웹 서버(서블릿 /api/companion/*)와 주고받기 — DB에는 직접 붙지 않는다.
 * 모두 POST, 쿠키 없음, 토큰은 Authorization: Bearer 로. 시간 제한을 꼭 둔다 (서버가 멈춰도 캐릭터는 안 멈추게).
 */
public class ApiClient {

    /** 연결이 해제됐거나 토큰이 틀림 — 다시 연결해야 한다 */
    public static class UnauthorizedException extends IOException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public record Tier(int index, String name, String title, int score, String nextName, int pointsToNext) {
    }

    public record Trend(String name, String summary, String url) {
    }

    /**
     * @param signedOut 그 PC 브라우저에서 사이트를 로그아웃했다 — 다른 값은 비어 있고 siteUrl은 로그인 화면
     * @param account   연결된 계정 (사용자 id) — 이 PC 브라우저에서 다른 계정으로 로그인하면 바뀐다
     */
    public record Snapshot(String userName, Tier tier, List<Message> messages, String siteUrl, List<Trend> trends,
                           String summary, boolean signedOut, Long account) {
    }

    /** 서버에 올라간 최신 설치 파일 (없으면 version이 null) */
    public record Latest(String version, String notes, String sha256, long size) {
    }

    /** 연습장 — version은 마지막 저장 시각 (웹과 동시에 고쳤는지 비교) */
    public record Note(String text, String version) {
    }

    /** 연습장 저장 결과 — conflict면 웹 쪽 최신 내용이 들어 있다 */
    public record NoteSave(boolean conflict, String version, String serverText) {
    }

    private static final Gson GSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 일회용 코드 → 캐릭터 전용 토큰.
     * @param previousToken 이 PC가 들고 있던 예전 토큰 — 서버가 그 연결을 끊어 한 PC에 연결이 하나만 남는다
     */
    public String exchange(String server, String code, String deviceName, String previousToken)
            throws IOException, InterruptedException {
        JsonObject body = post(server, "/token", null, Map.of("code", code, "device", deviceName == null ? "" : deviceName,
                "previous", previousToken == null ? "" : previousToken));
        return body.get("token").getAsString();
    }

    public Snapshot messages(String server, String token, int eveningHour) throws IOException, InterruptedException {
        JsonObject body = post(server, "/messages", token, Map.of("eveningHour", String.valueOf(eveningHour)));
        if (body.has("signedOut") && body.get("signedOut").getAsBoolean()) {
            return new Snapshot(null, null, List.of(), str(body, "siteUrl"), List.of(), null, true, null);
        }
        List<Message> list = new ArrayList<>();
        JsonArray arr = body.getAsJsonArray("messages");
        if (arr != null) {
            for (JsonElement e : arr) {
                JsonObject m = e.getAsJsonObject();
                list.add(new Message(str(m, "key"), str(m, "kind"), str(m, "label"), str(m, "text"),
                        str(m, "linkText"), str(m, "url"),
                        m.has("notificationId") && !m.get("notificationId").isJsonNull() ? m.get("notificationId").getAsLong() : null,
                        false));
            }
        }
        Tier tier = body.has("tier") && body.get("tier").isJsonObject() ? GSON.fromJson(body.get("tier"), Tier.class) : null;
        List<Trend> trends = new ArrayList<>();
        if (body.has("trends") && body.get("trends").isJsonArray()) {
            for (JsonElement e : body.getAsJsonArray("trends")) {
                JsonObject t = e.getAsJsonObject();
                trends.add(new Trend(str(t, "name"), str(t, "summary"), str(t, "url")));
            }
        }
        Long account = body.has("account") && !body.get("account").isJsonNull() ? body.get("account").getAsLong() : null;
        return new Snapshot(str(body, "userName"), tier, list, str(body, "siteUrl"), trends, str(body, "summary"), false, account);
    }

    /** 가벼운 확인 — 지금 이 PC의 캐릭터가 어느 계정인지, 로그아웃해 쉬는 중인지 */
    public record WhoAmI(Long account, boolean signedOut) {
    }

    public WhoAmI whoami(String server, String token) throws IOException, InterruptedException {
        JsonObject b = post(server, "/whoami", token, Map.of());
        Long account = b.has("account") && !b.get("account").isJsonNull() ? b.get("account").getAsLong() : null;
        return new WhoAmI(account, b.has("signedOut") && b.get("signedOut").getAsBoolean());
    }

    public Latest latest(String server, String token) throws IOException, InterruptedException {
        JsonObject b = post(server, "/latest", token, Map.of());
        return new Latest(str(b, "version"), str(b, "notes"), str(b, "sha256"), b.has("size") ? b.get("size").getAsLong() : 0);
    }

    public Note note(String server, String token) throws IOException, InterruptedException {
        JsonObject b = post(server, "/note", token, Map.of());
        return new Note(str(b, "text"), str(b, "version"));
    }

    public NoteSave saveNote(String server, String token, String text, String version, boolean force)
            throws IOException, InterruptedException {
        HttpResponse<String> resp = send(server, "/note-save", token, Map.of("text", text, "version", version == null ? "" : version,
                "force", String.valueOf(force)));
        JsonObject b = GSON.fromJson(resp.body(), JsonObject.class);
        if (resp.statusCode() == 409) {
            return new NoteSave(true, str(b, "version"), str(b, "text"));
        }
        check(resp, b);
        return new NoteSave(false, str(b, "version"), null);
    }

    /** 최신 설치 파일을 to에 받는다 */
    public void download(String server, String token, java.nio.file.Path to, java.util.function.DoubleConsumer progress, long size)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(server + "/api/companion/download"))
                .timeout(Duration.ofMinutes(10))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<java.io.InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IOException("내려받지 못했어요 (HTTP " + resp.statusCode() + ")");
        }
        try (java.io.InputStream in = resp.body(); var out = java.nio.file.Files.newOutputStream(to)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (size > 0) {
                    progress.accept(Math.min(1.0, done / (double) size));
                }
            }
        }
    }

    public void markRead(String server, String token, long notificationId) throws IOException, InterruptedException {
        post(server, "/read", token, Map.of("id", String.valueOf(notificationId)));
    }

    public void disconnect(String server, String token) throws IOException, InterruptedException {
        post(server, "/disconnect", token, Map.of());
    }

    private JsonObject post(String server, String path, String token, Map<String, String> form)
            throws IOException, InterruptedException {
        HttpResponse<String> resp = send(server, path, token, form);
        JsonObject json;
        try {
            json = GSON.fromJson(resp.body(), JsonObject.class);
        } catch (RuntimeException e) {
            throw new IOException("서버 응답을 읽지 못했습니다 (HTTP " + resp.statusCode() + ")");
        }
        check(resp, json);
        return json;
    }

    private static void check(HttpResponse<String> resp, JsonObject json) throws IOException {
        if (resp.statusCode() == 401) {
            throw new UnauthorizedException(json != null && json.has("message") ? json.get("message").getAsString() : "연결이 해제됐어요.");
        }
        if (resp.statusCode() != 200 || json == null) {
            throw new IOException(json != null && json.has("message") ? json.get("message").getAsString()
                    : "서버 오류 (HTTP " + resp.statusCode() + ")");
        }
    }

    private HttpResponse<String> send(String server, String path, String token, Map<String, String> form)
            throws IOException, InterruptedException {
        StringJoiner body = new StringJoiner("&");
        form.forEach((k, v) -> body.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(server + "/api/companion" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
