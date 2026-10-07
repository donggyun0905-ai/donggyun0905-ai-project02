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

    public record Snapshot(String userName, Tier tier, List<Message> messages, String siteUrl) {
    }

    private static final Gson GSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** 일회용 코드 → 캐릭터 전용 토큰 */
    public String exchange(String server, String code, String deviceName) throws IOException, InterruptedException {
        JsonObject body = post(server, "/token", null, Map.of("code", code, "device", deviceName == null ? "" : deviceName));
        return body.get("token").getAsString();
    }

    public Snapshot messages(String server, String token) throws IOException, InterruptedException {
        JsonObject body = post(server, "/messages", token, Map.of());
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
        return new Snapshot(str(body, "userName"), tier, list, str(body, "siteUrl"));
    }

    public void markRead(String server, String token, long notificationId) throws IOException, InterruptedException {
        post(server, "/read", token, Map.of("id", String.valueOf(notificationId)));
    }

    public void disconnect(String server, String token) throws IOException, InterruptedException {
        post(server, "/disconnect", token, Map.of());
    }

    private JsonObject post(String server, String path, String token, Map<String, String> form)
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
        HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject json;
        try {
            json = GSON.fromJson(resp.body(), JsonObject.class);
        } catch (RuntimeException e) {
            throw new IOException("서버 응답을 읽지 못했습니다 (HTTP " + resp.statusCode() + ")");
        }
        if (resp.statusCode() == 401) {
            throw new UnauthorizedException(json != null && json.has("message") ? json.get("message").getAsString() : "연결이 해제됐어요.");
        }
        if (resp.statusCode() != 200 || json == null) {
            throw new IOException("서버 오류 (HTTP " + resp.statusCode() + ")");
        }
        return json;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
