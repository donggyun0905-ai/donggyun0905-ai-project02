package com.specodyssey.companion;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 실행할 때 받은 값 — 웹 "캐릭터 연결"이 연 specodyssey://connect?code=…&server=… 와 업데이트 뒤의 --updated.
 */
public record LaunchArgs(String code, String server, boolean updated) {

    public static final String SCHEME = "specodyssey";

    public static LaunchArgs parse(String[] args) {
        String code = null;
        String server = null;
        boolean updated = false;
        for (String a : args) {
            if (a == null) {
                continue;
            }
            if ("--updated".equals(a)) {
                updated = true;
            } else if (a.toLowerCase().startsWith(SCHEME + ":")) {
                try {
                    String query = URI.create(a.trim()).getRawQuery();
                    if (query != null) {
                        for (String pair : query.split("&")) {
                            int eq = pair.indexOf('=');
                            if (eq <= 0) {
                                continue;
                            }
                            String k = pair.substring(0, eq);
                            String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                            if ("code".equals(k)) {
                                code = v;
                            } else if ("server".equals(k)) {
                                server = v;
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // 잘못된 주소는 무시하고 그냥 켠다
                }
            }
        }
        if (server != null && !(server.startsWith("http://") || server.startsWith("https://"))) {
            server = null; // 웹 주소만 받는다
        }
        return new LaunchArgs(code, server, updated);
    }

    /** 이미 켜진 캐릭터에게 넘길 때 한 줄로 */
    public String toLine() {
        StringBuilder sb = new StringBuilder();
        if (updated) {
            sb.append("--updated ");
        }
        if (code != null && server != null) {
            sb.append(SCHEME).append("://connect?code=").append(code)
                    .append("&server=").append(java.net.URLEncoder.encode(server, StandardCharsets.UTF_8));
        }
        return sb.toString().trim();
    }

    public boolean hasConnect() {
        return code != null && server != null;
    }
}
