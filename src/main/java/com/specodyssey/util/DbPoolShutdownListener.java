package com.specodyssey.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** 웹앱이 내려갈 때(서버 종료·재배포) DB 커넥션 풀과 드라이버 정리 스레드를 닫는다. */
@WebListener
public class DbPoolShutdownListener implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        DBUtil.shutdown();
        // MySQL 드라이버가 따로 띄우는 정리 스레드도 같이 멈춘다(안 멈추면 재배포 때마다 스레드·클래스로더가 남는다는 Tomcat 경고)
        com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.uncheckedShutdown();
    }
}
