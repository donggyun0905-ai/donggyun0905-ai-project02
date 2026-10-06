package com.specodyssey.util;

import com.specodyssey.service.NotificationService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 시간에 맞춰 보내는 알림 스케줄러 (한국 시간 기준).
 * - 매일 09:00  D-day 알림 — 내일이 목표일인 일정(하루 전)과 오늘이 목표일인 일정(당일)
 * - 매일 23:00  오늘의 미션 마감 1시간 전 알림 — 미션은 00시에 바뀐다(DailyMissionService)
 * 서버가 그 시각에 꺼져 있었을 경우를 위해 기동 직후에도 "오늘 이미 지난 시각"의 알림을 한 번 만든다.
 * 같은 알림은 NOTIFICATION의 복합 UNIQUE로 막혀서, 재기동으로 여러 번 돌아도 한 번만 쌓인다.
 */
@WebListener
public class NotificationScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(NotificationScheduler.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final LocalTime DDAY_TIME = LocalTime.of(9, 0);
    static final LocalTime MISSION_TIME = LocalTime.of(23, 0);

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "notification-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(this::catchUp);
        long day = TimeUnit.DAYS.toMillis(1);
        executor.scheduleAtFixedRate(this::runDday, millisUntil(DDAY_TIME), day, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::runMission, millisUntil(MISSION_TIME), day, TimeUnit.MILLISECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // 기동 시각이 이미 그 시각을 지났으면 오늘 몫을 바로 만든다
    private void catchUp() {
        LocalTime now = LocalTime.now(ZONE);
        if (!now.isBefore(DDAY_TIME)) {
            runDday();
        }
        if (!now.isBefore(MISSION_TIME)) {
            runMission();
        }
    }

    // 실패가 스케줄을 죽이면 다음 날부터 영영 안 돌기 때문에 예외는 전부 여기서 삼키고 로그만 남긴다
    private void runDday() {
        try {
            int created = new NotificationService().createDdayReminders(LocalDate.now(ZONE));
            LOG.info("D-day 알림(하루 전·당일) 생성: " + created + "건");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "D-day 알림 생성 실패", e);
        }
    }

    private void runMission() {
        try {
            int created = new NotificationService().createMissionReminders(LocalDate.now(ZONE));
            LOG.info("오늘의 미션 마감 알림 생성: " + created + "건");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "오늘의 미션 알림 생성 실패", e);
        }
    }

    /** 지금부터 다음 time(오늘 또는 내일)까지 남은 밀리초 */
    static long millisUntil(LocalDateTime now, LocalTime time) {
        LocalDateTime next = now.toLocalDate().atTime(time);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }

    private static long millisUntil(LocalTime time) {
        return millisUntil(LocalDateTime.now(ZONE), time);
    }
}
