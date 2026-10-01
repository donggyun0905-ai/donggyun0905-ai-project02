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

    // ---------------------------------------------------------------- 간단한 커넥션 풀
    // 호출마다 DriverManager로 새 연결(TCP + 인증)을 맺던 걸 재사용한다. 공유 DB가 원격이면 연결 한 번이
    // 수십~수백 ms라서, 한 화면이 DAO를 여러 번 부르는 것만으로 느려졌다. 외부 라이브러리 없이 JDK만 쓴다.
    // 호출부는 그대로 try-with-resources로 close()하면 되고, close()는 연결을 닫지 않고 풀에 돌려준다.
    // 돌려줄 때 트랜잭션이 열려 있으면 롤백하고 autoCommit을 되돌려 다음 사용자에게 상태가 새지 않게 한다.
    // 풀이 비면 새로 맺고(상한 없음 — 기존 동작과 같다), 풀에는 최대 MAX_IDLE개만 남긴다.
    private static final int MAX_IDLE = 10;
    private static final long VALIDATE_AFTER_IDLE_MILLIS = 5_000;
    private static final long MAX_IDLE_MILLIS = 4 * 60_000; // 서버 wait_timeout보다 짧게

    private static final class IdleConnection {
        final Connection connection;
        final long returnedAtMillis;

        IdleConnection(Connection connection, long returnedAtMillis) {
            this.connection = connection;
            this.returnedAtMillis = returnedAtMillis;
        }
    }

    private static final java.util.ArrayDeque<IdleConnection> IDLE = new java.util.ArrayDeque<>();

    public static Connection getConnection() throws SQLException {
        long now = System.currentTimeMillis();
        while (true) {
            IdleConnection idle;
            synchronized (IDLE) {
                idle = IDLE.pollFirst();
            }
            if (idle == null) {
                break;
            }
            long idleFor = now - idle.returnedAtMillis;
            if (idleFor <= MAX_IDLE_MILLIS && (idleFor < VALIDATE_AFTER_IDLE_MILLIS || isAlive(idle.connection))) {
                return wrap(idle.connection);
            }
            closeQuietly(idle.connection);
        }
        return wrap(DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD));
    }

    private static boolean isAlive(Connection conn) {
        try {
            return !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void closeQuietly(Connection conn) {
        try {
            conn.close();
        } catch (SQLException ignored) {
            // 이미 끊긴 연결 — 버리면 그만이다.
        }
    }

    private static Connection wrap(Connection raw) {
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                DBUtil.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("close".equals(name)) {
                        if (closed.compareAndSet(false, true)) {
                            release(raw);
                        }
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return closed.get() || raw.isClosed();
                    }
                    if (closed.get()) {
                        throw new SQLException("이미 반납한 커넥션입니다.");
                    }
                    try {
                        return method.invoke(raw, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static void release(Connection raw) {
        try {
            if (raw.isClosed()) {
                return;
            }
            if (!raw.getAutoCommit()) {
                raw.rollback();
                raw.setAutoCommit(true);
            }
        } catch (SQLException e) {
            closeQuietly(raw);
            return;
        }
        synchronized (IDLE) {
            if (IDLE.size() < MAX_IDLE) {
                IDLE.addFirst(new IdleConnection(raw, System.currentTimeMillis()));
                return;
            }
        }
        closeQuietly(raw);
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
