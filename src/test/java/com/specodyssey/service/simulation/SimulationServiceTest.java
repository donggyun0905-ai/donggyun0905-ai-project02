package com.specodyssey.service.simulation;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.SimulationDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.SimulationStateDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ScoreService;
import com.specodyssey.util.AppClock;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시뮬레이션을 실제 DB로 며칠 돌려 본다 — 날짜, 일시정지·이어 하기, 초기화 범위, 권한.
 * 테스트용 계정 2개(테스트 계정 1, 일반 계정 1)를 만들고 끝나면 지운다.
 */
class SimulationServiceTest {

    private static final List<String> ACTIVITY_TABLES = List.of(
            "USER_DAILY_MISSION", "SCORE_LOG", "USER_SCORE_SUMMARY", "SPEC_SCORE_HISTORY", "GAP_ANALYSIS", "ROADMAP");

    private final UserDao userDao = new UserDao();
    private final SimulationDao simulationDao = new SimulationDao();
    private final SimulationService service = new SimulationService(simulationDao);

    private Long testerId;
    private Long normalId;

    @BeforeEach
    void setUp() throws Exception {
        JobDto backend = new JobDao().findAll().stream()
                .filter(j -> "BACKEND".equals(j.getJobCategory()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("시드 데이터에 BACKEND 직무가 없습니다"));
        testerId = newUser("sim_test_" + System.nanoTime(), true, backend.getId());
        normalId = newUser("sim_normal_" + System.nanoTime(), false, backend.getId());
        // 일반 계정에도 활동 데이터가 있어야 "초기화가 일반 계정을 건드리지 않는다"를 확인할 수 있다
        new ScoreService().award(normalId, "QUIZ", 1L, 5);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Long id : new Long[]{testerId, normalId}) {
            if (id == null) {
                continue;
            }
            sql("UPDATE USERS SET is_test = TRUE WHERE id = ?", id); // 정리는 초기화와 같은 코드로
            simulationDao.resetTestUserData(id, LocalDateTime.of(2000, 1, 1, 0, 0));
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", id);
                TestFixtures.hardDeleteByColumn(conn, "USER_SPECS", "user_id", id);
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    @Test
    void 하루씩_그날_날짜로_쌓는다() throws Exception {
        SimulationStateDto state = service.prepareStart(testerId, 1000);
        SimPersona persona = SimPersona.fromName(state.getPersona());
        LocalDate start = AppClock.today().minusDays(persona.estimateDays(0, 1000, SimulationService.MAX_DAYS));
        assertEquals(start, state.getStartDate(), "첫날은 목표까지 어림잡은 날 수만큼 전");
        assertEquals(1000, state.getTargetScore());

        for (int i = 0; i < 3; i++) {
            service.runNextDay(testerId);
        }
        assertEquals(3, simulationDao.findByUserId(testerId).getDaysDone());

        // 첫날 격차 분석·로드맵이 만들어지고, 날짜가 그날로 맞춰져 있다
        assertEquals(1, count("SELECT COUNT(*) FROM GAP_ANALYSIS WHERE user_id = ? AND DATE(analyzed_at) = ? AND DATE(created_at) = ?",
                testerId, Date.valueOf(start), Date.valueOf(start)));
        assertEquals(1, count("SELECT COUNT(*) FROM ROADMAP WHERE user_id = ? AND DATE(created_at) = ?", testerId, Date.valueOf(start)));
        // 스펙 점수 기록은 하루에 하나, 3일치
        assertEquals(3, count("SELECT COUNT(*) FROM SPEC_SCORE_HISTORY WHERE user_id = ? AND snapshot_date BETWEEN ? AND ?",
                testerId, Date.valueOf(start), Date.valueOf(start.plusDays(2))));
        // 미션·점수는 모두 시뮬레이션 날짜 안 — 오늘 날짜로 들어간 행이 없다
        assertEquals(0, count("SELECT COUNT(*) FROM USER_DAILY_MISSION WHERE user_id = ? AND (assigned_date < ? OR assigned_date > ?)",
                testerId, Date.valueOf(start), Date.valueOf(start.plusDays(2))));
        assertEquals(0, count("SELECT COUNT(*) FROM SCORE_LOG WHERE user_id = ? AND DATE(earned_at) > ?",
                testerId, Date.valueOf(start.plusDays(2))));
        assertEquals(0, count("SELECT COUNT(*) FROM USER_DAILY_MISSION WHERE user_id = ? AND DATE(created_at) >= ?",
                testerId, Date.valueOf(AppClock.today())));
    }

    @Test
    void 일시정지하면_멈추고_이어_하면_다음_날부터() throws Exception {
        service.prepareStart(testerId, 100_000);
        service.runNextDay(testerId);
        service.runNextDay(testerId);

        service.pause(testerId);
        SimulationStateDto paused = service.runNextDay(testerId);
        assertEquals(SimulationStateDto.PAUSED, paused.getStatus());
        assertEquals(2, paused.getDaysDone(), "멈춘 동안은 진행하지 않는다");

        service.prepareStart(testerId, 100_000);
        SimulationStateDto resumed = service.runNextDay(testerId);
        assertEquals(3, resumed.getDaysDone());
        LocalDate start = resumed.getStartDate();
        // 같은 날을 두 번 돌리지 않았다 — 스펙 점수 기록이 날마다 정확히 하나
        assertEquals(3, count("SELECT COUNT(DISTINCT snapshot_date) FROM SPEC_SCORE_HISTORY WHERE user_id = ?", testerId));
        assertEquals(3, count("SELECT COUNT(*) FROM SPEC_SCORE_HISTORY WHERE user_id = ? AND snapshot_date BETWEEN ? AND ?",
                testerId, Date.valueOf(start), Date.valueOf(start.plusDays(2))));
    }

    @Test
    void 초기화는_테스트_계정_데이터만_지운다() throws Exception {
        service.prepareStart(testerId, 100_000);
        service.runNextDay(testerId);
        service.runNextDay(testerId);
        assertTrue(count("SELECT COUNT(*) FROM USER_DAILY_MISSION WHERE user_id = ?", testerId) > 0
                || count("SELECT COUNT(*) FROM SPEC_SCORE_HISTORY WHERE user_id = ?", testerId) > 0);

        Map<String, Integer> normalBefore = countAll(normalId);
        service.reset(testerId);

        assertEquals(normalBefore, countAll(normalId), "일반 계정 데이터는 그대로");
        for (Map.Entry<String, Integer> e : countAll(testerId).entrySet()) {
            assertEquals(0, e.getValue(), e.getKey() + "가 남아 있다");
        }
        assertEquals(null, simulationDao.findByUserId(testerId), "진행 상태도 지운다");
        assertNotNull(userDao.findById(testerId), "계정은 남긴다");

        // 다시 시작할 수 있다
        assertEquals(0, service.prepareStart(testerId, 1000).getDaysDone());
    }

    @Test
    void 목표_점수에_닿으면_멈추고_며칠_걸렸는지_남는다() throws Exception {
        service.prepareStart(testerId, 15); // 첫날 미션 몇 개면 닿는 점수
        SimulationStateDto state = null;
        for (int i = 0; i < 30; i++) {
            state = service.runNextDay(testerId);
            if (state.isDone()) {
                break;
            }
        }
        assertEquals(SimulationStateDto.DONE, state.getStatus());
        SimulationService.Status status = service.status(testerId);
        assertTrue(status.reached(), "목표 달성");
        assertTrue(status.score() >= 15);
        assertEquals(state.getDaysDone(), status.daysDone(), "걸린 날 수");

        // 끝난 뒤 지금보다 낮은 목표는 안내, 더 높은 목표는 이어서 간다
        assertThrows(IllegalStateException.class, () -> service.prepareStart(testerId, 10));
        int doneBefore = state.getDaysDone();
        SimulationStateDto more = service.prepareStart(testerId, status.score() + 500);
        assertEquals(SimulationStateDto.RUNNING, more.getStatus());
        assertEquals(doneBefore + 1, service.runNextDay(testerId).getDaysDone(), "다음 날부터 이어 간다");
    }

    @Test
    void 고른_성향으로_시작하고_없는_성향은_안내한다() throws Exception {
        assertThrows(IllegalStateException.class, () -> service.prepareStart(testerId, 1000, "없는성향"));
        assertEquals("ON_OFF", service.prepareStart(testerId, 1000, "ON_OFF").getPersona());
        assertEquals("작심삼일형", service.status(testerId).persona());
        assertEquals("ON_OFF", service.status(testerId).personaCode());
    }

    @Test
    void 판을_새로_시작하면_난수_씨앗이_달라진다() {
        SimulationStateDto first = new SimulationStateDto();
        first.setUserId(42L);
        first.setStartedAt(LocalDateTime.of(2026, 10, 6, 11, 0, 0));
        SimulationStateDto second = new SimulationStateDto();
        second.setUserId(42L);
        second.setStartedAt(LocalDateTime.of(2026, 10, 6, 11, 0, 1));
        assertTrue(SimulationService.seedOf(first) != SimulationService.seedOf(second), "시작 시각이 1초만 달라도 다른 판");
        assertEquals(SimulationService.seedOf(first), SimulationService.seedOf(first), "같은 판은 이어 해도 같다");
    }

    @Test
    void 목표_점수가_없거나_범위를_벗어나면_시작하지_않는다() throws Exception {
        assertThrows(IllegalStateException.class, () -> service.prepareStart(testerId, null));
        assertThrows(IllegalStateException.class, () -> service.prepareStart(testerId, 0));
        assertThrows(IllegalStateException.class, () -> service.prepareStart(testerId, SimulationService.MAX_TARGET + 1));
        assertEquals(null, simulationDao.findByUserId(testerId));
    }

    @Test
    void 일반_계정은_시작도_초기화도_못_한다() throws Exception {
        Map<String, Integer> before = countAll(normalId);
        assertThrows(SecurityException.class, () -> service.prepareStart(normalId, 1000));
        assertThrows(SecurityException.class, () -> service.reset(normalId));
        assertThrows(SecurityException.class, () -> simulationDao.resetTestUserData(normalId, null));
        assertEquals(before, countAll(normalId));
    }

    @Test
    void 희망_직무가_없으면_안내하고_시작하지_않는다() throws Exception {
        sql("UPDATE USERS SET desired_job_id = NULL WHERE id = ?", testerId);
        assertThrows(IllegalStateException.class, () -> service.prepareStart(testerId, 1000));
        assertEquals(null, simulationDao.findByUserId(testerId));
    }

    // ----------------------------------------------------------------

    private Long newUser(String loginId, boolean test, Long jobId) throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(loginId);
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        Long id = userDao.insert(user);
        sql("UPDATE USERS SET is_test = ?, desired_job_id = ? WHERE id = ?", test, jobId, id);
        return id;
    }

    private Map<String, Integer> countAll(Long userId) throws Exception {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : ACTIVITY_TABLES) {
            counts.put(table, count("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", userId));
        }
        return counts;
    }

    private static int count(String sql, Object... params) throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void sql(String sql, Object... params) throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            pstmt.executeUpdate();
        }
    }
}
