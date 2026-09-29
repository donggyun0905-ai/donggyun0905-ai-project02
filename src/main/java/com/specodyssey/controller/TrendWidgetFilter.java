package com.specodyssey.controller;

import com.specodyssey.dao.TrendCollectDao;
import com.specodyssey.dto.TrendTechDto;
import com.specodyssey.dto.UserDto;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 사이드바 "오늘의 트렌드 기술" 위젯(common/trend-widget.jsp)에 쓸 데이터를 요청에 실어 준다.
 * 관련 요구사항: FR-54 트렌드 기술 노출, FR-55 직무 연관 필터링
 *
 * 위젯이 들어가는 화면마다 서블릿을 고치는 대신 필터 한 곳에서 처리한다.
 * 항상 세션의 본인 목표 직무로만 조회한다(다른 사용자 id를 파라미터로 받지 않는다).
 * 조회에 실패해도 화면 전체를 깨뜨리지 않고 빈 목록으로 둔다.
 * /roadmap은 seongwon 브랜치 원본엔 없었지만, 로드맵 화면에도 위젯을 넣기로 해서 추가했다
 * (사용자 요청, 2026-09-29).
 */
@WebFilter(urlPatterns = {
        "/dashboard", "/dday", "/documents", "/gap-analysis", "/insights",
        "/job-discovery", "/mission", "/resume-feedback", "/share-links", "/roadmap"
})
public class TrendWidgetFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(TrendWidgetFilter.class.getName());
    private static final int WIDGET_SIZE = 3;

    private final TrendCollectDao trendDao = new TrendCollectDao();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (loginUser instanceof UserDto user && user.getDesiredJobId() != null) {
            try {
                List<TrendTechDto> trends = trendDao.findTopByJobId(user.getDesiredJobId(), WIDGET_SIZE);
                req.setAttribute("trendTechs", trends);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "트렌드 위젯 조회 실패 — 빈 목록으로 표시합니다", e);
            }
        }
        chain.doFilter(request, response);
    }
}
