package com.specodyssey.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalApiClientTest {

    static class Sample {
        String name;
        int score;
    }

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parseJson_유효한_JSON은_객체로_변환된다() throws Exception {
        Sample sample = ExternalApiClient.parseJson("{\"name\":\"backend\",\"score\":90}", Sample.class);
        assertEquals("backend", sample.name);
        assertEquals(90, sample.score);
    }

    @Test
    void parseJson_깨진_JSON은_ExternalApiException으로_알린다() {
        assertThrows(ExternalApiClient.ExternalApiException.class,
                () -> ExternalApiClient.parseJson("{이건 JSON이 아님", Sample.class));
    }

    @Test
    void HTTP_429_응답은_상태코드가_그대로_담긴다() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/limited", exchange -> {
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/limited?authKey=SECRET123";

        ExternalApiClient.ExternalApiException e = assertThrows(ExternalApiClient.ExternalApiException.class,
                () -> ExternalApiClient.get(url));

        assertEquals(429, e.getStatusCode());
        assertFalse(e.getMessage().contains("SECRET123"), "예외 메시지에 인증키가 남으면 안 된다: " + e.getMessage());
        assertTrue(e.getMessage().contains("/limited"), "경로는 메시지에 남아있어야 한다: " + e.getMessage());
    }

    @Test
    void 연결_실패시_예외메시지에서도_쿼리스트링은_빠진다() {
        // 127.0.0.1:1 은 통상 아무 서비스도 없어 연결이 즉시 거부된다.
        String url = "http://127.0.0.1:1/worknet?authKey=SECRET456";

        ExternalApiClient.ExternalApiException e = assertThrows(ExternalApiClient.ExternalApiException.class,
                () -> ExternalApiClient.get(url));

        assertEquals(-1, e.getStatusCode());
        assertFalse(e.getMessage().contains("SECRET456"), "예외 메시지에 인증키가 남으면 안 된다: " + e.getMessage());
    }
}
