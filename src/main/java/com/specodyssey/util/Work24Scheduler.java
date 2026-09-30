package com.specodyssey.util;

import com.specodyssey.service.work24.Work24CollectService;
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
 * 고용24 Open API(직무정보·학과정보·직업정보) 갱신을 매일 00:00에 실행하는 스케줄러.
 * TrendScheduler와 같은 방식 — 서버가 자정에 꺼져 있었을 경우를 위해 기동 직후에도 "오늘 받은 적 없는 API만" 한 번 실행한다.
 * 수동 실행은 ApiUpdater.
 */
@WebListener
public class Work24Scheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(Work24Scheduler.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "work24-collector");
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
            new Work24CollectService().collectIfNotYetToday().forEach(o -> LOG.info(o.toString()));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "고용24 갱신 실패 — 직전 데이터를 유지합니다", e);
        }
    }

    private static long millisUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
    }
}
