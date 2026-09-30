package com.specodyssey.util;

import com.specodyssey.service.SpecScoreService;
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
 * SPEC_SCORE_HISTORY 스냅샷을 매일 00:00에 전체 사용자에 대해 실행하는 스케줄러.
 * 관련 요구사항: FR-41 · 45 · 84
 * TrendScheduler와 같은 패턴 — 서버가 자정에 꺼져 있었을 경우를 위해 기동 직후에도
 * "오늘 아직 기록 안 한 사용자"만 골라 한 번 실행한다(SpecScoreService가 사용자별로 멱등 처리).
 */
@WebListener
public class SpecScoreScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(SpecScoreScheduler.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "spec-score-snapshot");
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

    // 한 명 실패나 배치 전체 실패가 스케줄을 죽이면 다음 날부터 영영 안 돌기 때문에 예외는 여기서 삼킨다
    private void runSafely() {
        try {
            int recorded = new SpecScoreService().snapshotAllIfNotYetToday();
            LOG.info(() -> "SPEC_SCORE_HISTORY 일일 스냅샷 " + recorded + "명 기록");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "SPEC_SCORE_HISTORY 스냅샷 배치 실패 — 다음 주기에 재시도", e);
        }
    }

    private static long millisUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
    }
}
