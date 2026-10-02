package com.specodyssey.util;

import java.net.URI;

/**
 * 사용자가 입력한 웹 주소 검사 — http/https만 허용한다. javascript:, data:, file: 같은 주소가 링크로 저장돼
 * 면접관이 누르는 순간 실행되는 일을 막는다. 저장할 때와 화면에 내보낼 때 둘 다 이 규칙을 쓴다.
 */
public final class UrlRules {

    public static final int MAX_URL_LENGTH = 500;

    private UrlRules() {
    }

    /** http/https이고 호스트가 있는 주소인지(길이 제한 포함). null·빈 값은 false. */
    public static boolean isWebUrl(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return web && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 값이 있으면 웹 주소여야 한다 — null은 "입력 안 함"이라 통과. 아니면 사용자에게 그대로 보여줄 메시지로 거절한다.
     * @param label 메시지에 쓸 이름(예: "코드 저장소 링크")
     */
    public static void requireWebUrlIfPresent(String value, String label) {
        if (value == null) {
            return;
        }
        if (value.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(label + "는 " + MAX_URL_LENGTH + "자 이내로 입력해주세요.");
        }
        if (!isWebUrl(value)) {
            throw new IllegalArgumentException(label + "는 http:// 또는 https://로 시작하는 올바른 주소여야 합니다.");
        }
    }
}
