package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.GapAnalysisItemDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.TransactionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 격차 분석. 관련 요구사항: FR-31 · 42 · 111 · 112
 *
 * 목표 직무의 요구 기술(JOB_REQUIRED_SKILL)과 사용자 보유 기술(USER_SKILLS)을 대조해
 * 기술별 MET/MISSING을 판정하고 GAP_ANALYSIS·GAP_ANALYSIS_ITEM에 저장한다.
 *
 * 매칭 방식(팀 합의, 2026-09-29): 지금은 정확 일치(대소문자·공백 무시)로 단순하게 처리한다.
 * 임베딩 기반 의미 매칭은 TD-1 배치가 붙은 뒤 similarity_score를 채우는 쪽으로 고도화할 자리만
 * 비워둔다(지금은 항상 null) — 여기서 하는 건 skill_id가 이미 같거나, skill_id가 아직 안 잡힌
 * 수동 입력(raw_input)이 SKILL.skill_name과 문자열이 같은 경우까지만 "충족"으로 본다.
 */
public class GapAnalysisService {

    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisItemDao gapAnalysisItemDao = new GapAnalysisItemDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final SkillDao skillDao = new SkillDao();
    private final JobDao jobDao = new JobDao();

    // 새 분석을 만들어 저장하고 새 GAP_ANALYSIS.id를 반환한다.
    public Long analyze(Long userId, Long jobId) throws SQLException {
        List<JobRequiredSkillDto> required = jobRequiredSkillDao.findByJobId(jobId);
        List<UserSkillDto> userSkills = userSkillDao.findByUserId(userId);

        Set<Long> ownedSkillIds = new HashSet<>();
        Set<String> ownedRawNames = new HashSet<>();
        for (UserSkillDto skill : userSkills) {
            if (skill.getSkillId() != null) {
                ownedSkillIds.add(skill.getSkillId());
            } else if (skill.getRawInput() != null) {
                ownedRawNames.add(normalize(skill.getRawInput()));
            }
        }

        int metCount = 0;
        boolean[] metFlags = new boolean[required.size()];
        for (int i = 0; i < required.size(); i++) {
            JobRequiredSkillDto req = required.get(i);
            boolean met = ownedSkillIds.contains(req.getSkillId());
            if (!met && !ownedRawNames.isEmpty()) {
                SkillDto skill = skillDao.findById(req.getSkillId());
                met = skill != null && skill.getSkillName() != null
                        && ownedRawNames.contains(normalize(skill.getSkillName()));
            }
            metFlags[i] = met;
            if (met) {
                metCount++;
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

    private String normalize(String s) {
        return s.trim().toLowerCase();
    }
}
