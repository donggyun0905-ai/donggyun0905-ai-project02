package com.specodyssey.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 설정값 조회 유틸.
 * 우선순위: 환경변수 → 클래스패스의 config.properties (프로젝트 루트의 파일을 pom.xml이 빌드 시 포함).
 *
 * config.properties는 API 키·DB 비밀번호가 들어가므로 .gitignore 대상이다.
 * 각자 루트의 config.properties.example을 복사해 같은 위치에 config.properties로 만들어 쓴다.
 * Maven 빌드 결과(WEB-INF/classes)에 포함되므로 IntelliJ·VS Code·mvn package 어느 쪽으로 실행해도 같은 값을 읽는다.
 */
public final class AppConfig {

    private static final String FILE_NAME = "config.properties";
    private static final Properties PROPS = load();

    private AppConfig() {
    }

    /** 값이 없으면 null. */
    public static String get(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String value = PROPS.getProperty(key);
        if (value == null) {
            return null;
        }
        // Properties는 줄 끝 주석을 지원하지 않는다 — "KEY=값   # 설명" 형태로 적어도 값만 쓰도록 공백 뒤 #부터 잘라낸다.
        value = value.replaceFirst("\\s+#.*$", "").trim();
        return value.isEmpty() ? null : value;
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
            if (in != null) {
                // 한글 주석이 있으므로 UTF-8로 읽는다 (Properties.load(InputStream)은 ISO-8859-1).
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(FILE_NAME + " 읽기 실패: " + e);
        }
        return props;
    }
}
