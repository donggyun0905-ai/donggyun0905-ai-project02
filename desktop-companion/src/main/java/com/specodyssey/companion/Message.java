package com.specodyssey.companion;

/**
 * 말풍선 하나. 서버(/api/companion/messages)가 준 것과 캐릭터가 스스로 만드는 것(인사·칭찬·업데이트·연결 안내)이 있다.
 * key가 같으면 같은 말 — 한 번 한 말은 상황이 바뀌기 전까지 다시 하지 않는다.
 */
public record Message(String key, String kind, String label, String text, String linkText, String url,
                      Long notificationId, boolean local) {

    // 서버가 주는 종류
    public static final String WARN = "WARN";
    public static final String NOTICE = "NOTICE";
    public static final String TODO = "TODO";
    // 캐릭터가 만드는 종류
    public static final String PRAISE = "PRAISE";
    public static final String INFO = "INFO";

    public static Message local(String key, String kind, String label, String text, String linkText, String url) {
        return new Message(key, kind, label, text, linkText, url, null, true);
    }
}
