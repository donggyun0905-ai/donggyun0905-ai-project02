package com.specodyssey.service;

import com.specodyssey.dao.InsightDao;
import com.specodyssey.dao.InsightDao.GapCellRow;
import com.specodyssey.dao.InsightDao.PeerScoreRow;
import com.specodyssey.dao.InsightDao.TrendRow;
import com.specodyssey.dao.JobBenchmarkSpecDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dto.InsightViewDto;
import com.specodyssey.dto.InsightViewDto.BenchmarkTier;
import com.specodyssey.dto.InsightViewDto.HeatCell;
import com.specodyssey.dto.InsightViewDto.HeatRow;
import com.specodyssey.dto.InsightViewDto.HeatmapView;
import com.specodyssey.dto.InsightViewDto.Notice;
import com.specodyssey.dto.InsightViewDto.PeerView;
import com.specodyssey.dto.InsightViewDto.TrendSkill;
import com.specodyssey.dto.InsightViewDto.TrendView;
import com.specodyssey.dto.JobBenchmarkSpecDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.UserDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 데이터 인사이트 화면 계산. 관련 요구사항: FR-45 · 46 · 47 · 48
 * 조회는 DAO, 여기서는 묶고 비율을 내는 일만 한다. 계산 함수는 DB 없이 테스트할 수 있게 static으로 분리했다.
 */
public class InsightService {

    static final int TREND_MONTHS = 4;
    static final int TREND_TOP_SKILLS = 8;

    // JOB_REQUIRED_SKILL.required_level → 화면 열 순서. 값이 없는 행은 "미지정" 열로 모은다.
    private static final Map<String, String> LEVEL_LABELS = new LinkedHashMap<>();
    private static final String LEVEL_UNKNOWN = "미지정";

    // JOB_BENCHMARK_SPEC.tier → 화면 표시명
    private static final Map<String, String> TIER_LABELS = new LinkedHashMap<>();

    static {
        LEVEL_LABELS.put("BASIC", "기초");
        LEVEL_LABELS.put("INTERMEDIATE", "중급");
        LEVEL_LABELS.put("ADVANCED", "심화");

        TIER_LABELS.put("ENTRY", "입문");
        TIER_LABELS.put("CORE", "핵심");
        TIER_LABELS.put("ADVANCED", "심화");
        TIER_LABELS.put("EXPERT", "전문가");
    }

    private final InsightDao insightDao = new InsightDao();
    private final JobDao jobDao = new JobDao();
    private final JobBenchmarkSpecDao benchmarkDao = new JobBenchmarkSpecDao();

    /** 목표 직무가 없는 사용자는 호출하지 말 것 (서블릿에서 안내 화면으로 분기). */
    public InsightViewDto build(UserDto user) throws SQLException {
        Long jobId = user.getDesiredJobId();
        InsightViewDto view = new InsightViewDto();

        JobDto job = jobDao.findById(jobId);
        view.setJobName(job == null ? null : job.getJobName());

        // FR-45
        BigDecimal myScore = insightDao.findLatestScore(user.getId());
        List<PeerScoreRow> peers = isBlank(user.getMajor()) || isBlank(user.getGrade())
                ? Collections.emptyList()
                : insightDao.findLatestPeerScores(user.getMajor(), user.getGrade());
        view.setPeer(comparePeers(user.getId(), user.getMajor(), user.getGrade(), myScore, peers));

        // FR-47
        view.setTrend(buildTrend(insightDao.findRecentTrend(jobId, TREND_MONTHS), TREND_TOP_SKILLS));

        // FR-46
        view.setBenchmark(groupBenchmark(benchmarkDao.findByJobId(jobId)));

        // FR-48
        HeatmapView heatmap = buildHeatmap(insightDao.findLatestGapCells(user.getId(), jobId));
        boolean noSkills = insightDao.countUserSkills(user.getId()) == 0;
        view.setHeatmap(heatmap.withNoOwnedSkills(noSkills && !heatmap.rows().isEmpty()));

        view.setNotices(buildNotices(view, noSkills));
        return view;
    }

