package com.specodyssey.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 요청 하나 동안 "AI 응답을 받지 못해 대체했다"는 안내를 모은다. 관련 요구사항: FR-111
 *
 * AI 호출은 서블릿 → 서비스 → 생성기처럼 깊은 곳에서 일어나서, 실패 여부를 반환값으로 화면까지 올리려면
 * 여러 메서드 시그니처를 바꿔야 한다. 대신 호출한 곳에서 add()로 남기면 AiNoticeFilter가 요청이 끝날 때
 * 세션에 옮기고, 다음 화면의 header.jsp가 한 번 보여주고 지운다 (2026-10-02).
 * 시간이 지나면 풀리는 실패(429·5xx·타임아웃)면 다시 시도할 대상(RetryTarget)을 함께 남겨
 * 배너에 "다시 시도" 버튼이 뜨게 한다 — 버튼은 AiRetryServlet으로 간다 (2026-10-06).
 *
 * 요청 스레드는 재사용되므로 AiNoticeFilter가 요청 시작·끝에 반드시 비운다. 요청 밖(스케줄러·테스트)에서
 * 쌓인 안내는 화면으로 가지 않으며, 계속 쌓이지 않게 개수를 제한한다.
 */
public final class AiNotices {

    /** header.jsp가 읽는 세션 속성 이름 (값은 List&lt;Notice&gt;) */
    public static final String SESSION_KEY = "aiNotice";
    static final int MAX_NOTICES = 5;

    /** "다시 시도"가 다시 부를 기능. AiRetryServlet의 target 파라미터 값이다. */
    public enum RetryTarget { DISCOVERY, ROADMAP_PROJECT }

    /** 안내 한 줄. 세션에 담기므로 Serializable, JSP EL이 getter로 읽는다. */
    public static final class Notice implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String message;
        private final RetryTarget retryTarget;

        public Notice(String message, RetryTarget retryTarget) {
            this.message = message;
            this.retryTarget = retryTarget;
        }

        public String getMessage() {
            return message;
        }

        /** 다시 시도할 대상 이름. 없으면 null — 버튼을 띄우지 않는다 */
        public String getRetryTarget() {
            return retryTarget == null ? null : retryTarget.name();
        }

        public boolean isRetryable() {
            return retryTarget != null;
        }

        @Override
        public String toString() {
            return retryTarget == null ? message : message + " [" + retryTarget + "]";
        }
    }

    private static final ThreadLocal<Map<String, Notice>> NOTICES = ThreadLocal.withInitial(LinkedHashMap::new);

    private AiNotices() {
    }

    public static void add(String message) {
        add(message, null);
    }

    /** 같은 문구는 한 번만 남는다. 같은 문구가 다시 시도 대상과 함께 오면 대상을 채운다. */
    public static void add(String message, RetryTarget retryTarget) {
        if (message == null || message.isBlank()) {
            return;
        }
        merge(NOTICES.get(), new Notice(message, retryTarget));
    }

    /** 모인 안내를 꺼내고 비운다 (넣은 순서대로) */
    public static List<Notice> drain() {
        List<Notice> notices = new ArrayList<>(NOTICES.get().values());
        NOTICES.remove();
        return notices;
    }

    public static void clear() {
        NOTICES.remove();
    }

    /** 아직 못 보여준 안내 뒤에 새 안내를 덧붙인다 — 같은 문구는 한 번만 (AiNoticeFilter가 세션에 담을 때) */
    public static List<Notice> merge(List<?> pending, List<Notice> added) {
        Map<String, Notice> merged = new LinkedHashMap<>();
        if (pending != null) {
            for (Object item : pending) {
                if (item instanceof Notice notice) {
                    merge(merged, notice);
                }
            }
        }
        added.forEach(notice -> merge(merged, notice));
        return new ArrayList<>(merged.values());
    }

    private static void merge(Map<String, Notice> notices, Notice notice) {
        Notice existing = notices.get(notice.getMessage());
        if (existing == null) {
            if (notices.size() < MAX_NOTICES) {
                notices.put(notice.getMessage(), notice);
            }
        } else if (!existing.isRetryable() && notice.isRetryable()) {
            notices.put(notice.getMessage(), notice);
        }
    }
}
