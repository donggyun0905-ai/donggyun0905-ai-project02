package com.specodyssey.service;

import com.specodyssey.dto.SpecScoreHistoryDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * FR-84 면접관 뷰 "성장 잠재력" 그래프 — 스펙 완성도 기록을 주·월·년 단위로 묶는다.
 *
 * 최근 몇 개 기록만 0~100 막대로 그리면 하루하루 차이가 작아 변화가 안 보인다(2026-10-08 피드백).
 * 그래서 기간마다 그 기간의 마지막 값을 막대 하나로 두고, 세로축은 0이 아니라 기록의 최저~최고 근처만
 * 잘라 보여 준다(축 범위는 화면에 함께 표시). 막대마다 직전 기간 대비 증감도 붙인다.
 */
public final class GrowthChart {

    static final int MAX_WEEKS = 12;
    static final int MAX_MONTHS = 12;
    static final int MAX_YEARS = 5;

    private GrowthChart() {
    }

    /** 막대 하나 = 한 기간의 마지막 완성도 */
    public record Bar(String label, String scoreText, String deltaText, boolean down, int heightPercent) {
        // JSP의 EL이 읽을 수 있게 getter를 같이 둔다 — Tomcat 10.1(BeanELResolver)은 getX()만, Tomcat 11(RecordELResolver)은 x()만 찾는다.
        public String getLabel() {
            return label;
        }

        public String getScoreText() {
            return scoreText;
        }

        public String getDeltaText() {
            return deltaText;
        }

        public boolean isDown() {
            return down;
        }

        public int getHeightPercent() {
            return heightPercent;
        }
    }

    /** 주·월·년 중 하나 — key는 탭 id(week/month/year) */
    public record Period(String key, String name, String changeText, boolean down, List<Bar> bars) {
        public String getKey() {
            return key;
        }

        public String getName() {
            return name;
        }

        public String getChangeText() {
            return changeText;
        }

        public boolean isDown() {
            return down;
        }

        public List<Bar> getBars() {
            return bars;
        }
    }

    /** 세 기간이 같은 세로축(axisMin~axisMax)을 쓴다 — 탭을 바꿔도 높이를 그대로 비교할 수 있게 */
    public record Chart(List<Period> periods, int axisMin, int axisMax, String summaryText) {
        public List<Period> getPeriods() {
            return periods;
        }

        public int getAxisMin() {
            return axisMin;
        }

        public int getAxisMax() {
            return axisMax;
        }

        public String getSummaryText() {
            return summaryText;
        }
    }

    /**
     * @param history snapshot_date 오름차순 (SpecScoreHistoryDao.findByUserId 순서)
     * @return 기록이 없으면 null
     */
    public static Chart build(List<SpecScoreHistoryDto> history) {
        List<SpecScoreHistoryDto> rows = history == null ? List.of() : history.stream()
                .filter(h -> h.getSnapshotDate() != null && h.getCompletenessScore() != null)
                .toList();
        if (rows.isEmpty()) {
            return null;
        }
        boolean oneYear = rows.get(0).getSnapshotDate().getYear() == rows.get(rows.size() - 1).getSnapshotDate().getYear();

        List<Bucket> weeks = lastN(bucket(rows, d -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                d -> monthDay(d) + "~" + monthDay(d.plusDays(6))), MAX_WEEKS);
        List<Bucket> months = lastN(bucket(rows, d -> d.withDayOfMonth(1),
                d -> oneYear ? d.getMonthValue() + "월" : (d.getYear() % 100) + "." + d.getMonthValue() + "월"), MAX_MONTHS);
        List<Bucket> years = lastN(bucket(rows, d -> d.withDayOfYear(1), d -> d.getYear() + "년"), MAX_YEARS);

        // 세로축 — 보여 줄 값(각 기간의 첫 값·마지막 값)의 최저~최고를 10점 단위로 넓혀 자른다
        BigDecimal min = rows.get(rows.size() - 1).getCompletenessScore();
        BigDecimal max = min;
        for (List<Bucket> list : List.of(weeks, months, years)) {
            for (Bucket b : list) {
                min = min.min(b.first).min(b.last);
                max = max.max(b.first).max(b.last);
            }
        }
        int axisMin = Math.max(0, (int) Math.floor((min.doubleValue() - 5) / 10) * 10);
        int axisMax = Math.min(100, (int) Math.ceil((max.doubleValue() + 5) / 10) * 10);
        if (axisMax <= axisMin) {
            axisMax = Math.min(100, axisMin + 10);
            axisMin = axisMax - 10;
        }

        List<Period> periods = List.of(
                period("week", "주", "주", MAX_WEEKS, weeks, axisMin, axisMax),
                period("month", "월", "개월", MAX_MONTHS, months, axisMin, axisMax),
                period("year", "년", "년", MAX_YEARS, years, axisMin, axisMax));

        SpecScoreHistoryDto first = rows.get(0);
        SpecScoreHistoryDto last = rows.get(rows.size() - 1);
        long days = ChronoUnit.DAYS.between(first.getSnapshotDate(), last.getSnapshotDate());
        String summary = days == 0
                ? "기록 시작 " + first.getSnapshotDate() + " · 완성도 " + score(last.getCompletenessScore()) + "점"
                : "기록 시작 " + first.getSnapshotDate() + "부터 " + days + "일 동안 "
                  + score(first.getCompletenessScore()) + "점 → " + score(last.getCompletenessScore()) + "점 ("
                  + delta(last.getCompletenessScore().subtract(first.getCompletenessScore())) + "점)";
        return new Chart(periods, axisMin, axisMax, summary);
    }

