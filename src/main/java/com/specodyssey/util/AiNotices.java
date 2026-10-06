package com.specodyssey.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 요청 하나 동안 "AI 응답을 받지 못해 대체했다"는 안내를 모은다. 관련 요구사항: FR-111
 *
 * AI 호출은 서블릿 → 서비스 → 생성기처럼 깊은 곳에서 일어나서, 실패 여부를 반환값으로 화면까지 올리려면
 * 여러 메서드 시그니처를 바꿔야 한다. 대신 호출한 곳에서 add()로 남기면 AiNoticeFilter가 요청이 끝날 때
 * 세션에 옮기고, 다음 화면의 header.jsp가 한 번 보여주고 지운다 (2026-10-02).
 *
 * 요청 스레드는 재사용되므로 AiNoticeFilter가 요청 시작·끝에 반드시 비운다. 요청 밖(스케줄러·테스트)에서
 * 쌓인 안내는 화면으로 가지 않으며, 계속 쌓이지 않게 개수를 제한한다.
 */
public final class AiNotices {

    /** header.jsp가 읽는 세션 속성 이름 (값은 List&lt;String&gt;) */
    public static final String SESSION_KEY = "aiNotice";
    static final int MAX_NOTICES = 5;

    private static final ThreadLocal<Set<String>> NOTICES = ThreadLocal.withInitial(LinkedHashSet::new);

    private AiNotices() {
    }

    /** 같은 문구는 한 번만 남는다 */
    public static void add(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Set<String> notices = NOTICES.get();
        if (notices.size() < MAX_NOTICES) {
            notices.add(message);
        }
    }

    /** 모인 안내를 꺼내고 비운다 (넣은 순서대로) */
    public static List<String> drain() {
        List<String> notices = new ArrayList<>(NOTICES.get());
        NOTICES.remove();
        return notices;
    }

    public static void clear() {
        NOTICES.remove();
    }
}
