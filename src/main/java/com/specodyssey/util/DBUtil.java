package com.specodyssey.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DB 커넥션 발급 유틸.
 * 접속 정보는 소스에 하드코딩하지 않고 환경변수(DB_URL, DB_USER, DB_PASSWORD)로 분리한다.
 * Tomcat 구동 전에 setenv.sh/bat 또는 IDE 실행 설정에서 세 값을 지정해야 한다.
 */
public final class DBUtil {

    private static final String DB_URL = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    static {
        if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
            throw new ExceptionInInitializerError(
                "DB_URL, DB_USER, DB_PASSWORD 환경변수를 설정해야 합니다. " +
                "예) DB_URL=jdbc:mysql://localhost:3306/spec_odyssey?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
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
}
