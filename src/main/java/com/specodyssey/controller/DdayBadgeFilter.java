package com.specodyssey.controller;

import com.specodyssey.dto.DdayItemDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.DdayService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 사이드 메뉴 "D-day 알림" 옆 배지(common/header.jsp)에 쓸, 가장 가까운 일정의 D-day를 요청에 실어 준다.
 * 관련 요구사항: FR-72 마감 임박 강조
 *
 * 메뉴는 로그인한 모든 화면에 나오므로 화면마다 서블릿을 고치는 대신 필터 한 곳에서 처리한다.
 * 항상 세션의 본인 일정만 조회한다. 다가오는 일정이 없거나 조회에 실패하면 배지를 띄우지 않는다.
 */
@WebFilter(urlPatterns = {"/*"})
public class DdayBadgeFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(DdayBadgeFilter.class.getName());

    // 메뉴가 없는 정적 리소스 요청에서는 DB를 조회하지 않는다
    private static final String[] STATIC_PREFIXES = {"/css/", "/js/", "/img/", "/image/"};

    private final DdayService ddayService = new DdayService();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (loginUser instanceof UserDto user && !isStatic(req.getServletPath())) {
            try {
                DdayItemDto nearest = ddayService.findNearest(user.getId(), LocalDate.now());
                if (nearest != null) {
                    req.setAttribute("navDdayText", nearest.getDdayText());
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "D-day 배지 조회 실패 — 배지 없이 표시합니다", e);
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
