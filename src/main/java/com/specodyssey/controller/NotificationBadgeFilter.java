package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.NotificationService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 헤더 알림 버튼(common/notification-bell.jsp)에 쓸 안 읽은 개수와 안 읽은 최근 알림을 요청에 실어 준다.
 * 읽은 알림은 드롭다운에 넣지 않는다 — 지난 알림은 전체 알림 화면(/notifications)에서 회색으로 보인다.
 * 헤더는 로그인한 모든 화면에 나오므로 DdayBadgeFilter처럼 필터 한 곳에서 처리한다.
 * 항상 세션의 본인 알림만 조회한다. 면접관 계정은 받을 알림이 없어 조회하지 않는다.
 * 조회에 실패하면 알림 없이 화면을 그대로 보여준다.
 */
@WebFilter(urlPatterns = {"/*"})
public class NotificationBadgeFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(NotificationBadgeFilter.class.getName());
    private static final int RECENT_LIMIT = 8;

    // 헤더가 없는 정적 리소스 요청에서는 DB를 조회하지 않는다
    private static final String[] STATIC_PREFIXES = {"/css/", "/js/", "/img/", "/image/"};

    private final NotificationService notificationService = new NotificationService();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (loginUser instanceof UserDto user && !RoleFilter.INTERVIEWER.equals(user.getUserType())
                && !isStatic(req.getServletPath())) {
            try {
                req.setAttribute("notiUnreadCount", notificationService.countUnread(user.getId()));
                req.setAttribute("notiRecent", notificationService.findRecentUnread(user.getId(), RECENT_LIMIT));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "알림 조회 실패 — 알림 없이 표시합니다", e);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isStatic(String path) {
        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
