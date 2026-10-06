package com.specodyssey.service;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.TrendCollectDao;
import com.specodyssey.dao.TrendTechDao;
import com.specodyssey.dao.TrendTechJobDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.TrendTechDto;
import com.specodyssey.dto.TrendTechJobDto;
import com.specodyssey.service.TrendLlmService.TrendItem;
import com.specodyssey.service.TrendSourceClient.Candidate;
import com.specodyssey.util.AppConfig;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.TransactionUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 트렌드 기술 수집 서비스 — 외부 소스에서 후보를 모으고 LLM으로 정리해 TREND_TECH/TREND_TECH_JOB에 저장한다.
 * 관련 요구사항: FR-54 트렌드 기술 노출, FR-55 직무 연관 필터링
 *
 * 흐름: 수집(Hacker News, GitHub, dev.to, Lobsters, GeekNews) → LLM 정리(기술명·한 줄 설명·관련 직무)
 *     → 직군별 보충 → 직군 몫을 보장해 고르기 → 한 트랜잭션으로 저장.
 * 일반 소스는 개발자 커뮤니티라 기획(PM) 같은 직군은 기술이 거의 안 잡힌다. 그래서 일반 정리 결과에서 기술이
 * MIN_PER_CATEGORY개 미만인 직군(JOB.job_category)만 그 직군 태그 글을 더 모아 직군 전용 프롬프트로 한 번 더 정리한다.
 * 저장 상한은 직군마다 최소 몫을 먼저 채운 뒤 남는 자리를 직군을 돌아가며 채우고(직군당 MAX_PER_CATEGORY까지), 한 직군이 상한을 다 차지하지 못하게 한다.
 * 소스 하나가 실패하면 그 소스만, 직군 보충이 실패하면 그 직군만 건너뛴다. 일반 소스가 전부 실패했거나 일반 LLM 정리가
 * 실패하면 아무것도 저장하지 않아 직전 데이터가 유지된다.
 * 관련 직무가 하나도 없는 기술은 어떤 사용자에게도 노출되지 않으므로 저장하지 않는다.
 * SKILL 테이블에 없는 신기술도 LLM이 기술명을 뽑기 때문에 그대로 다룬다.
 */
public class TrendCollectService {

    private static final Logger LOG = Logger.getLogger(TrendCollectService.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_SAVED_PER_RUN = 30;
    // 직군마다 하루에 최소 이만큼은 보이도록 보충·자리 보장을 한다 (직군 6개 × 3 = 18 ≤ 30)
    static final int MIN_PER_CATEGORY = 3;
    // 한 직군이 하루 저장분을 다 차지하지 못하게 하는 상한 (30개를 6개 직군이 나눠 쓰는 정도)
    static final int MAX_PER_CATEGORY = 8;
    private static final int REPEAT_BLOCK_DAYS = 7;
    // 애매한 기술을 거르는 확신도 기준 — 공고에 실제로 쓰이는 기술 사전(SKILL)에 있으면 완화, 없는 신기술은 더 엄격하게
    private static final BigDecimal MIN_CONFIDENCE_KNOWN_SKILL = new BigDecimal("0.8");
    private static final BigDecimal MIN_CONFIDENCE_NEW_TECH = new BigDecimal("0.9");

    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();
    private final TrendTechDao trendTechDao = new TrendTechDao();
    private final TrendTechJobDao trendTechJobDao = new TrendTechJobDao();
    private final TrendCollectDao trendCollectDao = new TrendCollectDao();
    private final TrendSourceClient sourceClient = new TrendSourceClient();
    private final TrendLlmService llmService = new TrendLlmService();

    /** 저장 후보 1건 — 일반·보충 수집의 후보 목록이 서로 달라 번호 대신 출처 글을 직접 묶어 둔다. */
    record Pick(Candidate source, TrendItem item) {
    }

    /** 오늘 아직 수집하지 않았을 때만 실행한다. 저장한 건수를 돌려주고, 이미 수집했으면 0. */
    public int collectIfNotYetToday() throws SQLException, ExternalApiException {
        boolean done;
        try (Connection conn = DBUtil.getConnection()) {
            done = trendCollectDao.existsCollectedSince(conn, LocalDate.now(ZONE).atStartOfDay());
        }
        return done ? 0 : collect();
    }

    /** 수집 → LLM 정리 → 직군별 보충 → 저장을 한 번 실행한다. 저장한 TREND_TECH 건수를 돌려준다. */
    public int collect() throws SQLException, ExternalApiException {
        List<JobDto> jobs = jobDao.findAll();
        Map<Long, String> categoryByJob = new HashMap<>();
        for (JobDto job : jobs) {
            categoryByJob.put(job.getId(), job.getJobCategory());
        }
        LocalDateTime repeatSince = LocalDateTime.now(ZONE).minusDays(REPEAT_BLOCK_DAYS);

        List<Candidate> candidates = fetchCandidates(sourceClient.sources());
        if (candidates.isEmpty()) {
            throw new ExternalApiException("모든 트렌드 소스 수집에 실패했습니다", null);
        }
        List<TrendItem> organized = llmService.organize(candidates.stream().map(Candidate::text).toList(), jobs);
        List<Pick> picks = new ArrayList<>();
        addFresh(picks, candidates, dropAmbiguous(organized), repeatSince);
        int generalCount = picks.size();

        // FR-55: 일반 소스로 기술이 모자란 직군만 보충한다. 보충 실패는 그 직군만 건너뛴다.
        List<String> categories = jobs.stream().map(JobDto::getJobCategory).filter(Objects::nonNull).distinct().toList();
        for (String category : shortCategories(picks, categoryByJob, categories)) {
            try {
                supplement(picks, category, jobs, repeatSince);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "트렌드 직군 보충 실패 — " + category + " 직군을 건너뜁니다", e);
            }
        }

        // LLM 정리까지 끝난 뒤에 저장한다 — 여기까지 예외가 나면 DB는 그대로다
        List<Pick> selected = selectWithQuota(picks, categoryByJob, MIN_PER_CATEGORY, MAX_PER_CATEGORY, MAX_SAVED_PER_RUN);
        int saved = TransactionUtil.runInTransaction(conn -> {
            int count = 0;
            for (Pick pick : selected) {
                // 고른 뒤 저장 전까지 다른 실행이 같은 기술을 넣었을 수 있어 한 번 더 확인한다
                if (!isFresh(conn, pick.source().url(), pick.item().techName(), repeatSince)) {
                    continue;
                }
                TrendTechDto tech = new TrendTechDto();
                tech.setTechName(pick.item().techName());
                tech.setSummary(pick.item().summary());
                tech.setSourceUrl(pick.source().url());
                tech.setPublishedAt(pick.source().publishedAt());
                Long techId = trendTechDao.insert(conn, tech);

                for (Map.Entry<Long, BigDecimal> job : pick.item().jobRelevance().entrySet()) {
                    TrendTechJobDto link = new TrendTechJobDto();
                    link.setTrendTechId(techId);
                    link.setJobId(job.getKey());
                    link.setRelevanceScore(job.getValue());
                    trendTechJobDao.insert(conn, link);
                }
                count++;
            }
            return count;
        });
        LOG.info("트렌드 기술 수집 완료: 후보 " + candidates.size() + "건, LLM 선별 " + organized.size()
                + "건, 일반 통과 " + generalCount + "건, 보충 " + (picks.size() - generalCount) + "건, 저장 " + saved + "건");
        return saved;
    }

