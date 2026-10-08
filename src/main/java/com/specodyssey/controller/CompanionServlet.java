package com.specodyssey.controller;

import com.google.gson.Gson;
import com.specodyssey.dto.CompanionDeviceDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.CompanionReleaseDto;
import com.specodyssey.service.companion.CompanionAuthService;
import com.specodyssey.service.companion.CompanionReleaseService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 웹 쪽 데스크톱 캐릭터 연결 — "오셍이들" 화면(/bot, bot.jsp, js/companion.js)이 부른다.
 *   POST /companion/connect  일회용 코드를 만들어 캐릭터를 켜는 specodyssey:// 주소를 돌려준다
 *   GET  /companion/devices  연결된 PC 목록 (이 브라우저의 PC는 thisPc). 방금 연결이 끝났으면 이 브라우저에
 *                            "이 PC의 캐릭터" 쿠키를 남긴다 — 이후 이 브라우저에서 로그인·로그아웃하면
 *                            캐릭터가 따라간다 (CompanionLinkFilter)
 *   POST /companion/revoke   id → 그 PC 연결 해제
 *   GET  /companion/download 최신 설치 파일(Setup.exe) 내려받기 — 관리자가 올린 것을 DB에서 이어 보낸다
 * 로그인 세션이 필요하고(SessionFilter), POST는 CSRF 헤더를 확인한다(SecurityHeadersFilter). 항상 세션의 본인 것만.
 */
@WebServlet("/companion/*")
public class CompanionServlet extends HttpServlet {


    /**
     * 이 브라우저(세션)가 설치 파일을 받아갔다는 표시. 메뉴의 [캐릭터 내려받기]를 [캐릭터 켜기]로
     * 바꿔 주는 스위치다 (CompanionNavFilter → common/companion-nav.jspf).
     * 브라우저는 PC에 설치가 끝났는지 알려 주지 않으니, "설치 파일을 받아갔다"가 우리가 아는 가장
     * 가까운 사실이다. 세션이라 다시 로그인하면 지워지고, 그걸 보정하는 것은 브라우저 localStorage다
     * (js/companion-nav.js).
     */
    static final String DOWNLOADED_ATTR = "companionDownloaded";

    /** 이 브라우저와 PC 캐릭터를 잇는 쿠키 — 값은 CompanionAuthService.browserLink (서명된 행 id) */
    static final String LINK_COOKIE = "so_companion_pc";
    /** 이 세션이 "캐릭터 연결"로 만든 행 id와 시각 — 캐릭터가 코드를 교환하면 그 행에 대한 쿠키를 남긴다 */
    static final String PENDING_ATTR = "companionPendingDevice";
    static final String PENDING_AT_ATTR = "companionPendingAt";
    private static final String APPLIED_ATTR = "companionLinkApplied";
    /** 연결을 기다리는 시간 — 처음 설치하면 캐릭터가 뜨기까지 오래 걸릴 수 있어 넉넉히 */
    private static final long PENDING_MS = 10 * 60_000L;
    private static final int LINK_COOKIE_DAYS = 365;

