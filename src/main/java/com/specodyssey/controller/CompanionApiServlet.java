package com.specodyssey.controller;

import com.google.gson.Gson;
import com.specodyssey.dto.CompanionDeviceDto;
import com.specodyssey.dto.CompanionReleaseDto;
import com.specodyssey.service.NoteService;
import com.specodyssey.service.NotificationService;
import com.specodyssey.service.companion.CompanionReleaseService;
import com.specodyssey.service.companion.CompanionAuthService;
import com.specodyssey.service.companion.CompanionMessageService;
import com.specodyssey.service.companion.CompanionSnapshotCache;
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
 *   POST /api/companion/token       code, device, previous → 토큰 발급 (토큰 없이 부르는 유일한 곳 — 일회용 코드로 확인).
 *                                   previous(이 PC가 들고 있던 예전 토큰)가 있으면 그 연결은 끊는다 — 한 PC = 한 연결
 *   POST /api/companion/whoami      account, signedOut — 캐릭터가 2초마다 묻는 가벼운 확인 (계정이 바뀌면 바로 /messages)
 *   POST /api/companion/messages    지금 말할 것들 + 등급 (캐릭터 그림). 그 PC 브라우저에서 로그아웃했으면 signedOut만
 *                                   (CompanionLinkFilter). 바뀐 게 없으면 직전 결과를 다시 쓴다 (CompanionSnapshotCache)
 *   POST /api/companion/read        id → 사이트 알림 읽음 처리 (웹 헤더의 안 읽은 개수도 같이 준다)
 *   POST /api/companion/disconnect  이 토큰 연결 해제
 *   POST /api/companion/latest      최신 설치 파일 버전·바뀐 점·SHA-256 (캐릭터 업데이트 확인)
 *   POST /api/companion/download    최신 설치 파일 내려받기 (DB에 나눠 둔 것을 이어서 보낸다)
 *   POST /api/companion/note        연습장 내용·버전 / note-save  text, version, force → 저장 (웹과 동시에 고쳤으면 409)
 * SessionFilter 공개 경로(/api/companion/)이고, 쿠키를 쓰지 않아 CSRF 대상이 아니다. 토큰으로는 그 사용자의
 * 안내 문장 조회·알림 읽음·연결 해제만 할 수 있다. 모두 POST — GET은 SecurityHeadersFilter가 세션을 만들어서
 * 1분마다 부르면 세션이 쌓이기 때문이다.
 */