    // 직군 하나 보충 — 그 직군 태그 글을 모아, 그 직군 직무만 넘겨 직군 전용 프롬프트로 정리한다
    private void supplement(List<Pick> picks, String category, List<JobDto> jobs, LocalDateTime repeatSince)
            throws SQLException, ExternalApiException {
        List<TrendSourceClient.Source> sources = sourceClient.categorySources(category);
        if (sources.isEmpty()) {
            return;
        }
        List<Candidate> candidates = fetchCandidates(sources);
        if (candidates.isEmpty()) {
            throw new ExternalApiException(category + " 직군 보충 소스 수집에 모두 실패했습니다", null);
        }
        List<JobDto> categoryJobs = jobs.stream().filter(j -> category.equals(j.getJobCategory())).toList();
        List<TrendItem> organized = llmService.organize(
                candidates.stream().map(Candidate::text).toList(), categoryJobs, category);
        int before = picks.size();
        addFresh(picks, candidates, dropAmbiguous(organized), repeatSince);
        LOG.info("트렌드 직군 보충: " + category + " 후보 " + candidates.size() + "건, 추가 " + (picks.size() - before) + "건");
    }

    // 저장할 만한 것만 picks에 더한다 — 연결 직무 없음(FR-55), 이번 실행에서 이미 고른 기술·출처, DB에 있는 중복은 뺀다
    private void addFresh(List<Pick> picks, List<Candidate> candidates, List<TrendItem> items,
                          LocalDateTime repeatSince) throws SQLException {
        Set<String> seenNames = new HashSet<>();
        Set<String> seenUrls = new HashSet<>();
        for (Pick p : picks) {
            seenNames.add(p.item().techName().toLowerCase());
            seenUrls.add(p.source().url());
        }
        try (Connection conn = DBUtil.getConnection()) {
            for (TrendItem item : items) {
                Candidate c = candidates.get(item.candidateIndex());
                if (item.jobRelevance().isEmpty()
                        || seenNames.contains(item.techName().toLowerCase()) || seenUrls.contains(c.url())
                        || !isFresh(conn, c.url(), item.techName(), repeatSince)) {
                    continue;
                }
                seenNames.add(item.techName().toLowerCase());
                seenUrls.add(c.url());
                picks.add(new Pick(c, item));
            }
        }
    }

    // TREND_TECH에는 UNIQUE가 없어 중복(같은 출처·최근 같은 기술)을 직접 막는다
    private boolean isFresh(Connection conn, String url, String techName, LocalDateTime repeatSince)
            throws SQLException {
        return !trendCollectDao.existsBySourceUrl(conn, url)
                && !trendCollectDao.existsByTechNameSince(conn, techName, repeatSince);
    }