    // ----------------------------------------------------------------

    /** 한 기간의 첫 값·마지막 값 */
    private static final class Bucket {
        final String label;
        final BigDecimal first;
        BigDecimal last;

        Bucket(String label, BigDecimal first) {
            this.label = label;
            this.first = first;
            this.last = first;
        }
    }

    // 날짜 오름차순이라 같은 키에 덮어쓰면 그 기간의 마지막 값이 남는다
    private static List<Bucket> bucket(List<SpecScoreHistoryDto> rows, Function<LocalDate, LocalDate> keyOf,
                                       Function<LocalDate, String> labelOf) {
        Map<LocalDate, Bucket> buckets = new LinkedHashMap<>();
        for (SpecScoreHistoryDto row : rows) {
            LocalDate key = keyOf.apply(row.getSnapshotDate());
            Bucket b = buckets.get(key);
            if (b == null) {
                buckets.put(key, new Bucket(labelOf.apply(key), row.getCompletenessScore()));
            } else {
                b.last = row.getCompletenessScore();
            }
        }
        return new ArrayList<>(buckets.values());
    }

    // 증감은 잘리기 전 목록에서 계산해야 첫 막대도 직전 기간과 비교된다 — 그래서 직전 기간 하나를 남겨 둔다
    private static List<Bucket> lastN(List<Bucket> all, int n) {
        return all.subList(Math.max(0, all.size() - n - 1), all.size());
    }

    private static Period period(String key, String name, String unit, int max, List<Bucket> withPrev,
                                 int axisMin, int axisMax) {
        boolean hasPrev = withPrev.size() > max;
        List<Bucket> shown = hasPrev ? withPrev.subList(1, withPrev.size()) : withPrev;
        List<Bar> bars = new ArrayList<>();
        BigDecimal prev = hasPrev ? withPrev.get(0).last : null;
        for (Bucket b : shown) {
            // 직전 기간이 없는 첫 막대는 그 기간 안에서 오른 만큼 (예: 첫 달 안에 42 → 55면 +13)
            BigDecimal diff = prev != null ? b.last.subtract(prev)
                    : b.last.compareTo(b.first) != 0 ? b.last.subtract(b.first) : null;
            bars.add(new Bar(b.label, score(b.last), diff == null ? "" : delta(diff),
                    diff != null && diff.signum() < 0, height(b.last, axisMin, axisMax)));
            prev = b.last;
        }
        // 기간 전체 변화 — 보여 주는 첫 기간의 첫 기록부터 마지막 기록까지
        BigDecimal change = shown.get(shown.size() - 1).last.subtract(shown.get(0).first);
        String range = shown.size() == 1 ? shown.get(0).label : "최근 " + shown.size() + unit;
        return new Period(key, name, range + " 동안 " + delta(change) + "점", change.signum() < 0, bars);
    }

    // 주 이름 — 월요일~일요일 기간 그대로 (예: 8/31~9/6)
    private static String monthDay(LocalDate d) {
        return d.getMonthValue() + "/" + d.getDayOfMonth();
    }

    static int height(BigDecimal score, int axisMin, int axisMax) {
        double ratio = (score.doubleValue() - axisMin) / (axisMax - axisMin);
        return (int) Math.max(4, Math.min(100, Math.round(ratio * 100)));
    }

    static String score(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String delta(BigDecimal diff) {
        if (diff.signum() == 0) {
            return "±0";
        }
        return (diff.signum() > 0 ? "+" : "") + score(diff);
    }
}
