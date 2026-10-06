package com.specodyssey.controller;

import com.specodyssey.util.AiNotices;
import com.specodyssey.util.AiNotices.Notice;
import com.specodyssey.util.AiNotices.RetryTarget;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 요청 중 모인 AI 대체 안내를 세션으로 옮기는 AiNoticeFilter (FR-111). 서블릿 컨테이너 없이 가짜 요청·세션으로 본다. */
class AiNoticeFilterTest {

    private final AiNoticeFilter filter = new AiNoticeFilter();

    @SuppressWarnings("unchecked")
    private static List<String> sessionMessages(Map<String, Object> session) {
        return ((List<Notice>) session.get(AiNotices.SESSION_KEY)).stream().map(Notice::getMessage).toList();
    }

    @Test
    void 요청_중_남긴_안내가_세션으로_옮겨진다() throws Exception {
        Map<String, Object> session = new HashMap<>();
        AiNotices.add("이전 요청에서 새어 나온 안내"); // 요청 시작 때 비워져야 한다

        filter.doFilter(request(session), null, (req, resp) -> AiNotices.add("AI 응답을 받지 못했습니다."));

        assertEquals(List.of("AI 응답을 받지 못했습니다."), sessionMessages(session));
        assertTrue(AiNotices.drain().isEmpty(), "요청이 끝나면 스레드에 남지 않아야 한다");
    }

    @Test
    void 안내가_없으면_세션을_건드리지_않는다() throws Exception {
        Map<String, Object> session = new HashMap<>();

        filter.doFilter(request(session), null, (req, resp) -> { });

        assertNull(session.get(AiNotices.SESSION_KEY));
    }

    @Test
    void 아직_못_보여준_안내_뒤에_덧붙이고_같은_문구는_한_번만() throws Exception {
        Map<String, Object> session = new HashMap<>();
        session.put(AiNotices.SESSION_KEY, List.of(new Notice("먼저 안내", null)));

        filter.doFilter(request(session), null, (req, resp) -> {
            AiNotices.add("먼저 안내");
            AiNotices.add("새 안내");
        });

        assertEquals(List.of("먼저 안내", "새 안내"), sessionMessages(session));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 다시_시도_대상도_세션까지_옮겨진다() throws Exception {
        Map<String, Object> session = new HashMap<>();

        filter.doFilter(request(session), null, (req, resp) -> AiNotices.add("다시 시도 가능", RetryTarget.DISCOVERY));

        Notice notice = ((List<Notice>) session.get(AiNotices.SESSION_KEY)).get(0);
        assertTrue(notice.isRetryable());
        assertEquals("DISCOVERY", notice.getRetryTarget());
    }

    @Test
    void 처리_중_예외가_나도_스레드를_비운다() {
        Map<String, Object> session = new HashMap<>();
        FilterChain failing = (req, resp) -> {
            AiNotices.add("실패 전 안내");
            throw new IllegalStateException("처리 실패");
        };

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request(session), null, failing));
        assertTrue(AiNotices.drain().isEmpty());
        assertEquals(List.of("실패 전 안내"), sessionMessages(session));
    }

    @Test
    void 세션이_없으면_안내를_버린다() throws Exception {
        filter.doFilter(request(null), null, (req, resp) -> AiNotices.add("로그인 전 안내"));

        assertTrue(AiNotices.drain().isEmpty());
    }

    // 2026-10-06 재시도 버튼 — 연타 방지 간격
    @Test
    void 다시_시도는_짧은_간격_안에_다시_누르면_막는다() {
        HttpSession session = session(new HashMap<>());

        assertTrue(AiRetryServlet.allowNow(session, 1_000_000));
        assertFalse(AiRetryServlet.allowNow(session, 1_000_000 + AiRetryServlet.MIN_INTERVAL_MS - 1));
        assertTrue(AiRetryServlet.allowNow(session, 1_000_000 + AiRetryServlet.MIN_INTERVAL_MS));
    }

    @Test
    void 다시_시도_대상은_정해진_값만_받는다() {
        assertEquals(RetryTarget.DISCOVERY, AiRetryServlet.parseTarget("DISCOVERY"));
        assertEquals(RetryTarget.ROADMAP_PROJECT, AiRetryServlet.parseTarget("ROADMAP_PROJECT"));
        assertNull(AiRetryServlet.parseTarget("discovery"));
        assertNull(AiRetryServlet.parseTarget("../admin"));
        assertNull(AiRetryServlet.parseTarget(null));
    }

    // getAttribute·setAttribute만 쓰는 가짜 세션
    private static HttpSession session(Map<String, Object> attributes) {
        return (HttpSession) Proxy.newProxyInstance(
                AiNoticeFilterTest.class.getClassLoader(), new Class<?>[]{HttpSession.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> attributes.get((String) args[0]);
                    case "setAttribute" -> attributes.put((String) args[0], args[1]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    // getSession(false)만 쓰는 가짜 요청 (map이 null이면 세션 없음)
    private static HttpServletRequest request(Map<String, Object> attributes) {
        HttpSession session = attributes == null ? null : session(attributes);
        return (HttpServletRequest) Proxy.newProxyInstance(
                AiNoticeFilterTest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    if ("getSession".equals(method.getName())) {
                        return session;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
