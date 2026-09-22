package com.specodyssey.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DB 커넥션 발급 유틸.
 * 접속 정보는 소스에 하드코딩하지 않고 아래 순서로 찾는다:
 *   1. src/main/resources/.env 파일 (KEY=VALUE 형식, 로컬 전용 — .gitignore로 커밋 안 됨)
 *   2. 환경변수(DB_URL, DB_USER, DB_PASSWORD) — .env가 없을 때의 대체 수단(CI 등)
 * .env는 각자 로컬에 .env.example을 복사해서 실제 값으로 채운다.
 */
public final class DBUtil {

    private static final String ENV_FILE = "/.env";

    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;

    static {
        Properties env = loadEnvFile();
        DB_URL = firstNonNull(env.getProperty("DB_URL"), System.getenv("DB_URL"));
        DB_USER = firstNonNull(env.getProperty("DB_USER"), System.getenv("DB_USER"));
        DB_PASSWORD = firstNonNull(env.getProperty("DB_PASSWORD"), System.getenv("DB_PASSWORD"));

        if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
            throw new ExceptionInInitializerError(
                "DB_URL, DB_USER, DB_PASSWORD를 찾을 수 없습니다. 아래 둘 중 하나로 설정하세요.\n" +
                "1) src/main/resources/.env.example을 .env로 복사해서 실제 값 채우기\n" +
                "2) 환경변수로 직접 지정 (예: DB_URL=jdbc:mysql://localhost:3306/spec_odyssey_test" +
                "?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8)"
            );
        }
        try {
            // Tomcat은 manager/examples 등 여러 webapp을 한 JVM에서 같이 띄운다.
            // 이 앱보다 먼저 다른 webapp이 DriverManager를 건드리면 JDBC 4 자동 탐색이
            // 이 WAR의 WEB-INF/lib/mysql-connector-j.jar를 못 찾을 수 있어 명시적으로 로드한다.
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("MySQL 드라이버(com.mysql.cj.jdbc.Driver)를 찾을 수 없습니다: " + e);
        }
    }

    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // src/main/resources/.env를 클래스패스에서 읽는다 — 배포 위치(WAR/exploded 등)와 무관하게 항상 같은 방식으로 찾는다.
    // 없으면 조용히 빈 Properties를 반환하고 환경변수로 넘어간다.
    private static Properties loadEnvFile() {
        Properties props = new Properties();
        try (InputStream in = DBUtil.class.getResourceAsStream(ENV_FILE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(".env 파일을 읽는 중 오류가 발생했습니다: " + e);
        }
        return props;
    }

    private static String firstNonNull(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback;
    }
}
