package com.specodyssey.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 설정값(API 키 등) 조회 유틸.
 * DBUtil·FileStorageUtil과 같은 규칙으로 찾는다:
 *   1. src/main/resources/.env 파일 (KEY=VALUE 형식, 로컬 전용 — .gitignore(*.env)로 커밋 안 됨)
 *   2. 환경변수 — .env에 값이 없을 때의 대체 수단(CI 등)
 * .env는 각자 로컬에 .env.example을 복사해서 실제 값으로 채운다.
 */
public final class AppConfig {

    private static final String ENV_FILE = "/.env";
    private static final Properties PROPS = load();

    private AppConfig() {
    }

    /** 값이 없으면 null. */
    public static String get(String key) {
        String value = PROPS.getProperty(key);
        if (value != null) {
            // Properties는 줄 끝 주석을 지원하지 않는다 — "KEY=값   # 설명" 형태로 적어도 값만 쓰도록 공백 뒤 #부터 잘라낸다.
            value = value.replaceFirst("\\s+#.*$", "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        String env = System.getenv(key);
        return env == null || env.isBlank() ? null : env.trim();
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(ENV_FILE)) {
            if (in != null) {
                // 한글 주석이 있으므로 UTF-8로 읽는다 (Properties.load(InputStream)은 ISO-8859-1).
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(".env 파일을 읽는 중 오류가 발생했습니다: " + e);
        }
        return props;
    }
}
