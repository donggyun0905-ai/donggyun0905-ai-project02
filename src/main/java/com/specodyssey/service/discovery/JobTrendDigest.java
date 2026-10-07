package com.specodyssey.service.discovery;

import com.specodyssey.dao.InsightDao.TrendRow;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 직무 요약(FR-35) 전망의 근거 — 최근 공고에서 많이 찾는 기술과 지난달보다 늘어난 기술. 관련: FR-35, FR-47
 *
 * JOB_SKILL_TREND 최근 2개월(InsightDao.findRecentTrend)을 받아 계산만 한다(DB 없이 테스트 가능).
 * 화면에는 이 숫자를 그대로 보여 주고, LLM에는 기술 이름만 근거로 준다(전망 문장에 수치 금지는 그대로).
 *
 * 공고가 적은 달은 비율이 크게 튄다(인사이트 화면과 같은 문제). 그래서 그 달에 언급된 기술이
 * MIN_SKILLS_PER_MONTH개 미만이면 근거로 쓰지 않는다 — 2026-10-07 기준 UI 개발자(0건)·IT 기획자(2건) 등.
 */
public final class JobTrendDigest {

    static final int MIN_SKILLS_PER_MONTH = 5;
    static final int TOP_HOT = 3;
    static final int TOP_RISING = 3;
    /** 지난달보다 언급 비율이 이만큼(%p) 이상 늘어야 "늘어난 기술"로 본다 */
    static final int RISING_MIN_POINTS = 5;

    private final String month;              // "9월"
    private final List<String> hot;          // 최신 달 언급 비율 상위
    private final List<Rising> rising;       // 지난달 대비 늘어난 기술

    private JobTrendDigest() { // Gson이 summary_json을 읽을 때
        this(null, List.of(), List.of());
    }

    private JobTrendDigest(String month, List<String> hot, List<Rising> rising) {
        this.month = month;
        this.hot = hot;
        this.rising = rising;
    }

    /** 늘어난 기술 하나 — change는 %p (정수). EL이 getter로 읽으므로 클래스로 둔다. */
    public static class Rising {
        private String name;
        private int change;

        Rising() {
        }

        Rising(String name, int change) {
            this.name = name;
            this.change = change;
        }

        public String getName() { return name; }
        public int getChange() { return change; }
    }

    /** rows: period_ym 오름차순(최근 2개월). 근거로 쓰기에 데이터가 부족하면 null. */
    public static JobTrendDigest from(List<TrendRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        TreeSet<String> periods = new TreeSet<>();
        for (TrendRow r : rows) {
            periods.add(r.periodYm());
        }
        String latest = periods.last();
        String previous = periods.lower(latest);

        Map<String, Integer> now = ratios(rows, latest);
        if (now.size() < MIN_SKILLS_PER_MONTH) {
            return null;
        }
        List<String> hot = new ArrayList<>();
        now.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_HOT)
                .forEach(e -> hot.add(e.getKey()));

        List<Rising> rising = new ArrayList<>();
        Map<String, Integer> before = previous == null ? Map.of() : ratios(rows, previous);
        // 지난달 데이터도 충분할 때만 비교한다 — 한쪽이 빈약하면 "늘었다"가 착시가 된다
        if (before.size() >= MIN_SKILLS_PER_MONTH) {
            List<Rising> candidates = new ArrayList<>();
            for (Map.Entry<String, Integer> e : now.entrySet()) {
                Integer old = before.get(e.getKey());
                if (old != null && e.getValue() - old >= RISING_MIN_POINTS) {
                    candidates.add(new Rising(e.getKey(), e.getValue() - old));
                }
            }
            candidates.sort(Comparator.comparingInt(Rising::getChange).reversed().thenComparing(Rising::getName));
            rising.addAll(candidates.subList(0, Math.min(TOP_RISING, candidates.size())));
        }
        return new JobTrendDigest(formatMonth(latest), hot, rising);
    }

    private static Map<String, Integer> ratios(List<TrendRow> rows, String period) {
        Map<String, Integer> out = new HashMap<>();
        for (TrendRow r : rows) {
            if (period.equals(r.periodYm()) && r.mentionRatio() != null) {
                int v = r.mentionRatio().setScale(0, RoundingMode.HALF_UP).intValue();
                out.merge(r.skillName(), v, Math::max);
            }
        }
        return out;
    }

    public String getMonth() { return month; }
    public List<String> getHot() { return hot == null ? List.of() : hot; }
    public List<Rising> getRising() { return rising == null ? List.of() : rising; }

    /** LLM 프롬프트에 넣을 근거 한 줄 (기술 이름만, 수치 없음) */
    String promptLine() {
        StringBuilder sb = new StringBuilder("최근 공고에서 많이 찾는 기술: ").append(String.join(", ", hot));
        if (!rising.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Rising r : rising) {
                names.add(r.getName());
            }
            sb.append(" / 지난달보다 언급이 늘어난 기술: ").append(String.join(", ", names));
        }
        return sb.toString();
    }

    /** "202609" → "9월" — InsightService.formatMonth와 같은 규칙 */
    static String formatMonth(String periodYm) {
        if (periodYm == null || periodYm.length() != 6) {
            return periodYm;
        }
        return Integer.parseInt(periodYm.substring(4)) + "월";
    }
}
