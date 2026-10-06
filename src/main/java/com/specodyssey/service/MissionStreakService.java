package com.specodyssey.service;

import com.specodyssey.util.AppClock;
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

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(MissionStreakService.class.getName());
    static final String SIGNAL_TYPE_STREAK = "STREAK";

    private final MissionDao missionDao = new MissionDao();
    private final ScoreService scoreService = new ScoreService();

    /** 미션 화면 "연속 N일째" 카드용. */
    public record StreakView(int streak, boolean todayDone, List<DayView> days, int nextBonus) {
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

        // Tomcat 11(EL 6)의 RecordELResolver는 record를 만나면 getX()가 아니라 구성요소 이름 그대로의
        // 접근자 메서드(x())만 찾는다 — JSP의 ${missionStreak.nextStreak}가 NoSuchMethodException으로 터지던 원인.
        // Tomcat 10 계열(BeanELResolver)은 위 getNextStreak()를 쓰므로 둘 다 둔다.
        public int nextStreak() {
            return getNextStreak();
        }

        public List<DayView> getDays() {
            return days;
        }

        /** 오늘 미션을 풀어서 끝내면 받는 연속 보너스(점). 이미 오늘 끝냈으면 0 */
        public int getNextBonus() {
            return nextBonus;
        }

        public int nextBonus() {
            return nextBonus;
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
    public int recordIfDayComplete(Connection conn, Long userId, Long missionId) throws SQLException {
        LocalDate today = AppClock.today();
        if (!today.equals(missionDao.findAssignedDate(conn, userId, missionId))) {
            return 0;
        }
        int[] counts = missionDao.countMissionsByDate(conn, userId, today, today).get(today);
        if (!isDayDone(counts)) {
            return 0;
        }
        StreakRow row = missionDao.findStreak(conn, userId);
        LocalDate last = row == null ? null : row.lastMissionDate();
        if (today.equals(last)) {
            return 0;
        }
        int streak = nextStreak(row == null ? 0 : row.streakCount(), last, today);
        missionDao.saveStreak(conn, userId, streak, today);
        return streak;
    }

    /**
     * 오늘 처음 끝낸 날(recordIfDayComplete가 돌려준 streak)의 연속 보너스를 적립한다. 하나라도 코드를 제출해서
     * 풀어야 받는다("실패"만 눌러 연속을 이어가는 길을 막는다). 날짜당 한 번(ref_id = 날짜)만 들어가고, 실패해도
     * 문제 제출은 그대로 둔다.
     * @return 받은 보너스 점수(없으면 0)
     */
    public int awardBonusIfEarned(Long userId, int streak) {
        if (streak <= 1) {
            return 0;
        }
        try {
            LocalDate today = AppClock.today();
            int bonus = bonusFor(streak);
            if (bonus <= 0) {
                return 0;
            }
            int solved;
            try (Connection conn = DBUtil.getConnection()) {
                solved = missionDao.countSolvedOn(conn, userId, today);
            }
            if (solved < 1) {
                return 0;
            }
            scoreService.award(userId, SIGNAL_TYPE_STREAK, today.toEpochDay(), bonus);
            return bonus;
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "연속 보너스를 적립하지 못했습니다", e);
            return 0;
        }
    }

    /** streak일째에 받는 보너스 — 하루마다 늘다가 상한에 멈추고, 7일·30일째에는 큰 보너스가 더 붙는다. 첫날은 0 */
    static int bonusFor(int streak) {
        if (streak <= 1) {
            return 0;
        }
        int bonus = Math.min(ScoringRules.get(ScoringRules.STREAK_BONUS_MAX),
                ScoringRules.get(ScoringRules.STREAK_BONUS_PER_DAY) * (streak - 1));
        if (streak == 7) {
            bonus += ScoringRules.get(ScoringRules.STREAK_BONUS_DAY7);
        } else if (streak == 30) {
            bonus += ScoringRules.get(ScoringRules.STREAK_BONUS_DAY30);
        }
        return bonus;
    }

    public StreakView getStreakView(Long userId) throws SQLException {
        LocalDate today = AppClock.today();
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
            boolean todayDone = today.equals(last);
            return new StreakView(streak, todayDone, days, todayDone ? 0 : bonusFor(streak + 1));
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