    private static final Logger LOG = Logger.getLogger(CompanionServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final CompanionAuthService authService = new CompanionAuthService();
    private final CompanionReleaseService releaseService = new CompanionReleaseService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long userId = currentUserId(req);
        if ("/download".equals(req.getPathInfo())) {
            download(req, resp);
            return;
        }
        if (!"/devices".equals(req.getPathInfo())) {
            writeJson(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("message", "없는 요청입니다."));
            return;
        }
        try {
            String link = applyPendingLink(req, resp, authService);
            CompanionDeviceDto thisPc = authService.deviceForBrowserLink(link != null ? link : linkCookie(req));
            List<Map<String, Object>> list = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now(ZONE);
            for (CompanionDeviceDto d : authService.connectedDevices(userId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", d.getId());
                item.put("name", d.getDeviceName() == null ? "이름 없는 PC" : d.getDeviceName());
                item.put("lastUsed", ago(d.getLastUsedAt(), now));
                item.put("thisPc", thisPc != null && thisPc.getId().equals(d.getId()));
                list.add(item);
            }
            writeJson(resp, HttpServletResponse.SC_OK, Map.of("devices", list));
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "캐릭터 연결 목록 조회 실패", e);
            writeJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Map.of("message", "목록을 불러오지 못했어요."));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long userId = currentUserId(req);
        String action = req.getPathInfo();
        try {
            if ("/connect".equals(action)) {
                CompanionAuthService.Issued issued = authService.issue(userId);
                String code = issued.code();
                HttpSession session = req.getSession(false);
                if (session != null) {
                    session.setAttribute(PENDING_ATTR, issued.deviceId());
                    session.setAttribute(PENDING_AT_ATTR, System.currentTimeMillis());
                }
                String launchUrl = "specodyssey://connect?code=" + code
                        + "&server=" + URLEncoder.encode(CompanionApiServlet.baseUrl(req), StandardCharsets.UTF_8);
                CompanionReleaseDto latest = releaseService.latest();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("launchUrl", launchUrl);
                body.put("downloadUrl", latest == null ? null : req.getContextPath() + "/companion/download");
                body.put("version", latest == null ? null : latest.getVersion());
                body.put("sizeText", latest == null ? null : latest.getSizeText());
                writeJson(resp, HttpServletResponse.SC_OK, body);
            } else if ("/revoke".equals(action)) {
                boolean done = authService.revoke(userId, parseId(req.getParameter("id")));
                writeJson(resp, done ? HttpServletResponse.SC_OK : HttpServletResponse.SC_NOT_FOUND,
                        Map.of("ok", done));
            } else {
                writeJson(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("message", "없는 요청입니다."));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "캐릭터 연결 처리 실패 (" + action + ")", e);
            writeJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Map.of("message", "잠시 후 다시 시도해 주세요."));
        }
    }

    private void download(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            CompanionReleaseDto r = releaseService.latest();
            if (r == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "아직 올라간 설치 파일이 없어요.");
                return;
            }
            resp.setContentType("application/octet-stream");
            resp.setContentLengthLong(r.getFileSize());
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + r.getFileName() + "\"");
            releaseService.writeTo(r, resp.getOutputStream());
            // 파일을 다 보냈으니 메뉴를 [캐릭터 켜기]로 바꾼다. 보내기 전에 표시하면 중간에 끊겨도
            // 켜기로 바뀌어, 사용자가 설치되지 않은 프로그램을 켜려고 하게 된다.
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.setAttribute(DOWNLOADED_ATTR, Boolean.TRUE);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "캐릭터 설치 파일 내려받기 실패", e);
            if (!resp.isCommitted()) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요.");
            }
        }
    }

    /**
     * 이 세션이 "캐릭터 연결"을 눌렀고 캐릭터가 그 코드로 연결을 마쳤으면 "이 PC의 캐릭터" 쿠키를 남긴다.
     * 연결 목록(/companion/devices)과 다음 화면 이동(CompanionNavFilter) 때 부른다. 기다리는 게 없으면 DB를 보지 않는다.
     * @return 방금 남긴 쿠키 값 (안 남겼으면 null)
     */
    static String applyPendingLink(HttpServletRequest req, HttpServletResponse resp, CompanionAuthService authService)
            throws SQLException {
        // 같은 요청에서 앞서(필터에서) 이미 남겼으면 그 값 — 요청의 쿠키는 아직 예전 값이라 여기서 알려 줘야 한다
        if (req.getAttribute(APPLIED_ATTR) instanceof String applied) {
            return applied;
        }
        HttpSession session = req.getSession(false);
        Object pending = session == null ? null : session.getAttribute(PENDING_ATTR);
        if (!(pending instanceof Long deviceId)) {
            return null;
        }
        Object at = session.getAttribute(PENDING_AT_ATTR);
        if (!(at instanceof Long started) || System.currentTimeMillis() - started > PENDING_MS) {
            clearPending(session); // 코드가 만료돼 더 기다려도 연결되지 않는다
            return null;
        }
        String link = authService.browserLink(deviceId);
        if (link == null) {
            return null; // 캐릭터가 아직 코드를 안 바꿨다 — 다음에 다시 본다
        }
        clearPending(session);
        req.setAttribute(APPLIED_ATTR, link);
        if (!resp.isCommitted()) {
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(LINK_COOKIE, link);
            cookie.setHttpOnly(true);
            cookie.setSecure(req.isSecure());
            cookie.setPath(req.getContextPath().isEmpty() ? "/" : req.getContextPath());
            cookie.setMaxAge(LINK_COOKIE_DAYS * 24 * 60 * 60);
            cookie.setAttribute("SameSite", "Lax");
            resp.addCookie(cookie);
        }
        return link;
    }

    static String linkCookie(HttpServletRequest req) {
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie c : cookies) {
            if (LINK_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private static void clearPending(HttpSession session) {
        session.removeAttribute(PENDING_ATTR);
        session.removeAttribute(PENDING_AT_ATTR);
    }

    static String ago(LocalDateTime at, LocalDateTime now) {
        if (at == null) {
            return "-";
        }
        long minutes = Duration.between(at, now).toMinutes();
        if (minutes < 1) {
            return "방금";
        }
        if (minutes < 60) {
            return minutes + "분 전";
        }
        if (minutes < 60 * 24) {
            return (minutes / 60) + "시간 전";
        }
        return at.format(DATE);
    }

    private static Long parseId(String raw) {
        try {
            return raw == null ? -1L : Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto user = session == null ? null : (UserDto) session.getAttribute("loginUser");
        return user == null ? null : user.getId();
    }

    private static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(GSON.toJson(body));
    }
}
