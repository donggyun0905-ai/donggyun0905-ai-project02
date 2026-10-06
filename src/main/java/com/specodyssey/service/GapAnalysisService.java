package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.GapAnalysisItemDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobPostingDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.TransactionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 격차 분석. 관련 요구사항: FR-31 · 42 · 111 · 112
 *
 * 목표 직무의 요구 기술(JOB_REQUIRED_SKILL)과 사용자 보유 기술(USER_SKILLS)을 대조해
 * 기술별 MET/MISSING을 판정하고 GAP_ANALYSIS·GAP_ANALYSIS_ITEM에 저장한다.
 *
 * 매칭 방식(2026-09-30 갱신): skill_id가 이미 연결된 보유 스킬은 그대로 인정하고, 아직 skill_id가
 * 없는 수동 입력(raw_input)은 SkillMatcher(기본값 EmbeddingMatcher — TD-1 임베딩, 정확 일치/
 * SKILL_ALIAS/편집거리로 못 잡으면 로컬 임베딩 유사도까지 시도)로 SKILL 마스터와 매칭한다. 매칭
 * 점수는 similarity_score에 저장해둬서(TD-1이 원래 비워뒀던 자리) 나중에 화면에서 "얼마나 확실한
 * 매칭인지" 보여줄 수 있다.
 */
public class GapAnalysisService {

    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisItemDao gapAnalysisItemDao = new GapAnalysisItemDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final JobDao jobDao = new JobDao();
    private final JobPostingDao jobPostingDao = new JobPostingDao();
    private final SkillMatcher skillMatcher;

    public GapAnalysisService() {
        this(new EmbeddingMatcher());
    }

    public GapAnalysisService(SkillMatcher skillMatcher) {
        this.skillMatcher = skillMatcher;
    }

    // 새 분석을 만들어 저장하고 새 GAP_ANALYSIS.id를 반환한다.
    public Long analyze(Long userId, Long jobId) throws SQLException {
        List<JobRequiredSkillDto> required = jobRequiredSkillDao.findByJobId(jobId);
        List<UserSkillDto> userSkills = userSkillDao.findByUserId(userId);

        Set<Long> ownedSkillIds = new HashSet<>();
        // raw_input을 매칭해서 알아낸 skill_id만 점수를 남긴다 — 이미 skill_id로 정식 연결된 보유
        // 스킬은 "매칭 신뢰도"라는 개념 자체가 없는 확정 사실이라 null로 둔다(기존 설계 그대로).
        Map<Long, Double> matchedScoreBySkillId = new HashMap<>();
        for (UserSkillDto skill : userSkills) {
            if (skill.getSkillId() != null) {
                ownedSkillIds.add(skill.getSkillId());
            } else if (skill.getRawInput() != null) {
                // "Java Spring"처럼 원문 하나에 기술이 여러 개 있으면 모두 보유로 본다 (2026-10-02)
                for (SkillMatcher.MatchResult result : skillMatcher.matchAll(skill.getRawInput())) {
                    ownedSkillIds.add(result.skillId());
                    matchedScoreBySkillId.merge(result.skillId(), result.score(), Math::max);
                }
            }
        }

        int metCount = 0;
        boolean[] metFlags = new boolean[required.size()];
        BigDecimal[] similarityScores = new BigDecimal[required.size()];
        for (int i = 0; i < required.size(); i++) {
            JobRequiredSkillDto req = required.get(i);
            boolean met = ownedSkillIds.contains(req.getSkillId());
            metFlags[i] = met;
            if (met) {
                metCount++;
                Double score = matchedScoreBySkillId.get(req.getSkillId());
                similarityScores[i] = score == null ? null : BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
            }
        }

        BigDecimal matchRate = required.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(metCount * 100.0 / required.size()).setScale(2, RoundingMode.HALF_UP);

        // 이 분석이 지금 시점 JOB.requirement_version 기준이라는 걸 스냅샷으로 남긴다(2026-09-30
        // 팀 결정) — 나중에 JOB 쪽 요구 기술이 바뀌면(bumpRequirementVersion) 이 값과 비교해서
        // 로드맵이 낡았는지 판단한다(RoadmapService.isJobRequirementOutdated).
        JobDto job = jobDao.findById(jobId);
        Integer jobRequirementVersion = job == null ? null : job.getRequirementVersion();

        return TransactionUtil.runInTransaction(conn -> {
            GapAnalysisDto analysis = new GapAnalysisDto();
            analysis.setUserId(userId);
            analysis.setJobId(jobId);
            analysis.setMatchRate(matchRate);
            analysis.setJobRequirementVersion(jobRequirementVersion);
            analysis.setAnalyzedAt(LocalDateTime.now());
            Long analysisId = gapAnalysisDao.insert(conn, analysis);

            for (int i = 0; i < required.size(); i++) {
                GapAnalysisItemDto item = new GapAnalysisItemDto();
                item.setGapAnalysisId(analysisId);
                item.setSkillId(required.get(i).getSkillId());
                item.setStatus(metFlags[i] ? "MET" : "MISSING");
                item.setSimilarityScore(similarityScores[i]);
                gapAnalysisItemDao.insert(conn, item);
            }
            return analysisId;
        });
    }

    // 로드맵 페이지 등에서 "최근 분석 있나" 확인할 때 — findByUserId가 analyzed_at DESC라 첫 번째가 최신.
    public GapAnalysisDto getLatest(Long userId) throws SQLException {
        List<GapAnalysisDto> analyses = gapAnalysisDao.findByUserId(userId);
        return analyses.isEmpty() ? null : analyses.get(0);
    }

    public List<GapAnalysisItemDto> getItems(Long gapAnalysisId) throws SQLException {
        return gapAnalysisItemDao.findByGapAnalysisId(gapAnalysisId);
    }

    // ================= FR-113 · TD-2 "예시적 추정" 표시 (2026-10-06, E 추가) =================

    /** 이 공고 수보다 적으면 "실제 공고가 적어 일반적인 요구 역량 기반 추정" 안내를 띄운다 */
    public static final int FEW_POSTINGS_THRESHOLD = 5;

    /** 직무 요구 기술이 어디서 왔는지 — 추정치(is_estimated)인 기술과 그 직무의 실제 공고 수 */
    public static final class RequirementSource {
        private final Set<Long> estimatedSkillIds;
        private final int postingCount;

        public RequirementSource(Set<Long> estimatedSkillIds, int postingCount) {
            this.estimatedSkillIds = Set.copyOf(estimatedSkillIds);
            this.postingCount = postingCount;
        }

        public boolean isEstimated(Long skillId) {
            return skillId != null && estimatedSkillIds.contains(skillId);
        }

        public boolean isAnyEstimated() {
            return !estimatedSkillIds.isEmpty();
        }

        public int getPostingCount() {
            return postingCount;
        }

        public boolean isFewPostings() {
            return postingCount < FEW_POSTINGS_THRESHOLD;
        }
    }

    public RequirementSource getRequirementSource(Long jobId) throws SQLException {
        Set<Long> estimated = new HashSet<>();
        for (JobRequiredSkillDto required : jobRequiredSkillDao.findByJobId(jobId)) {
            if (required.isEstimated()) {
                estimated.add(required.getSkillId());
            }
        }
        return new RequirementSource(estimated, jobPostingDao.countByJobId(jobId));
    }
}
