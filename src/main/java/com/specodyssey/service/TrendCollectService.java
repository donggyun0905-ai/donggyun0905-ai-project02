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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 트렌드 기술 수집 서비스 — 외부 소스에서 후보를 모으고 LLM으로 정리해 TREND_TECH/TREND_TECH_JOB에 저장한다.
 * 관련 요구사항: FR-54 트렌드 기술 노출, FR-55 직무 연관 필터링
 *
 * 흐름: 수집(Hacker News, GitHub, dev.to, Lobsters, GeekNews) → LLM 정리(기술명·한 줄 설명·관련 직무) → 한 트랜잭션으로 저장.
 * 소스 하나가 실패하면 그 소스만 건너뛴다. 전부 실패했거나 LLM 정리가 실패하면 아무것도 저장하지 않아 직전 데이터가 유지된다.
 * 관련 직무가 하나도 없는 기술은 어떤 사용자에게도 노출되지 않으므로 저장하지 않는다.
 * SKILL 테이블에 없는 신기술도 LLM이 기술명을 뽑기 때문에 그대로 다룬다.
 */
public class TrendCollectService {

    private static final Logger LOG = Logger.getLogger(TrendCollectService.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_SAVED_PER_RUN = 30;
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

    /** 오늘 아직 수집하지 않았을 때만 실행한다. 저장한 건수를 돌려주고, 이미 수집했으면 0. */
    public int collectIfNotYetToday() throws SQLException, ExternalApiException {
        boolean done;
        try (Connection conn = DBUtil.getConnection()) {
            done = trendCollectDao.existsCollectedSince(conn, LocalDate.now(ZONE).atStartOfDay());
        }
        return done ? 0 : collect();
    }

    /** 수집 → LLM 정리 → 저장을 한 번 실행한다. 저장한 TREND_TECH 건수를 돌려준다. */
    public int collect() throws SQLException, ExternalApiException {
        List<Candidate> candidates = fetchCandidates();

        List<JobDto> jobs = jobDao.findAll();
        List<TrendItem> organized = llmService.organize(candidates.stream().map(Candidate::text).toList(), jobs);
        List<TrendItem> items = dropAmbiguous(organized);

        // LLM 정리까지 끝난 뒤에 저장한다 — 여기까지 예외가 나면 DB는 그대로다
        LocalDateTime repeatSince = LocalDateTime.now(ZONE).minusDays(REPEAT_BLOCK_DAYS);
        int saved = TransactionUtil.runInTransaction(conn -> {
            int count = 0;
            for (TrendItem item : items) {
                if (count >= MAX_SAVED_PER_RUN) {
                    break;
                }
                Candidate c = candidates.get(item.candidateIndex());
                // FR-55: 연결된 직무가 없으면 노출될 곳이 없다. TREND_TECH에는 UNIQUE가 없어 중복(같은 출처·최근 같은 기술)도 직접 막는다.
                if (item.jobRelevance().isEmpty()
                        || trendCollectDao.existsBySourceUrl(conn, c.url())
                        || trendCollectDao.existsByTechNameSince(conn, item.techName(), repeatSince)) {
                    continue;
                }
                TrendTechDto tech = new TrendTechDto();
                tech.setTechName(item.techName());
                tech.setSummary(item.summary());
                tech.setSourceUrl(c.url());
                tech.setPublishedAt(c.publishedAt());
                Long techId = trendTechDao.insert(conn, tech);

                for (Map.Entry<Long, BigDecimal> job : item.jobRelevance().entrySet()) {
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
        LOG.info("트렌드 기술 수집 완료: 후보 " + candidates.size() + "건, LLM 선별 " + organized.size() + "건, 확신도 통과 " + items.size() + "건, 저장 " + saved + "건");
        return saved;
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

    // 소스별로 실패를 격리한다 — 한 곳이 죽어도 나머지 후보로 진행하고, 전부 죽었을 때만 실패로 본다
    private List<Candidate> fetchCandidates() throws ExternalApiException {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (TrendSourceClient.Source source : sourceClient.sources()) {
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
        if (candidates.isEmpty()) {
            throw new ExternalApiException("모든 트렌드 소스 수집에 실패했습니다", null);
        }
        return candidates;
    }
}
