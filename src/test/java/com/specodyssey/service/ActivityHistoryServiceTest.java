package com.specodyssey.service;

import com.specodyssey.dao.ActivityDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 활동 내역 — 잔디 격자·단계 계산과 타임라인 문구. 격자 계산은 DB 없이, 적립 기록 조회는 실제 DB로 본다.
 */
class ActivityHistoryServiceTest {

    private static UserDto user;
    private final ActivityHistoryService service = new ActivityHistoryService();

    @BeforeAll
    static void setUp() throws Exception {
        user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_act_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        user.setId(new UserDao().insert(user));
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "SCORE_LOG", "user_id", user.getId());
            TestFixtures.hardDelete(conn, "USERS", user.getId());
        }
    }

    private void award(String signalType, long refId, int points, LocalDateTime at) throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement(
                     "INSERT INTO SCORE_LOG (user_id, signal_type, ref_id, points, earned_at) VALUES (?, ?, ?, ?, ?)")) {
            p.setLong(1, user.getId());
            p.setString(2, signalType);
            p.setLong(3, refId);
            p.setInt(4, points);
            p.setObject(5, at);
            p.executeUpdate();
        }
    }

    @Test
    void 활동이_없으면_빈_내역이고_격자는_그대로_만들어진다() throws Exception {
        ActivityHistoryService.History history = service.load(user.getId(), LocalDate.of(2026, 10, 7));

        assertTrue(history.isEmpty());
        assertEquals(0, history.getTotalEvents(), "화면은 empty 대신 이 값을 본다 — empty는 EL 예약어라 ${x.empty}를 쓸 수 없다");
        assertEquals(ActivityHistoryService.WEEKS, history.getWeeks().size(), "주 수는 항상 같다");
        assertTrue(history.getWeeks().stream().allMatch(w -> w.size() == 7), "한 열은 월~일 7칸");
        assertEquals(0, history.getTotalEvents());
        assertTrue(history.getTimeline().isEmpty());
    }

    @Test
    void 적립_기록이_날짜별_칸과_타임라인에_들어간다() throws Exception {
        LocalDate today = LocalDate.of(2026, 10, 7);
        award("PROBLEM", 90001L, 6, today.minusDays(3).atTime(10, 0));
        award("PROBLEM", 90002L, 6, today.minusDays(3).atTime(11, 0));
        award("STREAK", 90003L, 2, today.minusDays(1).atTime(9, 30));
        try {
            ActivityHistoryService.History history = service.load(user.getId(), today);

            assertFalse(history.isEmpty());
            assertEquals(3, history.getTotalEvents(), "적립 3건이 모두 세어진다");
            assertEquals(2, history.getActiveDays(), "같은 날 두 번 해도 활동한 날은 하루");

            long filled = history.getWeeks().stream().flatMap(List::stream)
                    .filter(c -> !c.isFiller() && c.getEvents() > 0).count();
            assertEquals(2, filled, "활동이 있는 칸은 이틀");

            // 가장 많이 한 날(2회)이 4단계, 1회인 날은 그보다 낮다
            int busiest = history.getWeeks().stream().flatMap(List::stream)
                    .filter(c -> c.getEvents() == 2).findFirst().orElseThrow().getLevel();
            int lighter = history.getWeeks().stream().flatMap(List::stream)
                    .filter(c -> c.getEvents() == 1).findFirst().orElseThrow().getLevel();
            assertEquals(4, busiest);
            assertTrue(lighter < busiest, "덜 한 날은 색이 연해야 한다: " + lighter);

            assertEquals(3, history.getTimeline().size());
            assertEquals("연속 기록", history.getTimeline().get(0).getLabel(), "최신이 먼저 온다");
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "SCORE_LOG", "user_id", user.getId());
            }
        }
    }

    @Test
    void 아직_오지_않은_날은_빈_칸으로_둔다() throws Exception {
        // 오늘이 수요일이면 그 주의 목~일은 아직 오지 않았다
        LocalDate wednesday = LocalDate.of(2026, 10, 7);
        ActivityHistoryService.History history = service.load(user.getId(), wednesday);

        List<ActivityHistoryService.Cell> lastWeek = history.getWeeks().get(history.getWeeks().size() - 1);
        assertEquals(4, lastWeek.stream().filter(ActivityHistoryService.Cell::isFiller).count(),
                "목·금·토·일 네 칸이 빈 칸");
        assertFalse(lastWeek.get(2).isFiller(), "수요일 칸은 있다");
    }

    @Test
    void 단계는_가장_많이_한_날을_기준으로_나뉜다() {
        assertEquals(0, ActivityHistoryService.level(0, 10), "활동이 없으면 0");
        assertEquals(4, ActivityHistoryService.level(10, 10), "최대치는 4");
        assertEquals(1, ActivityHistoryService.level(1, 10));
        assertEquals(4, ActivityHistoryService.level(1, 1), "하루만 활동했으면 그 하루가 최대치");
    }

    @Test
    void 로드맵_활동은_어떤_단계였는지_문구에_넣고_나머지는_종류만_보여_준다() {
        ActivityDao.ActivityRow roadmap = new ActivityDao.ActivityRow(
                LocalDateTime.of(2026, 10, 5, 14, 30), "ROADMAP", 120, "PROJECT",
                "아이디어: 도서 대출 관리 API — Spring Boot로 만드는 프로젝트입니다.");
        ActivityHistoryService.Entry entry = ActivityHistoryService.toEntry(roadmap);
        assertEquals("2026.10.05 14:30", entry.getStamp());
        assertEquals("로드맵 프로젝트", entry.getLabel());
        assertEquals("도서 대출 관리 API", entry.getDetail(), "앞머리와 긴 설명을 떼고 제목만");
        assertEquals(120, entry.getPoints());

        ActivityDao.ActivityRow problem = new ActivityDao.ActivityRow(
                LocalDateTime.of(2026, 10, 6, 9, 0), "PROBLEM", 6, null, null);
        ActivityHistoryService.Entry problemEntry = ActivityHistoryService.toEntry(problem);
        assertEquals("문제 풀이", problemEntry.getLabel());
        assertNull(problemEntry.getDetail(), "문제 내용은 공유 대상이 아니다");
    }
}