@WebServlet("/api/companion/*")
public class CompanionApiServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(CompanionApiServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final CompanionAuthService authService = new CompanionAuthService();
    private final CompanionSnapshotCache snapshots = new CompanionSnapshotCache();
    private final NotificationService notificationService = new NotificationService();
    private final CompanionReleaseService releaseService = new CompanionReleaseService();
    private final NoteService noteService = new NoteService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        writeJson(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, Map.of("message", "POST로 요청해 주세요."));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            if ("/token".equals(action)) {
                CompanionAuthService.Connected connected = authService.exchange(req.getParameter("code"),
                        req.getParameter("device"), req.getParameter("previous"));
                if (connected == null) {
                    writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED,
                            Map.of("message", "연결 코드가 만료됐거나 이미 사용됐어요. 사이트 '오셍이들'에서 '캐릭터 연결'을 다시 눌러 주세요."));
                    return;
                }
                writeJson(resp, HttpServletResponse.SC_OK, Map.of("token", connected.token()));
                return;
            }

            CompanionDeviceDto device = "/whoami".equals(action)
                    ? authService.peek(bearer(req))
                    : authService.authenticate(bearer(req));
            if (device == null) {
                writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED, Map.of("message", "연결이 해제됐어요. 사이트에서 다시 연결해 주세요."));
                return;
            }
            Long userId = device.getUserId();
            // 그 PC 브라우저에서 로그아웃했다 — 연결은 두고, 다시 로그인할 때까지 그 계정 내용은 내주지 않는다
            if (device.isSignedOut()) {
                switch (action) {
                    case "/messages" -> {
                        writeJson(resp, HttpServletResponse.SC_OK, signedOutBody(req));
                        return;
                    }
                    case "/whoami" -> {
                        writeJson(resp, HttpServletResponse.SC_OK, Map.of("account", userId, "signedOut", true));
                        return;
                    }
                    case "/note", "/note-save", "/read" -> {
                        writeJson(resp, HttpServletResponse.SC_FORBIDDEN,
                                Map.of("message", "사이트에 로그인하면 다시 쓸 수 있어요."));
                        return;
                    }
                    default -> {
                        // 업데이트 확인·내려받기·연결 해제는 로그아웃 중에도 된다
                    }
                }
            }
            switch (action) {
                case "/messages" -> writeJson(resp, HttpServletResponse.SC_OK,
                        snapshotBody(req, userId, snapshots.load(userId, parseHour(req.getParameter("eveningHour")))));
                case "/whoami" -> {
                    // 2초마다 묻는 가벼운 확인 — 이 PC 브라우저에서 계정을 바꾸거나 로그아웃했는지만 (할 말은 /messages)
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("account", userId);
                    body.put("signedOut", false);
                    writeJson(resp, HttpServletResponse.SC_OK, body);
                }
                case "/latest" -> {
                    CompanionReleaseDto r = releaseService.latest();
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (r != null) {
                        body.put("version", r.getVersion());
                        body.put("notes", r.getNotes());
                        body.put("sha256", r.getSha256());
                        body.put("size", r.getFileSize());
                    }
                    writeJson(resp, HttpServletResponse.SC_OK, body);
                }
                case "/download" -> {
                    CompanionReleaseDto r = releaseService.latest();
                    if (r == null) {
                        writeJson(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("message", "아직 올라간 설치 파일이 없어요."));
                        return;
                    }
                    resp.setContentType("application/octet-stream");
                    resp.setContentLengthLong(r.getFileSize());
                    resp.setHeader("Content-Disposition", "attachment; filename=\"" + r.getFileName() + "\"");
                    releaseService.writeTo(r, resp.getOutputStream());
                }
                case "/note" -> {
                    NoteService.Note note = noteService.loadWithVersion(userId);
                    writeJson(resp, HttpServletResponse.SC_OK, Map.of("text", note.text(), "version", note.version()));
                }
                case "/note-save" -> {
                    NoteService.Note current = noteService.loadWithVersion(userId);
                    String base = req.getParameter("version");
                    if (!"true".equals(req.getParameter("force")) && base != null && !base.equals(current.version())) {
                        writeJson(resp, HttpServletResponse.SC_CONFLICT,
                                Map.of("message", "웹에서 바뀐 내용이 있어요.", "text", current.text(), "version", current.version()));
                        return;
                    }
                    try {
                        noteService.save(userId, req.getParameter("text"));
                    } catch (java.io.IOException e) {
                        throw new SQLException("연습장을 저장하지 못했어요", e);
                    } catch (IllegalArgumentException e) {
                        writeJson(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("message", e.getMessage()));
                        return;
                    }
                    writeJson(resp, HttpServletResponse.SC_OK, Map.of("version", noteService.loadWithVersion(userId).version()));
                }
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

    /**
     * 로그아웃해 쉬는 중의 /messages. 새 캐릭터(0.1.1~)는 signedOut만 보고 쉬는 모습으로 바뀐다.
     * 예전 캐릭터(0.1.0)는 signedOut을 모르므로, 같은 안내를 말 하나로도 넣어 준다 — 안 그러면 할 말이 비어
     * "지금은 급한 일이 없어요"라고 답한다.
     */
    private static Map<String, Object> signedOutBody(HttpServletRequest req) {
        String loginUrl = baseUrl(req) + "/login";
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("key", "signed-out");
        notice.put("kind", CompanionMessageService.WARN);
        notice.put("label", "쉬는 중");
        notice.put("text", "사이트에서 로그아웃해서 쉬고 있어요. 이 PC에서 다시 로그인하면 그 계정으로 저절로 이어져요.");
        notice.put("linkText", "로그인하기");
        notice.put("url", loginUrl);
        notice.put("notificationId", null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signedOut", true);
        body.put("siteUrl", loginUrl);
        body.put("messages", List.of(notice));
        body.put("trends", List.of());
        return body;
    }

    private static Map<String, Object> snapshotBody(HttpServletRequest req, Long userId, CompanionMessageService.Snapshot s) {
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
        body.put("account", userId); // 캐릭터가 다른 계정으로 옮겨 갔는지 — 바뀌면 지난 계정의 말풍선 기록을 지운다
        body.put("userName", s.userName());
        body.put("tier", s.tier());
        body.put("messages", messages);
        body.put("siteUrl", base + "/dashboard");
        body.put("summary", s.summary());
        body.put("trends", s.trends());
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

    private static int parseHour(String raw) {
        try {
            return raw == null ? CompanionMessageService.EVENING_HOUR_DEFAULT : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return CompanionMessageService.EVENING_HOUR_DEFAULT;
        }
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
