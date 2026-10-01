package com.specodyssey.service;

import com.specodyssey.dao.MissionDao;
import com.specodyssey.dao.MissionDao.StreakRow;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 일일 미션 연속 수행(스트릭). 관련 요구사항: FR-53 미션 완료 체크 → 연속 수행 기록
 * 저장 위치는 설계대로 USER_SCORE_SUMMARY.streak_count / last_mission_date (docs/db-design.md).
 *
 * 규칙
 *   - 그날 배정된 문제를 모두 끝내면(코드 제출 또는 실패) 그날을 "수행한 날"로 본다.
 *   - 어제도 수행했으면 +1, 하루라도 비었으면 1부터 다시 센다. 같은 날 여러 번 불려도 한 번만 센다.
 *   - 지난 날짜 미션을 뒤늦게 끝낸 것은 스트릭에 반영하지 않는다(오늘 배정분만).
 */
public class MissionStreakService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int WEEK_DAYS = 7;

    private final MissionDao missionDao = new MissionDao();

    /** 미션 화면 "연속 N일째" 카드용. */
    public record StreakView(int streak, boolean todayDone, List<DayView> days) {
        public int getStreak() {
            return streak;
        }

        public boolean isTodayDone() {
            return todayDone;
        }

        /** 오늘 미션을 끝내면 몇 일째가 되는지. */
        public int getNextStreak() {
            return streak + 1;
        }

        public List<DayView> getDays() {
            return days;
        }
    }

    /** 최근 7일 중 하루 — 요일 한 글자, 날짜(일), 수행 여부, 오늘 여부. */
    public record DayView(String dayOfWeek, int dayOfMonth, boolean done, boolean today) {
        public String getDayOfWeek() {
            return dayOfWeek;
        }

        public int getDayOfMonth() {
            return dayOfMonth;
        }

        public boolean isDone() {
            return done;
        }

        public boolean isToday() {
            return today;
        }
    }

    /**
     * 미션 하나를 끝낸 직후 호출한다(제출·실패 트랜잭션 안에서).
     * 오늘 배정분이 모두 끝났으면 스트릭을 갱신한다. 점수 적립(ScoreService)과 같은 트랜잭션이면 적립 뒤에 불러야 한다.
     */
    public void recordIfDayComplete(Connection conn, Long userId, Long missionId) throws SQLException {
        LocalDate today = LocalDate.now(ZONE);
        if (!today.equals(missionDao.findAssignedDate(conn, userId, missionId))) {
            return;
        }
        int[] counts = missionDao.countMissionsByDate(conn, userId, today, today).get(today);
        if (!isDayDone(counts)) {
            return;
        }
        StreakRow row = missionDao.findStreak(conn, userId);
        LocalDate last = row == null ? null : row.lastMissionDate();
        if (today.equals(last)) {
            return;
        }
        missionDao.saveStreak(conn, userId, nextStreak(row == null ? 0 : row.streakCount(), last, today), today);
    }

    public StreakView getStreakView(Long userId) throws SQLException {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(WEEK_DAYS - 1);
        try (Connection conn = DBUtil.getConnection()) {
            StreakRow row = missionDao.findStreak(conn, userId);
            Map<LocalDate, int[]> counts = missionDao.countMissionsByDate(conn, userId, from, today);

            List<DayView> days = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
                days.add(new DayView(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                        d.getDayOfMonth(), isDayDone(counts.get(d)), d.equals(today)));
            }
            LocalDate last = row == null ? null : row.lastMissionDate();
            int streak = displayStreak(row == null ? 0 : row.streakCount(), last, today);
            return new StreakView(streak, today.equals(last), days);
        }
    }

    /** [배정 수, 완료 수] — 배정이 있고 전부 끝났으면 수행한 날. */
    static boolean isDayDone(int[] counts) {
        return counts != null && counts[0] > 0 && counts[1] >= counts[0];
    }

    /** 오늘을 수행한 날로 기록할 때의 새 스트릭. 어제 이어서면 +1, 아니면 1. */
    static int nextStreak(int previous, LocalDate lastMissionDate, LocalDate today) {
        return today.minusDays(1).equals(lastMissionDate) ? previous + 1 : 1;
    }

    /** 화면에 보일 스트릭 — 마지막 수행일이 오늘이나 어제면 이어지는 중, 그보다 전이면 끊겨서 0. */
    static int displayStreak(int stored, LocalDate lastMissionDate, LocalDate today) {
        if (lastMissionDate == null) {
            return 0;
        }
        boolean alive = lastMissionDate.equals(today) || lastMissionDate.equals(today.minusDays(1));
        return alive ? stored : 0;
    }
}
