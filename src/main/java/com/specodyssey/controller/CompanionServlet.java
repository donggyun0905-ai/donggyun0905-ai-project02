package com.specodyssey.controller;

import com.google.gson.Gson;
import com.specodyssey.dto.CompanionDeviceDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.companion.CompanionAuthService;
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
 * 로그인 세션이 필요하고(SessionFilter), POST는 CSRF 헤더를 확인한다(SecurityHeadersFilter). 항상 세션의 본인 것만.
 */
@WebServlet("/companion/*")
public class CompanionServlet extends HttpServlet {

    /** 설치 파일 — GitHub Releases의 최신 버전 (업데이트도 같은 곳을 본다) */
    public static final String DOWNLOAD_URL =
            "https://github.com/donggyun0905-ai/donggyun0905-ai-project02/releases/latest/download/SpecOdysseyCompanion.zip";

    private static final Logger LOG = Logger.getLogger(CompanionServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final CompanionAuthService authService = new CompanionAuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long userId = currentUserId(req);
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
                writeJson(resp, HttpServletResponse.SC_OK, Map.of("launchUrl", launchUrl, "downloadUrl", DOWNLOAD_URL));
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
