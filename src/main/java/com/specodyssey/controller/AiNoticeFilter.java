package com.specodyssey.controller;

import com.specodyssey.util.AiNotices;
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

/**
 * 요청 중에 모인 AI 대체 안내(AiNotices)를 세션으로 옮긴다 — 다음 화면의 header.jsp가 한 번 보여주고 지운다 (FR-111, 2026-10-02).
 * 설문 제출·로드맵 생성은 POST 처리 후 redirect하므로 안내는 redirect된 다음 화면에 뜬다.
 * (Tomcat은 sendRedirect 응답을 필터 체인이 끝난 뒤에 보내므로, 브라우저가 다음 화면을 요청할 때는 이미 세션에 들어 있다)
 * 요청 스레드가 재사용되므로 요청 시작과 끝에 반드시 비운다.
 */
@WebFilter(urlPatterns = {"/*"})
public class AiNoticeFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        AiNotices.clear();
        try {
            chain.doFilter(request, response);
        } finally {
            List<AiNotices.Notice> notices = AiNotices.drain();
            if (!notices.isEmpty() && request instanceof HttpServletRequest req) {
                moveToSession(req.getSession(false), notices);
            }
        }
    }

    // 아직 보여주지 않은 안내가 있으면 뒤에 덧붙인다 (같은 문구는 한 번만)
    static void moveToSession(HttpSession session, List<AiNotices.Notice> notices) {
        if (session == null) {
            return;
        }
        List<?> pending = session.getAttribute(AiNotices.SESSION_KEY) instanceof List<?> list ? list : null;
        session.setAttribute(AiNotices.SESSION_KEY, AiNotices.merge(pending, notices));
    }
}
