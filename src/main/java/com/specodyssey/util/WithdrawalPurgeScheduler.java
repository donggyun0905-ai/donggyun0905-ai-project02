package com.specodyssey.util;

import com.specodyssey.service.UserService;
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
 * 탈퇴 유예(UserService.WITHDRAWAL_GRACE_DAYS)가 끝난 계정을 매일 00:00에 정리하는 스케줄러.
 * 관련 요구사항: FR-13
 * 아이디를 del_<id>_로 비워 다른 사람이 쓸 수 있게 하고 개인정보를 지운다. 행은 논리 삭제 원칙대로 남는다.
 * 서버가 자정에 꺼져 있었을 경우를 위해 기동 직후에도 한 번 돈다(이미 정리한 행은 건너뛰어 멱등).
 * 이 배치가 늦게 돌아도 가입은 막히지 않는다 — 가입 시 유예가 끝난 계정의 아이디는 그 자리에서 비운다.
 */
@WebListener
public class WithdrawalPurgeScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(WithdrawalPurgeScheduler.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "withdrawal-purge");
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

    // 실패가 스케줄을 죽이면 다음 날부터 영영 안 돌기 때문에 예외는 여기서 삼킨다
    private void runSafely() {
        try {
            int purged = new UserService().purgeExpiredWithdrawals();
            LOG.info(() -> "탈퇴 유예가 끝난 계정 " + purged + "개 정리");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "탈퇴 계정 정리 배치 실패 — 다음 주기에 재시도", e);
        }
    }

    private static long millisUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
    }
}