    /** 고른 기술이 MIN_PER_CATEGORY개 미만인 직군 목록 (categories 순서 유지). */
    static List<String> shortCategories(List<Pick> picks, Map<Long, String> categoryByJob, List<String> categories) {
        Map<String, Integer> counts = new HashMap<>();
        for (Pick pick : picks) {
            for (String category : categoriesOf(pick, categoryByJob)) {
                counts.merge(category, 1, Integer::sum);
            }
        }
        return categories.stream().filter(c -> counts.getOrDefault(c, 0) < MIN_PER_CATEGORY).toList();
    }

    /**
     * 저장 상한 안에서 직군 몫을 보장해 고른다.
     * 1차: 아직 몫(minPerCategory)을 못 채운 직군에 연결된 기술을 순서대로 고른다.
     * 2차: 남은 자리를 직군을 돌아가며 하나씩 채운다. 연결된 직군이 하나라도 maxPerCategory에 닿은 기술은 고르지 않는다
     *     (보충분은 직군별로 몰려 들어와서, 순서대로 채우면 한 직군이 남은 자리를 다 가져간다).
     * 결과는 원래 순서를 유지한다.
     */
    static List<Pick> selectWithQuota(List<Pick> picks, Map<Long, String> categoryByJob,
                                      int minPerCategory, int maxPerCategory, int max) {
        boolean[] chosen = new boolean[picks.size()];
        int total = 0;
        Map<String, Integer> counts = new HashMap<>();
        Set<String> order = new LinkedHashSet<>();
        for (int i = 0; i < picks.size(); i++) {
            Set<String> categories = categoriesOf(picks.get(i), categoryByJob);
            order.addAll(categories);
            if (total < max && categories.stream().anyMatch(c -> counts.getOrDefault(c, 0) < minPerCategory)) {
                chosen[i] = true;
                total++;
                categories.forEach(c -> counts.merge(c, 1, Integer::sum));
            }
        }
        boolean progressed = true;
        while (total < max && progressed) {
            progressed = false;
            for (String category : order) {
                if (total >= max) {
                    break;
                }
                for (int i = 0; i < picks.size(); i++) {
                    Set<String> categories = categoriesOf(picks.get(i), categoryByJob);
                    if (!chosen[i] && categories.contains(category)
                            && categories.stream().allMatch(c -> counts.getOrDefault(c, 0) < maxPerCategory)) {
                        chosen[i] = true;
                        total++;
                        categories.forEach(c -> counts.merge(c, 1, Integer::sum));
                        progressed = true;
                        break;
                    }
                }
            }
        }
        List<Pick> result = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) {
            if (chosen[i]) {
                result.add(picks.get(i));
            }
        }
        return result;
    }

    private static Set<String> categoriesOf(Pick pick, Map<Long, String> categoryByJob) {
        Set<String> result = new HashSet<>();
        for (Long jobId : pick.item().jobRelevance().keySet()) {
            String category = categoryByJob.get(jobId);
            if (category != null) {
                result.add(category);
            }
        }
        return result;
    }

    // 애매한 기술 거르기 — 제외 목록(TREND_EXCLUDE_TECHS, 쉼표 구분)과 LLM 확신도 기준. 버린 것은 조정할 수 있게 로그에 남긴다.
    private List<TrendItem> dropAmbiguous(List<TrendItem> organized) throws SQLException {
        Set<String> knownSkills = skillDao.findAll().stream()
                .map(SkillDto::getSkillName).map(String::toLowerCase).collect(Collectors.toSet());
        String excludeConfig = AppConfig.get("TREND_EXCLUDE_TECHS");
        Set<String> excluded = excludeConfig == null ? Set.of()
                : Arrays.stream(excludeConfig.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .map(String::toLowerCase).collect(Collectors.toSet());

        List<TrendItem> kept = new ArrayList<>();
        for (TrendItem item : organized) {
            String name = item.techName().toLowerCase();
            BigDecimal min = knownSkills.contains(name) ? MIN_CONFIDENCE_KNOWN_SKILL : MIN_CONFIDENCE_NEW_TECH;
            if (excluded.contains(name) || item.confidence().compareTo(min) < 0) {
                LOG.info("트렌드 기술 제외(애매함): " + item.techName() + " (확신도 " + item.confidence() + ", 기준 " + min + ")");
            } else {
                kept.add(item);
            }
        }
        return kept;
    }

    // 소스별로 실패를 격리한다 — 한 곳이 죽어도 나머지 후보로 진행한다. 전부 죽었는지는 호출부가 빈 목록으로 판단한다.
    private List<Candidate> fetchCandidates(List<TrendSourceClient.Source> sources) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (TrendSourceClient.Source source : sources) {
            try {
                int added = 0;
                for (Candidate c : source.fetch()) {
                    if (seenUrls.add(c.url())) { // 여러 소스에 같은 글이 올라오면 한 번만
                        candidates.add(c);
                        added++;
                    }
                }
                LOG.info("트렌드 후보 수집: " + source.name() + " " + added + "건");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "트렌드 후보 수집 실패 — " + source.name() + " 소스를 건너뜁니다", e);
            }
        }
        return candidates;
    }
}
