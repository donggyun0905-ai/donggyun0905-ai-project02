package com.specodyssey.service;

import com.specodyssey.dao.JobPostingDao;
import com.specodyssey.dao.JobSkillTrendDao;
import com.specodyssey.dto.JobPostingDto;
import com.specodyssey.dto.JobSkillTrendDto;
import com.specodyssey.util.TransactionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * JOB_SKILL_TREND 월별 집계 서비스 — LLM 없이, 이미 수집된 JOB_POSTING.tech_stack만으로 채운다.
 * 팀 결정(2026-09-30): "받을 API나 자료가 없어서" LLM으로 채우려던 계획을 접고, 채용공고
 * tech_stack 텍스트를 직접 집계하는 규칙 기반 방식으로 대체한다 (토큰 비용 없음).
 *
 * 집계 단위는 (job_id, period_ym) — period_ym은 posted_at 기준(없으면 collected_at 날짜로 대체)이고,
 * mention_count는 그 달 그 직무 공고 중 해당 기술이 언급된 "공고 수"(한 공고 안에서 중복 언급은 1로 셈),
 * mention_ratio는 그 중 비율을 퍼센트(DECIMAL(5,2))로 저장한다.
 *
 * 기술명 해석은 FuzzyNameMatcher를 쓴다 — tech_stack 토큰은 이미 정제된 이름이라 대부분 SKILL.skill_name
 * 정확 일치로 끝나고, 표기 차이가 있는 것만 SKILL_ALIAS·편집거리로 보완한다. 임베딩(EmbeddingMatcher)은
 * ONNX 모델 로딩이 필요해 이런 단순 집계 배치에는 과하다.
 */
public class JobSkillTrendService {

    private static final Logger LOG = Logger.getLogger(JobSkillTrendService.class.getName());
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final JobPostingDao jobPostingDao;
    private final JobSkillTrendDao jobSkillTrendDao;
    private final SkillMatcher skillMatcher;

    public JobSkillTrendService() {
        this(new JobPostingDao(), new JobSkillTrendDao(), new FuzzyNameMatcher());
    }

    public JobSkillTrendService(JobPostingDao jobPostingDao, JobSkillTrendDao jobSkillTrendDao,
                                 SkillMatcher skillMatcher) {
        this.jobPostingDao = jobPostingDao;
        this.jobSkillTrendDao = jobSkillTrendDao;
        this.skillMatcher = skillMatcher;
    }

    private record GroupKey(Long jobId, String periodYm) {
    }

    /**
     * JOB_POSTING 전체를 훑어 (job_id, period_ym, skill_id)별 mention_count/mention_ratio를 다시 계산하고
     * upsert한다. 이미 공고가 끝난 지난 달도 같은 값으로 재기록될 뿐이라 여러 번 돌려도 안전하다.
     *
     * @return upsert된 (job, skill, period) 행 수
     */
    public int refreshAll() throws SQLException {
        List<JobPostingDto> postings = jobPostingDao.findAll();

        // groupKey -> (skillId -> 그 그룹에서 언급한 공고 수)
        Map<GroupKey, Map<Long, Integer>> mentionCounts = new HashMap<>();
        // groupKey -> 그 그룹의 전체 공고 수 (분모)
        Map<GroupKey, Integer> totalPostings = new HashMap<>();
        Set<String> unresolved = new HashSet<>();
        // 토큰(예: "Java")이 공고 수백 건에 반복 등장하는데, FuzzyNameMatcher는 실패할 때마다
        // SKILL·SKILL_ALIAS 전체를 원격 DB에서 다시 긁어온다 — 같은 문자열은 한 번만 매칭하도록
        // 캐시해서 이 배치 안에서의 실제 매처 호출 횟수를 "서로 다른 토큰 수"로 줄인다.
        Map<String, SkillMatcher.MatchResult> matchCache = new HashMap<>();

        for (JobPostingDto posting : postings) {
            String periodYm = resolvePeriodYm(posting);
            if (periodYm == null || posting.getTechStack() == null || posting.getTechStack().isBlank()) {
                continue;
            }
            GroupKey key = new GroupKey(posting.getJobId(), periodYm);
            totalPostings.merge(key, 1, Integer::sum);

            Set<Long> skillsInThisPosting = new HashSet<>();
            for (String token : posting.getTechStack().split(",")) {
                String name = token.trim();
                if (name.isEmpty()) {
                    continue;
                }
                SkillMatcher.MatchResult result = matchCache.get(name);
                if (result == null) {
                    result = skillMatcher.match(name);
                    matchCache.put(name, result);
                }
                if (result.skillId() == null) {
                    unresolved.add(name);
                    continue;
                }
                skillsInThisPosting.add(result.skillId());
            }

            Map<Long, Integer> groupCounts = mentionCounts.computeIfAbsent(key, k -> new HashMap<>());
            for (Long skillId : skillsInThisPosting) {
                groupCounts.merge(skillId, 1, Integer::sum);
            }
        }

        if (!unresolved.isEmpty()) {
            LOG.warning(() -> "JOB_SKILL_TREND 집계 — SKILL로 해석 못 한 tech_stack 토큰 "
                    + unresolved.size() + "개: " + unresolved);
        }

        List<JobSkillTrendDto> toUpsert = new ArrayList<>();
        for (Map.Entry<GroupKey, Map<Long, Integer>> groupEntry : mentionCounts.entrySet()) {
            GroupKey key = groupEntry.getKey();
            int total = totalPostings.get(key);
            for (Map.Entry<Long, Integer> skillEntry : groupEntry.getValue().entrySet()) {
                int count = skillEntry.getValue();
                BigDecimal ratio = BigDecimal.valueOf(count)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

                JobSkillTrendDto trend = new JobSkillTrendDto();
                trend.setJobId(key.jobId());
                trend.setSkillId(skillEntry.getKey());
                trend.setPeriodYm(key.periodYm());
                trend.setMentionCount(count);
                trend.setMentionRatio(ratio);
                toUpsert.add(trend);
            }
        }

        return TransactionUtil.runInTransaction(conn -> {
            for (JobSkillTrendDto trend : toUpsert) {
                jobSkillTrendDao.upsertMonth(conn, trend);
            }
            return toUpsert.size();
        });
    }

    private String resolvePeriodYm(JobPostingDto posting) {
        LocalDate date = posting.getPostedAt();
        if (date == null && posting.getCollectedAt() != null) {
            date = posting.getCollectedAt().toLocalDate();
        }
        return date == null ? null : date.format(PERIOD_FORMAT);
    }
}
