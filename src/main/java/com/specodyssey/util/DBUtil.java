package com.specodyssey.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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

    private static final Properties ENV = loadEnvFile();

    static {
        DB_URL = firstNonNull(ENV.getProperty("DB_URL"), System.getenv("DB_URL"));
        DB_USER = firstNonNull(ENV.getProperty("DB_USER"), System.getenv("DB_USER"));
        DB_PASSWORD = firstNonNull(ENV.getProperty("DB_PASSWORD"), System.getenv("DB_PASSWORD"));

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

    // ---------------------------------------------------------------- 커넥션 풀 (HikariCP)
    // 호출마다 새 연결(TCP + 인증)을 맺던 걸 풀에서 빌려 쓴다. 공유 DB가 원격이면 연결 한 번이 수십~수백 ms라서
    // 한 화면이 DAO를 여러 번 부르는 것만으로 느려졌다. 호출부는 그대로 try-with-resources로 close()하면 되고,
    // close()는 연결을 닫지 않고 풀에 돌려준다(커밋 안 한 트랜잭션은 롤백, autoCommit은 원래대로 복구).
    //
    // 크기: 기본 최대 10개 / 최소 유휴 2개. 환경변수 DB_POOL_SIZE로 바꾼다(.env에도 쓸 수 있다).
    // 주의: 트랜잭션(TransactionUtil) 안에서 DAO를 또 부르면 한 요청이 연결을 2개 쓴다 — 동시 요청이 많은데 풀이 너무
    // 작으면 서로 기다리다 connectionTimeout(10초)에 실패할 수 있으니, 동시 사용자가 늘면 DB_POOL_SIZE를 키운다.
    // 팀원 여럿이 같은 공유 DB를 쓰므로 (사람 수 × 최대 크기)가 DB의 max_connections를 넘지 않게 한다.
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final int MIN_IDLE = 2;
    private static final long CONNECTION_TIMEOUT_MS = 10_000;
    private static final long IDLE_TIMEOUT_MS = 5 * 60_000;
    private static final long MAX_LIFETIME_MS = 25 * 60_000; // 서버 wait_timeout(기본 8시간)·중간 장비보다 짧게
    private static final long KEEPALIVE_MS = 2 * 60_000;
    // 드라이버 대기 시간 — 없으면 공유 DB가 응답을 멈췄을 때 요청이 끝없이 기다리고 연결도 풀에 돌아오지 않는다 (2026-10-02)
    // DB_URL에 같은 이름의 값이 이미 있으면 그 값을 쓴다.
    private static final int CONNECT_TIMEOUT_MS = 5_000;  // DB 서버 접속 시도
    private static final int SOCKET_TIMEOUT_MS = 60_000;  // 쿼리 하나의 응답 대기 — 스케줄러 집계 쿼리도 넉넉히 들어오게

    private static volatile HikariDataSource dataSource;
    // 내려가는 중이면 새 풀을 다시 만들지 않는다 — 종료 직전 스케줄러 스레드가 마지막으로 DB를 부르면 닫은 풀이 되살아나 누수가 된다
    private static volatile boolean shutDown;

    private static HikariDataSource pool() {
        HikariDataSource ds = dataSource;
        if (ds == null || ds.isClosed()) {
            synchronized (DBUtil.class) {
                ds = dataSource;
                if (ds == null || ds.isClosed()) {
                    ds = createPool();
                    dataSource = ds;
                }
            }
        }
        return ds;
    }

    private static HikariDataSource createPool() {
        int size = poolSize();
        HikariConfig config = new HikariConfig();
        config.setPoolName("spec-odyssey");
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(Math.min(MIN_IDLE, size));
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setKeepaliveTime(KEEPALIVE_MS);
        config.setAutoCommit(true);
        driverTimeouts(DB_URL).forEach((key, value) -> config.addDataSourceProperty((String) key, value));
        return new HikariDataSource(config);
    }

    // DB_URL에 없는 대기 시간만 기본값으로 채운다
    static Properties driverTimeouts(String url) {
        String query = url == null || url.indexOf('?') < 0 ? "" : url.substring(url.indexOf('?') + 1).toLowerCase();
        Properties props = new Properties();
        if (!hasParam(query, "connecttimeout")) {
            props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        }
        if (!hasParam(query, "sockettimeout")) {
            props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_MS));
        }
        return props;
    }

    private static boolean hasParam(String query, String name) {
        for (String pair : query.split("&")) {
            if (pair.startsWith(name + "=")) {
                return true;
            }
        }
        return false;
    }

    private static int poolSize() {
        String configured = firstNonNull(ENV.getProperty("DB_POOL_SIZE"), System.getenv("DB_POOL_SIZE"));
        if (configured != null) {
            try {
                int n = Integer.parseInt(configured.trim());
                if (n >= 1 && n <= 100) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // 잘못된 값이면 기본값을 쓴다
            }
        }
        return DEFAULT_POOL_SIZE;
    }

    public static Connection getConnection() throws SQLException {
        if (shutDown) {
            throw new SQLException("애플리케이션이 내려가는 중이라 DB 연결을 새로 만들 수 없습니다.");
        }
        if (POOL_DISABLED) {
            return DriverManager.getConnection(DB_URL, unpooledProperties());
        }
        return pool().getConnection();
    }

    // 풀 끄기 — 풀 적용 전/후 응답 시간 측정(docs/performance.md)과 장애 원인 분리용. 운영에서는 켜지 않는다 (2026-10-10)
    // 환경변수(또는 .env) DB_POOL_DISABLED=true일 때만 요청마다 새 연결을 맺는다(풀 도입 전 방식). 대기 시간 설정은 풀과 같다.
    private static final boolean POOL_DISABLED =
            "true".equalsIgnoreCase(firstNonNull(ENV.getProperty("DB_POOL_DISABLED"), System.getenv("DB_POOL_DISABLED")));

    private static Properties unpooledProperties() {
        Properties props = driverTimeouts(DB_URL);
        props.setProperty("user", DB_USER);
        props.setProperty("password", DB_PASSWORD);
        return props;
    }

    /** 애플리케이션이 내려갈 때(재배포 포함) 풀을 닫는다 — 안 닫으면 풀 스레드와 DB 연결이 남는다. */
    public static void shutdown() {
        synchronized (DBUtil.class) {
            shutDown = true;
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            dataSource = null;
        }
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
