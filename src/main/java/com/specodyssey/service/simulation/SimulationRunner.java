package com.specodyssey.service.simulation;

import com.specodyssey.dao.SimulationDao;
import com.specodyssey.dto.SimulationStateDto;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 시뮬레이션 작업 스레드 — 계정마다 하루를 돌리고 DAY_GAP_MS 쉬었다가 다음 날을 돌린다 (70일 ≈ 2~3분).
 * 진행 상태는 DB(SIMULATION_STATE)에 있어서, 일시정지는 상태만 바꾸면 다음 날로 넘어가기 전에 멈춘다.
 * 서버가 다시 켜지면 돌던 것은 일시정지로 바꾼다 (작업 스레드가 사라졌으므로) — ▶로 이어 하면 된다.
 */
@WebListener
public class SimulationRunner implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(SimulationRunner.class.getName());

    /** 하루를 끝내고 다음 날까지 쉬는 시간 — 하루 처리(DB 수십 번)와 합쳐 하루 2초 남짓 */
    static final long DAY_GAP_MS = 800;
    /** "화면 따라가기"를 켠 동안은 화면이 바뀌는 걸 눈으로 따라갈 수 있게 천천히 (70일 약 4분) */
    static final long FOLLOW_DAY_GAP_MS = 3000;

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "test-simulation");
        thread.setDaemon(true);
        return thread;
    });
    /** 지금 작업이 잡혀 있는 계정 — 같은 계정을 두 번 돌리지 않게 */
    private static final Map<Long, Boolean> SCHEDULED = new ConcurrentHashMap<>();
    /** 계정별 잠금 — 하루를 돌리는 중에는 초기화가 기다린다 (지운 뒤에 그날 행이 다시 생기지 않게) */
    private static final Map<Long, Object> LOCKS = new ConcurrentHashMap<>();
    /** "화면 따라가기"를 켠 계정 (메모리 — 패널이 켤 때마다 다시 알려 준다) */
    private static final Map<Long, Boolean> FOLLOWING = new ConcurrentHashMap<>();

    private static final SimulationService SERVICE = new SimulationService();

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            int paused = new SimulationDao().pauseAllRunning("서버가 다시 시작되어 멈췄어요. ▶을 누르면 이어서 진행합니다.");
            if (paused > 0) {
                LOG.info("서버 시작 — 돌던 시뮬레이션 " + paused + "개를 일시정지로 바꿨습니다");
            }
        } catch (SQLException e) {
            // 테이블이 아직 없는 DB(sql/24 미실행)에서도 서버는 떠야 한다
            LOG.log(Level.WARNING, "시뮬레이션 상태를 정리하지 못했습니다 (sql/24 실행 여부 확인)", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        EXECUTOR.shutdownNow();
    }

    // ---------------------------------------------------------------- 버튼 동작

    /** ▶ — 목표 점수까지 시작, 일시정지였으면 이어 간다, 끝났으면 더 높은 목표로 이어 간다 */
    public static void start(Long userId, Integer targetScore, String personaName) throws SQLException {
        SERVICE.prepareStart(userId, targetScore, personaName);
        if (SCHEDULED.putIfAbsent(userId, Boolean.TRUE) == null) {
            EXECUTOR.execute(() -> runDay(userId));
        }
    }

    /** ⏸ — 지금 돌던 하루까지만 마치고 멈춘다 */
    public static void pause(Long userId) throws SQLException {
        SERVICE.pause(userId);
    }

    /** 화면 따라가기 켜기·끄기 — 켜져 있으면 하루 간격을 늘린다 */
    public static void setFollow(Long userId, boolean on) {
        if (on) {
            FOLLOWING.put(userId, Boolean.TRUE);
        } else {
            FOLLOWING.remove(userId);
        }
    }

    public static boolean isFollowing(Long userId) {
        return FOLLOWING.containsKey(userId);
    }

    /** ↺ — 멈추고, 돌던 하루가 끝나길 기다렸다가 데이터를 지운다 */
    public static Map<String, Integer> reset(Long userId) throws SQLException {
        if (!SERVICE.isTester(userId)) {
            throw new SecurityException("테스트 계정만 쓸 수 있는 기능입니다.");
        }
        if (SERVICE.status(userId).status().equals(SimulationStateDto.RUNNING)) {
            SERVICE.pause(userId);
        }
        synchronized (lockFor(userId)) {
            return SERVICE.reset(userId);
        }
    }

    // ---------------------------------------------------------------- 작업 스레드

    private static void runDay(Long userId) {
        SimulationStateDto state;
        try {
            synchronized (lockFor(userId)) {
                state = SERVICE.runNextDay(userId);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "시뮬레이션 하루를 돌리지 못해 멈춥니다 (userId=" + userId + ")", e);
            SCHEDULED.remove(userId);
            try {
                new SimulationDao().updateStatus(userId, SimulationStateDto.PAUSED,
                        "오류로 멈췄어요: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            } catch (SQLException ignored) {
                // 상태 저장까지 실패하면 다음 ▶에서 다시 시도한다
            }
            return;
        }
        if (state != null && state.isRunning() && !state.isDone()) {
            EXECUTOR.schedule(() -> runDay(userId), gapFor(userId), TimeUnit.MILLISECONDS);
        } else {
            SCHEDULED.remove(userId);
            // 멈추려던 순간 ▶이 다시 눌렸으면(상태는 RUNNING인데 작업이 빠짐) 이어서 돈다
            try {
                SimulationStateDto latest = new SimulationDao().findByUserId(userId);
                if (latest != null && latest.isRunning() && !latest.isDone()
                        && SCHEDULED.putIfAbsent(userId, Boolean.TRUE) == null) {
                    EXECUTOR.schedule(() -> runDay(userId), gapFor(userId), TimeUnit.MILLISECONDS);
                }
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "시뮬레이션 상태를 다시 확인하지 못했습니다 (userId=" + userId + ")", e);
            }
        }
    }

    private static long gapFor(Long userId) {
        return isFollowing(userId) ? FOLLOW_DAY_GAP_MS : DAY_GAP_MS;
    }

    private static Object lockFor(Long userId) {
        return LOCKS.computeIfAbsent(userId, id -> new Object());
    }
}
