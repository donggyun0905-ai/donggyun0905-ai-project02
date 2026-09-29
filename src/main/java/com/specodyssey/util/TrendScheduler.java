package com.specodyssey.util;

import com.specodyssey.service.TrendCollectService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 트렌드 기술 수집을 매일 00:00에 실행하는 스케줄러.
 * 관련 요구사항: FR-54 (일 1회 갱신)
 * 서버가 자정에 꺼져 있었을 경우를 위해 기동 직후에도 "오늘 수집분이 없으면" 한 번 실행한다.
 */
@WebListener
public class TrendScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(TrendScheduler.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "trend-collector");
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(this::runSafely);
        executor.scheduleAtFixedRate(this::runSafely, millisUntilMidnight(), TimeUnit.DAYS.toMillis(1),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // 수집 실패가 스케줄을 죽이면 다음 날부터 영영 안 돌기 때문에 예외는 전부 여기서 삼키고 로그만 남긴다
    private void runSafely() {
        try {
            new TrendCollectService().collectIfNotYetToday();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "트렌드 기술 수집 실패 — 직전 데이터를 유지합니다", e);
        }
    }

    private static long millisUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
    }
}
