package com.specodyssey.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 로컬 가짜 서버로 Groq 응답·실패를 흉내 낸다. 마지막 테스트만 키가 있을 때 실제 Groq를 부른다. */
class GroqLlmClientTest {

    static class Reason {
        String reason;
    }

    // 테스트에서는 기다리지 않는다 (재시도 횟수·예산 규칙은 실제와 같게)
    private static final LlmRetryPolicy NO_WAIT = new LlmRetryPolicy(3, 0, Duration.ofSeconds(60));

    private HttpServer server;
    private final Deque<int[]> statuses = new ArrayDeque<>();
    private final Deque<String> bodies = new ArrayDeque<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 성공_응답의_content를_타입으로_파싱한다() throws Exception {
        respond(200, chat("{\"reason\":\"백엔드 기술 3개가 겹칩니다\"}"));

        Reason r = client("test-key").completeJson("추천 이유를 JSON으로", Reason.class);

        assertEquals("백엔드 기술 3개가 겹칩니다", r.reason);
        assertEquals("Bearer test-key", lastAuth.get());
        JsonObject req = JsonParser.parseString(lastRequest.get()).getAsJsonObject();
        assertEquals("json_object", req.getAsJsonObject("response_format").get("type").getAsString());
        assertEquals(GroqLlmClient.DEFAULT_MODEL, req.get("model").getAsString());
        assertEquals("추천 이유를 JSON으로", req.getAsJsonArray("messages").get(1).getAsJsonObject()
                .get("content").getAsString());
    }

    @Test
    void 일시적_실패_429와_5xx는_재시도해서_성공한다() throws Exception {
        respond(429, "{}");
        respond(503, "{}");
        respond(200, chat("{\"reason\":\"ok\"}"));

        assertEquals("ok", client("k").completeJson("p", Reason.class).reason);
        assertEquals(3, calls.get());
    }

    @Test
    void 일시적_실패가_계속되면_3번까지만_시도하고_상태코드를_그대로_알린다() {
        for (int i = 0; i < 5; i++) {
            respond(500, "{}");
        }
        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> client("k").completeJson("p", Reason.class));

        assertEquals(500, e.getStatusCode());
        assertEquals(3, calls.get());
    }

    @Test
    void 인증_실패_401은_재시도하지_않는다() {
        respond(401, "{}");
        respond(200, chat("{\"reason\":\"ok\"}"));

        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> client("k").completeJson("p", Reason.class));

        assertEquals(401, e.getStatusCode());
        assertEquals(1, calls.get());
    }

    @Test
    void 형식_오류_400은_한_번만_재시도한다() {
        respond(400, "{}");
        respond(400, "{}");
        respond(200, chat("{\"reason\":\"ok\"}"));

        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> client("k").completeJson("p", Reason.class));

        assertEquals(400, e.getStatusCode());
        assertEquals(2, calls.get());
    }

    @Test
    void 응답이_JSON이_아니면_한_번_재시도_후_FORMAT_ERROR() {
        respond(200, chat("이건 JSON이 아님"));
        respond(200, chat("{\"reason\":"));

        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> client("k").completeJson("p", Reason.class));

        assertEquals(LlmRetryPolicy.FORMAT_ERROR, e.getStatusCode());
        assertEquals(2, calls.get());
    }

    @Test
    void 토큰_한도에서_잘린_응답은_FORMAT_ERROR() {
        String truncated = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{\\\"reason\\\":\\\"ok\\\"}\"}}]}";
        respond(200, truncated);
        respond(200, truncated);

        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> client("k").completeJson("p", Reason.class));

        assertEquals(LlmRetryPolicy.FORMAT_ERROR, e.getStatusCode());
    }

    @Test
    void 키가_없으면_호출하지_않고_401() throws Exception {
        startServer();

        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> client(null).completeJson("p", Reason.class));

        assertEquals(401, e.getStatusCode());
        assertEquals(0, calls.get());
    }

    @Test
    void 예외_메시지에_API_키가_남지_않는다() {
        respond(401, "{}");

        ExternalApiException e = assertThrows(ExternalApiException.class,
                () -> client("SECRET-KEY-123").completeJson("p", Reason.class));

        assertFalse(e.getMessage().contains("SECRET-KEY-123"));
    }

    @Test
    void 실제_Groq_호출() throws Exception {
        assumeTrue(AppConfig.get("GROQ_API_KEY") != null, "GROQ_API_KEY가 없어 건너뜀");

        Reason r = GroqLlmClient.fromConfig().completeJson(
                "Java 백엔드 개발자 직무를 추천하는 이유를 한국어 한 문장으로 써라. 형식: {\"reason\":\"...\"}", Reason.class);

        assertNotNull(r.reason);
        assertTrue(!r.reason.isBlank());
        System.out.println("[Groq] " + r.reason);
    }

    // ================= 가짜 서버 =================

    private GroqLlmClient client(String apiKey) throws IOException {
        startServer();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat";
        return new GroqLlmClient(url, apiKey, null, Duration.ofSeconds(5), NO_WAIT);
    }

    private void respond(int status, String body) {
        statuses.add(new int[] {status});
        bodies.add(body);
    }

    private void startServer() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            calls.incrementAndGet();
            lastRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            int[] status = statuses.poll();
            String body = bodies.poll();
            byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status == null ? 500 : status[0], bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    private static String chat(String content) {
        JsonObject message = new JsonObject();
        message.addProperty("content", content);
        JsonObject choice = new JsonObject();
        choice.addProperty("finish_reason", "stop");
        choice.add("message", message);
        com.google.gson.JsonArray choices = new com.google.gson.JsonArray();
        choices.add(choice);
        JsonObject root = new JsonObject();
        root.add("choices", choices);
        return root.toString();
    }
}
