package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;

/**
 * 알림 목록과 읽음 처리.
 * GET  /notifications                  전체 알림(최근 50개)
 * POST /notifications action=open&id=  읽음으로 바꾸고 알림이 가리키는 화면으로 이동 (헤더 드롭다운·목록에서 누를 때)
 * POST /notifications action=readAll   모두 읽음 — 헤더 드롭다운(fetch)은 204로 그 자리에서, 폼은 보던 화면으로 돌아간다
 * 세션의 본인 알림만 다룬다 — 남의 알림 id를 보내면 목록으로 돌아갈 뿐 아무것도 바뀌지 않는다.
 */
@WebServlet("/notifications")
public class NotificationServlet extends HttpServlet {

    private static final int PAGE_LIMIT = 50;
    static final String FETCH_HEADER = "X-Requested-With";
    static final String FETCH_HEADER_VALUE = "fetch";

    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.setAttribute("notifications", notificationService.findRecent(loginUserId(req), PAGE_LIMIT));
        } catch (SQLException e) {
            throw new ServletException("알림을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/notifications.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = loginUserId(req);
        try {
            if ("readAll".equals(req.getParameter("action"))) {
                notificationService.markAllRead(userId);
                // 헤더 드롭다운은 fetch로 보내고 그 자리에서 화면만 고친다 — 이동하지 않는다
                if (FETCH_HEADER_VALUE.equals(req.getHeader(FETCH_HEADER))) {
                    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    return;
                }
                // 스크립트 없이 폼으로 보낸 경우에도 보던 화면으로 돌아간다
                resp.sendRedirect(req.getContextPath() + backPath(req));
                return;
            }
            Long id = parseId(req.getParameter("id"));
            String target = id == null ? "/notifications" : notificationService.open(id, userId);
            resp.sendRedirect(req.getContextPath() + target);
        } catch (SQLException e) {
            throw new ServletException("알림을 처리하는 중 오류가 발생했습니다.", e);
        }
    }

    /** 보던 화면(Referer)의 앱 안 경로. 같은 서버·같은 앱의 경로가 아니면 알림 목록. */
    private static String backPath(HttpServletRequest req) {
        return backPath(req.getHeader("Referer"), req.getServerName(), req.getContextPath());
    }

    static String backPath(String referer, String serverName, String ctx) {
        if (referer == null) {
            return NotificationService.FALLBACK_LINK;
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if ((uri.getHost() != null && !uri.getHost().equalsIgnoreCase(serverName))
                    || path == null || !path.startsWith(ctx + "/")) {
                return NotificationService.FALLBACK_LINK;
            }
            String inApp = path.substring(ctx.length()) + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            return NotificationService.safeLink(inApp);
        } catch (IllegalArgumentException e) {
            return NotificationService.FALLBACK_LINK;
        }
    }

    private static Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long loginUserId(HttpServletRequest req) {
        return ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
    }
}
