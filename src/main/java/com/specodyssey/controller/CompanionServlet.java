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
 * 웹 쪽 데스크톱 캐릭터 연결 — 내 프로필 "데스크톱 캐릭터" 칸(profile/_companion.jspf, js/companion.js)이 부른다.
 *   POST /companion/connect  일회용 코드를 만들어 캐릭터를 켜는 specodyssey:// 주소를 돌려준다
 *   GET  /companion/devices  연결된 PC 목록
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
            List<Map<String, Object>> list = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now(ZONE);
            for (CompanionDeviceDto d : authService.connectedDevices(userId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", d.getId());
                item.put("name", d.getDeviceName() == null ? "이름 없는 PC" : d.getDeviceName());
                item.put("lastUsed", ago(d.getLastUsedAt(), now));
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
                String code = authService.issueCode(userId);
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