    // FR-45 같은 전공·학년의 다른 사용자 최신 점수 평균과 내 최신 점수를 비교한다 (본인은 평균에서 제외).
    static PeerView comparePeers(Long userId, String major, String grade, BigDecimal myScore,
                                 List<PeerScoreRow> peers) {
        if (isBlank(major) || isBlank(grade)) {
            return new PeerView(major, grade, toInt(myScore), null, 0,
                    "프로필에 전공과 학년을 입력하면 같은 전공·학년 평균과 비교할 수 있습니다.");
        }
        // 0점은 프로필이 비어 있다는 뜻이라 비교해도 의미가 없다
        if (myScore == null || myScore.signum() <= 0) {
            return new PeerView(major, grade, null, null, 0,
                    "아직 스펙 완성도 점수가 없습니다. 프로필에 자격증·프로젝트·기술을 채우면 비교할 수 있습니다.");
        }

        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (PeerScoreRow peer : peers) {
            if (peer.userId() == userId || peer.score() == null) {
                continue;
            }
            sum = sum.add(peer.score());
            count++;
        }
        if (count == 0) {
            return new PeerView(major, grade, toInt(myScore), null, 0,
                    "아직 같은 전공·학년 사용자가 없어 비교할 수 없습니다.");
        }

        int mine = toInt(myScore);
        int average = sum.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).intValue();
        int diff = mine - average;
        String message = diff > 0 ? "평균보다 " + diff + "점 높습니다."
                : diff < 0 ? "평균보다 " + (-diff) + "점 낮습니다."
                : "평균과 같습니다.";
        return new PeerView(major, grade, mine, average, count, message);
    }

    // FR-47 최신 달 기준 언급 비율 상위 N개 기술의 월별 추이. rows는 period_ym 오름차순.
    static TrendView buildTrend(List<TrendRow> rows, int topN) {
        List<String> periods = new ArrayList<>();
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Map<String, Integer>> ratios = new LinkedHashMap<>();
        for (TrendRow row : rows) {
            if (!periods.contains(row.periodYm())) {
                periods.add(row.periodYm());
            }
            names.putIfAbsent(row.skillId(), row.skillName());
            ratios.computeIfAbsent(row.skillId(), k -> new TreeMap<>())
                    .put(row.periodYm(), toInt(row.mentionRatio()));
        }
        Collections.sort(periods);
        if (periods.isEmpty()) {
            return new TrendView(Collections.emptyList(), Collections.emptyList());
        }

        String latest = periods.get(periods.size() - 1);
        String previous = periods.size() >= 2 ? periods.get(periods.size() - 2) : null;

        List<Long> ranked = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Integer>> e : ratios.entrySet()) {
            if (e.getValue().get(latest) != null) {
                ranked.add(e.getKey());
            }
        }
        ranked.sort((a, b) -> Integer.compare(ratios.get(b).get(latest), ratios.get(a).get(latest)));

        List<TrendSkill> skills = new ArrayList<>();
        for (Long skillId : ranked.subList(0, Math.min(topN, ranked.size()))) {
            Map<String, Integer> byMonth = ratios.get(skillId);
            List<Integer> series = new ArrayList<>();
            for (String p : periods) {
                series.add(byMonth.get(p));
            }
            Integer before = previous == null ? null : byMonth.get(previous);
            Integer change = before == null ? null : byMonth.get(latest) - before;
            skills.add(new TrendSkill(names.get(skillId), series, change));
        }

        List<String> months = new ArrayList<>();
        for (String p : periods) {
            months.add(formatMonth(p));
        }
        return new TrendView(months, skills);
    }

    // FR-46 ENTRY → EXPERT 순으로 묶는다. 항목이 없는 단계는 빼고, 전부 없으면 빈 목록.
    static List<BenchmarkTier> groupBenchmark(List<JobBenchmarkSpecDto> specs) {
        Map<String, List<String>> byTier = new LinkedHashMap<>();
        for (String tier : TIER_LABELS.keySet()) {
            byTier.put(tier, new ArrayList<>());
        }
        for (JobBenchmarkSpecDto spec : specs) {
            List<String> items = byTier.get(spec.getTier());
            if (items != null) {
                items.add(spec.getContent());
            }
        }
        List<BenchmarkTier> tiers = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byTier.entrySet()) {
            if (!e.getValue().isEmpty()) {
                tiers.add(new BenchmarkTier(e.getKey(), TIER_LABELS.get(e.getKey()), e.getValue()));
            }
        }
        return tiers;
    }

    // FR-48 기술 분야(SKILL.category) × 요구 수준(required_level)별 부족 기술 수.
    static HeatmapView buildHeatmap(List<GapCellRow> rows) {
        if (rows.isEmpty()) {
            return new HeatmapView(Collections.emptyList(), Collections.emptyList(), 0, null, false);
        }

        List<String> levelKeys = new ArrayList<>(LEVEL_LABELS.keySet());
        boolean hasUnknown = false;
        for (GapCellRow row : rows) {
            if (!LEVEL_LABELS.containsKey(row.requiredLevel())) {
                hasUnknown = true;
                break;
            }
        }
        if (hasUnknown) {
            levelKeys.add(LEVEL_UNKNOWN);
        }

        // 분야 → 수준 → {부족, 전체}
        Map<String, Map<String, int[]>> counts = new TreeMap<>();
        for (GapCellRow row : rows) {
            String category = isBlank(row.category()) ? "기타" : row.category();
            String level = LEVEL_LABELS.containsKey(row.requiredLevel()) ? row.requiredLevel() : LEVEL_UNKNOWN;
            int[] cell = counts.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .computeIfAbsent(level, k -> new int[2]);
            if (row.missing()) {
                cell[0]++;
            }
            cell[1]++;
        }

        int maxMissing = 0;
        String weakest = null;
        int missingTotal = 0;
        for (Map.Entry<String, Map<String, int[]>> e : counts.entrySet()) {
            for (String level : levelKeys) {
                int[] cell = e.getValue().get(level);
                if (cell == null) {
                    continue;
                }
                missingTotal += cell[0];
                if (cell[0] > maxMissing) {
                    maxMissing = cell[0];
                    weakest = e.getKey() + " · " + labelOf(level);
                }
            }
        }

        List<HeatRow> heatRows = new ArrayList<>();
        for (Map.Entry<String, Map<String, int[]>> e : counts.entrySet()) {
            List<HeatCell> cells = new ArrayList<>();
            for (String level : levelKeys) {
                int[] cell = e.getValue().getOrDefault(level, new int[2]);
                cells.add(new HeatCell(cell[0], cell[1], shade(cell[0], maxMissing)));
            }
            heatRows.add(new HeatRow(e.getKey(), cells));
        }

        List<String> levelLabels = new ArrayList<>();
        for (String level : levelKeys) {
            levelLabels.add(labelOf(level));
        }
        return new HeatmapView(levelLabels, heatRows, missingTotal, weakest, false);
    }

    // 데이터가 부족한 카드마다 "먼저 해 볼 일"을 한 줄씩 만든다. 사용자가 직접 채울 수 없는 것(공고 집계·참고 루트)은 뺀다.
    static List<Notice> buildNotices(InsightViewDto view, boolean noSkills) {
        List<Notice> notices = new ArrayList<>();
        PeerView peer = view.getPeer();
        if (isBlank(peer.major()) || isBlank(peer.grade())) {
            notices.add(new Notice("프로필에 전공과 학년을 입력하면 또래 비교가 열립니다.", "/profile", "프로필 입력"));
        } else if (peer.myScore() == null) {
            notices.add(new Notice("프로필에 자격증·프로젝트·기술을 채우면 스펙 완성도 점수가 생깁니다.", "/profile", "프로필 채우기"));
        }
        if (noSkills) {
            notices.add(new Notice("보유 기술을 입력해야 약점 히트맵이 정확해집니다.", "/profile", "기술 입력"));
        }
        if (view.getHeatmap().rows().isEmpty()) {
            notices.add(new Notice("목표 직무로 격차 분석을 한 번 실행하면 약점 히트맵이 생깁니다.", "/gap-analysis", "격차 분석"));
        } else if (noSkills) {
            notices.add(new Notice("기술을 입력한 뒤 격차 분석을 다시 실행하세요.", "/gap-analysis", "격차 분석"));
        }
        return notices;
    }

    // 가장 많이 부족한 칸을 3으로 놓고 1~3단계로 나눈다. 부족이 없으면 0.
    static int shade(int missing, int maxMissing) {
        if (missing <= 0 || maxMissing <= 0) {
            return 0;
        }
        return (int) Math.ceil(3.0 * missing / maxMissing);
    }

    private static String labelOf(String level) {
        return LEVEL_LABELS.getOrDefault(level, LEVEL_UNKNOWN);
    }

    // "202609" → "9월"
    static String formatMonth(String periodYm) {
        if (periodYm == null || periodYm.length() != 6) {
            return periodYm;
        }
        return Integer.parseInt(periodYm.substring(4)) + "월";
    }

    private static Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
