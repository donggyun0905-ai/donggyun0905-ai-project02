package com.specodyssey.service;

import com.specodyssey.dao.ActivityDao;
import com.specodyssey.dao.ActivityDao.ActivityRow;
import com.specodyssey.dao.ActivityDao.DayCount;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 활동 내역 (2026-10-07 사용자 요청) — 면접관에게 "언제 무엇을 얼마나 꾸준히 했는지"를 보여 준다.
 *
 * 깃허브 잔디처럼 날짜별 활동량을 칸으로 깔고, 그 아래에 최근 활동 타임라인을 둔다.
 * 지원자가 공유 링크에서 "활동 내역"을 켠 링크에서만 보인다(SHARE_LINK.scope_activity, 기본 비공개).
 *
 * 화면은 출력만 한다(claude.md) — 주 단위 격자 묶기·단계 색·문구 만들기를 모두 여기서 끝낸다.
 */
public class ActivityHistoryService {

    /** 잔디에 보여 줄 기간 — 너무 길면 칸이 잘게 쪼개져 읽기 어렵다. */
    static final int WEEKS = 26;
    static final int TIMELINE_LIMIT = 20;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    /** 잔디 한 칸. level 0~4 — 0은 활동 없음. date가 null이면 격자를 채우는 빈 칸이다. */
    public record Cell(LocalDate date, int events, int points, int level) {
        public LocalDate getDate() {
            return date;
        }

        public int getEvents() {
            return events;
        }

        public int getPoints() {
            return points;
        }

        public int getLevel() {
            return level;
        }

        /** 칸에 마우스를 올렸을 때 보여 줄 설명 */
        public String getTitle() {
            if (date == null) {
                return "";
            }
            return events == 0
                    ? date + " 활동 없음"
                    : date + " 활동 " + events + "회 (+" + points + "점)";
        }

        public boolean isFiller() {
            return date == null;
        }

        // 구성요소가 아닌 파생 값은 x() 형태도 둬야 한다 — Tomcat 11의 RecordELResolver는
        // record에서 isX()/getX()를 찾지 않는다. ${cell.title}·${cell.filler}가 거기서만 500이었다.
        public String title() {
            return getTitle();
        }

        public boolean filler() {
            return isFiller();
        }
    }

    /** 타임라인 한 줄 — 화면은 이 문구를 그대로 쓴다. */
    public record Entry(String stamp, String label, String detail, int points) {
        public String getStamp() {
            return stamp;
        }

        public String getLabel() {
            return label;
        }

        public String getDetail() {
            return detail;
        }

        public int getPoints() {
            return points;
        }
    }

    /**
     * 활동 내역 한 묶음.
     * weeks는 주 단위 열(각 열이 7칸, 월요일 시작) — 화면이 열을 세로로 쌓아 잔디를 그린다.
     */
    public record History(List<List<Cell>> weeks, List<String> monthLabels, int activeDays, int totalEvents,
                          String rangeText, List<Entry> timeline) {
        public List<List<Cell>> getWeeks() {
            return weeks;
        }

        public List<String> getMonthLabels() {
            return monthLabels;
        }

        public int getActiveDays() {
            return activeDays;
        }

        public int getTotalEvents() {
            return totalEvents;
        }

        public String getRangeText() {
            return rangeText;
        }

        public List<Entry> getTimeline() {
            return timeline;
        }

        /**
         * 자바 코드용 — 화면(EL)은 이 값을 읽을 수 없다. empty는 EL 예약어라 ${x.empty}가 속성 접근이 아니라
         * 파싱 오류가 된다(Tomcat 11의 EL 6.0에서 실제로 화면이 500이었다). 화면은 totalEvents == 0을 쓴다.
         */
        public boolean isEmpty() {
            return totalEvents == 0;
        }
    }

    private final ActivityDao activityDao = new ActivityDao();

    public History load(Long userId) throws SQLException {
        return load(userId, LocalDate.now());
    }

    /** today를 받는 쪽은 테스트 — 잔디 범위가 "오늘"에 따라 달라지기 때문이다. */
    History load(Long userId, LocalDate today) throws SQLException {
        LocalDate end = today;
        // 월요일 시작 격자 — 마지막 열이 이번 주가 되도록 끝을 이번 주 일요일로 맞춘다
        LocalDate lastSunday = end.plusDays(7 - end.getDayOfWeek().getValue());
        LocalDate firstMonday = lastSunday.minusWeeks(WEEKS - 1).minusDays(6);

        Map<LocalDate, DayCount> byDate = new LinkedHashMap<>();
        for (DayCount day : activityDao.findDailyCounts(userId, firstMonday)) {
            byDate.put(day.date(), day);
        }
        int maxEvents = byDate.values().stream().mapToInt(DayCount::events).max().orElse(0);

        List<List<Cell>> weeks = new ArrayList<>();
        List<String> monthLabels = new ArrayList<>();
        int totalEvents = 0;
        for (LocalDate weekStart = firstMonday; !weekStart.isAfter(lastSunday); weekStart = weekStart.plusWeeks(1)) {
            List<Cell> week = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekStart.plusDays(i);
                if (date.isAfter(end)) {
                    week.add(new Cell(null, 0, 0, 0)); // 아직 오지 않은 날 — 빈 칸
                    continue;
                }
                DayCount count = byDate.get(date);
                int events = count == null ? 0 : count.events();
                int points = count == null ? 0 : count.points();
                totalEvents += events;
                week.add(new Cell(date, events, points, level(events, maxEvents)));
            }
            weeks.add(week);
            // 그 주에 달이 바뀌면 열 위에 달 이름을 적는다
            monthLabels.add(weekStart.getDayOfMonth() <= 7 ? weekStart.getMonthValue() + "월" : "");
        }

        List<Entry> timeline = new ArrayList<>();
        for (ActivityRow row : activityDao.findRecent(userId, TIMELINE_LIMIT)) {
            timeline.add(toEntry(row));
        }

        return new History(weeks, monthLabels, activityDao.countActiveDays(userId, firstMonday), totalEvents,
                firstMonday.format(DAY) + " ~ " + end.format(DAY), timeline);
    }

    /** 가장 많이 한 날을 4로 놓고 1~4단계로 나눈다. 활동이 없으면 0. */
    static int level(int events, int maxEvents) {
        if (events <= 0) {
            return 0;
        }
        if (maxEvents <= 1) {
            return 4;
        }
        return Math.max(1, Math.min(4, (int) Math.ceil(4.0 * events / maxEvents)));
    }

    static Entry toEntry(ActivityRow row) {
        String label = switch (row.signalType() == null ? "" : row.signalType()) {
            case "ROADMAP" -> "로드맵 " + stepTypeLabel(row.stepType());
            case "PROBLEM" -> "문제 풀이";
            case "STREAK" -> "연속 기록";
            case "DOCUMENT" -> "서류 등록";
            case "QUIZ" -> "퀴즈";
            case "SELF_CHECK" -> "자가 진단";
            default -> row.signalType();
        };
        // 로드맵 단계 설명은 길어서 앞부분만 — 면접관이 "무엇을 했는지" 알 정도면 된다
        String detail = "ROADMAP".equals(row.signalType()) ? GlanceService.shortReason(row.reason()) : null;
        String stamp = row.earnedAt() == null ? "" : row.earnedAt().format(STAMP);
        return new Entry(stamp, label, detail == null || detail.isBlank() ? null : detail, row.points());
    }

    private static String stepTypeLabel(String stepType) {
        return stepType == null ? "단계" : GlanceService.stepTypeLabel(stepType);
    }
}
