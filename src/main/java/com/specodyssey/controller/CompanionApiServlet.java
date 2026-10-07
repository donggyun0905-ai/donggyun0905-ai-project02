package com.specodyssey.controller;

import com.google.gson.Gson;
import com.specodyssey.dto.CompanionDeviceDto;
import com.specodyssey.service.NotificationService;
import com.specodyssey.service.companion.CompanionAuthService;
import com.specodyssey.service.companion.CompanionMessageService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 데스크톱 캐릭터(exe)가 부르는 API — 웹 세션 대신 캐릭터 전용 토큰(Authorization: Bearer …)으로 사용자를 확인한다.
 *   POST /api/companion/token       code, device → 토큰 발급 (토큰 없이 부르는 유일한 곳 — 일회용 코드로 확인)
 *   POST /api/companion/messages    지금 말할 것들 + 등급 (캐릭터 그림)
 *   POST /api/companion/read        id → 사이트 알림 읽음 처리 (웹 헤더의 안 읽은 개수도 같이 준다)
 *   POST /api/companion/disconnect  이 토큰 연결 해제
 * SessionFilter 공개 경로(/api/companion/)이고, 쿠키를 쓰지 않아 CSRF 대상이 아니다. 토큰으로는 그 사용자의
 * 안내 문장 조회·알림 읽음·연결 해제만 할 수 있다. 모두 POST — GET은 SecurityHeadersFilter가 세션을 만들어서
 * 1분마다 부르면 세션이 쌓이기 때문이다.
 */
@WebServlet("/api/companion/*")
public class CompanionApiServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(CompanionApiServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final CompanionAuthService authService = new CompanionAuthService();
    private final CompanionMessageService messageService = new CompanionMessageService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        writeJson(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, Map.of("message", "POST로 요청해 주세요."));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            if ("/token".equals(action)) {
                CompanionAuthService.Connected connected = authService.exchange(req.getParameter("code"), req.getParameter("device"));
                if (connected == null) {
                    writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED,
                            Map.of("message", "연결 코드가 만료됐거나 이미 사용됐어요. 사이트에서 '캐릭터 켜기'를 다시 눌러 주세요."));
                    return;
                }
                writeJson(resp, HttpServletResponse.SC_OK, Map.of("token", connected.token()));
                return;
            }

            CompanionDeviceDto device = authService.authenticate(bearer(req));
            if (device == null) {
                writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("message", "연결이 해제됐어요. 사이트에서 다시 연결해 주세요."));
                return;
            }
            Long userId = device.getUserId();
            switch (action) {
                case "/messages" -> writeJson(resp, HttpServletResponse.SC_OK, snapshotBody(req, messageService.load(userId)));
                case "/read" -> {
                    Long id = parseId(req.getParameter("id"));
                    if (id != null) {
                        notificationService.open(id, userId); // 남의 알림이면 아무것도 바뀌지 않는다
                    }
                    writeJson(resp, HttpServletResponse.SC_OK, Map.of("ok", true));
                }
                case "/disconnect" -> {
                    authService.revoke(userId, device.getId());
                    writeJson(resp, HttpServletResponse.SC_OK, Map.of("ok", true));
                }
                default -> writeJson(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("message", "없는 요청입니다."));
            }
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "캐릭터 API 처리 실패 (" + action + ")", e);
            writeJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Map.of("message", "잠시 후 다시 시도해 주세요."));
        }
    }

    private static Map<String, Object> snapshotBody(HttpServletRequest req, CompanionMessageService.Snapshot s) {
        String base = baseUrl(req);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (CompanionMessageService.Message m : s.messages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", m.key());
            item.put("kind", m.kind());
            item.put("label", m.label());
            item.put("text", m.text());
            item.put("linkText", m.linkText());
            item.put("url", m.path() == null ? null : base + m.path());
            item.put("notificationId", m.notificationId());
            messages.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userName", s.userName());
        body.put("tier", s.tier());
        body.put("messages", messages);
        body.put("siteUrl", base + "/dashboard");
        return body;
    }

    /** 이 서버의 주소 — 캐릭터가 브라우저로 열 화면 주소에 붙인다 (팀원마다 자기 PC 서버를 써도 그 서버로) */
    static String baseUrl(HttpServletRequest req) {
        String scheme = req.getScheme();
        int port = req.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + req.getServerName() + (defaultPort ? "" : ":" + port) + req.getContextPath();
    }

    private static String bearer(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }

    private static Long parseId(String raw) {
        try {
            return raw == null ? null : Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(GSON.toJson(body));
    }
}
