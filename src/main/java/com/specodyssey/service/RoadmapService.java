package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.GapAnalysisItemDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.TransactionUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 로드맵 생성 · 조회 · 완료 체크.
 * 관련 요구사항: FR-32(순서 있는 로드맵) · FR-33(단계별 이유) · FR-36(완료 체크 → 진행도) · FR-37(재분석 시 새 버전)
 *
 * 격차 분석(GAP_ANALYSIS)은 다른 담당자의 기능이지만, 이 서비스는 GapAnalysisDao/GapAnalysisItemDao를
 * 읽기 전용으로만 의존한다 — 그쪽에 Service/Controller가 없어도 DB에 데이터만 있으면 동작한다.
 *
 * 단계 우선순위 규칙 (팀 합의):
 *   1) JOB_REQUIRED_SKILL.importance가 REQUIRED면 +2점, PREFERRED면 +1점, 정보 없으면 0점
 *   2) 자격증(CERT) 단계는 커버리지를 정량화할 매핑 테이블이 없어 점수 경쟁에 넣지 않고 있으면 항상 1번으로 고정
 *   3) 프로젝트(PROJECT) 단계도 1개 생성해 2번에 고정 (상위 점수 기술을 반영한 안내 문구만 제공 — 구체 프로젝트 추천은 LLM 붙을 때 고도화)
 *   4) SKILL 단계들은 위 점수 내림차순으로 정렬해 3번부터 배치
 */
public class RoadmapService {

    public static class NoGapAnalysisException extends Exception {
        public NoGapAnalysisException(String message) {
            super(message);
        }
    }

    private static final String TIER_ENTRY = "ENTRY";
    private static final String TIER_CORE = "CORE";
    private static final int SCORE_REQUIRED = 2;
    private static final int SCORE_PREFERRED = 1;

    // 부족한 기술이 아무리 많아도 ENTRY 단계엔 상위 N개만 노출한다 — 신규 사용자에게
    // 수십 단계가 한꺼번에 쏟아지는 걸 막기 위함(db-design.md의 tier 단계적 노출 원칙).
    // 나머지는 버리지 않고 CORE 단계로 남겨 "다음 단계"에서 볼 수 있게 한다.
    private static final int MAX_ENTRY_SKILL_STEPS = 5;

    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisItemDao gapAnalysisItemDao = new GapAnalysisItemDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();

    public RoadmapDto getPrimaryRoadmap(Long userId) throws SQLException {
        return roadmapDao.findPrimaryByUserId(userId);
    }

    public List<RoadmapStepDto> getSteps(Long roadmapId) throws SQLException {
        return roadmapStepDao.findByRoadmapId(roadmapId);
    }

    public void completeStep(Long userId, Long stepId, boolean completed) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            roadmapStepDao.updateCompleted(conn, stepId, userId, completed, completed ? LocalDateTime.now() : null);
            return null;
        });
    }

    // 가장 최근 격차 분석을 기준으로 새 로드맵을 생성한다. 기존 대표 로드맵이 있으면 비활성화한다 (FR-37).
    public Long generate(Long userId) throws SQLException, NoGapAnalysisException {
        List<GapAnalysisDto> analyses = gapAnalysisDao.findByUserId(userId);
        if (analyses.isEmpty()) {
            throw new NoGapAnalysisException("격차 분석 결과가 없어 로드맵을 만들 수 없습니다. 먼저 격차 분석을 진행해주세요.");
        }
        GapAnalysisDto analysis = analyses.get(0); // findByUserId는 analyzed_at DESC 정렬 — 첫 번째가 최신

        // gap_analysis_id는 UNIQUE(1:1)라 같은 분석으로 또 생성하면 제약 위반이 난다.
        // 이미 이 분석의 로드맵이 있으면 새로 만들지 않고 대표로만 지정하고 그대로 반환한다(중복 클릭 방지).
        RoadmapDto existingForAnalysis = roadmapDao.findByGapAnalysisId(analysis.getId());
        if (existingForAnalysis != null) {
            if (!existingForAnalysis.isPrimary()) {
                RoadmapDto currentPrimary = roadmapDao.findPrimaryByUserId(userId);
                TransactionUtil.runInTransaction(conn -> {
                    if (currentPrimary != null) {
                        roadmapDao.updateActiveAndPrimary(conn, currentPrimary.getId(), userId, false, false);
                    }
                    roadmapDao.updateActiveAndPrimary(conn, existingForAnalysis.getId(), userId, true, true);
                    return null;
                });
            }
            return existingForAnalysis.getId();
        }

        List<GapAnalysisItemDto> rankedMissing = rankMissingSkills(analysis);
        JobDto job = jobDao.findById(analysis.getJobId());
        CertificationDto suggestedCert = findSuggestedCertification(userId, job);
        RoadmapDto previousPrimary = roadmapDao.findPrimaryByUserId(userId);
        int nextVersion = nextVersion(userId);

        return TransactionUtil.runInTransaction(conn -> {
            if (previousPrimary != null) {
                roadmapDao.updateActiveAndPrimary(conn, previousPrimary.getId(), userId, false, false);
            }

            RoadmapDto roadmap = new RoadmapDto();
            roadmap.setUserId(userId);
            roadmap.setGapAnalysisId(analysis.getId());
            roadmap.setVersion(nextVersion);
            roadmap.setActive(true);
            roadmap.setPrimary(true);
            roadmap.setTargetLevel("EXPERT");
            Long roadmapId = roadmapDao.insert(conn, roadmap);

            List<GapAnalysisItemDto> entryTierSkills = rankedMissing.size() > MAX_ENTRY_SKILL_STEPS
                    ? rankedMissing.subList(0, MAX_ENTRY_SKILL_STEPS)
                    : rankedMissing;
            List<GapAnalysisItemDto> coreTierSkills = rankedMissing.size() > MAX_ENTRY_SKILL_STEPS
                    ? rankedMissing.subList(MAX_ENTRY_SKILL_STEPS, rankedMissing.size())
                    : List.of();

            int order = 1;
            if (suggestedCert != null) {
                order = insertStep(conn, roadmapId, order, "CERT", TIER_ENTRY, suggestedCert.getId(), null,
                        buildCertReason(job, suggestedCert));
            }
            if (!entryTierSkills.isEmpty()) {
                order = insertStep(conn, roadmapId, order, "PROJECT", TIER_ENTRY, null, null,
                        buildProjectReason(entryTierSkills));
            }
            for (GapAnalysisItemDto item : entryTierSkills) {
                String importance = importanceOf(analysis.getJobId(), item.getSkillId());
                order = insertStep(conn, roadmapId, order, "SKILL", TIER_ENTRY, null, item.getSkillId(),
                        buildSkillReason(item.getSkillId(), importance));
            }
            // 상위 N개 밖으로 밀린 기술들 — 삭제하지 않고 CORE 단계로 남겨 "다음 단계 미리보기"로 노출한다.
            for (GapAnalysisItemDto item : coreTierSkills) {
                String importance = importanceOf(analysis.getJobId(), item.getSkillId());
                order = insertStep(conn, roadmapId, order, "SKILL", TIER_CORE, null, item.getSkillId(),
                        buildSkillReason(item.getSkillId(), importance));
            }

            return roadmapId;
        });
    }

    private int insertStep(Connection conn, Long roadmapId, int order, String stepType, String tier,
                            Long certificationId, Long relatedSkillId, String reason) throws SQLException {
        RoadmapStepDto step = new RoadmapStepDto();
        step.setRoadmapId(roadmapId);
        step.setStepOrder(order);
        step.setStepType(stepType);
        step.setTier(tier);
        step.setCertificationId(certificationId);
        step.setRelatedSkillId(relatedSkillId);
        step.setReason(reason);
        step.setCompleted(false);
        roadmapStepDao.insert(conn, step);
        return order + 1;
    }

    // GAP_ANALYSIS_ITEM 중 MISSING만 골라 JOB_REQUIRED_SKILL.importance 기준 점수 내림차순 정렬.
    private List<GapAnalysisItemDto> rankMissingSkills(GapAnalysisDto analysis) throws SQLException {
        Map<Long, String> importanceBySkillId = importanceMap(analysis.getJobId());
        return gapAnalysisItemDao.findByGapAnalysisId(analysis.getId()).stream()
                .filter(item -> "MISSING".equals(item.getStatus()))
                .sorted(Comparator.comparingInt(
                        (GapAnalysisItemDto item) -> scoreOf(importanceBySkillId.get(item.getSkillId()))).reversed())
                .collect(Collectors.toList());
    }

    private Map<Long, String> importanceMap(Long jobId) throws SQLException {
        Map<Long, String> map = new HashMap<>();
        for (JobRequiredSkillDto req : jobRequiredSkillDao.findByJobId(jobId)) {
            map.put(req.getSkillId(), req.getImportance());
        }
        return map;
    }

    private String importanceOf(Long jobId, Long skillId) throws SQLException {
        return importanceMap(jobId).get(skillId);
    }

    private int scoreOf(String importance) {
        if ("REQUIRED".equals(importance)) {
            return SCORE_REQUIRED;
        }
        if ("PREFERRED".equals(importance)) {
            return SCORE_PREFERRED;
        }
        return 0;
    }

    // 목표 직무 카테고리의 자격증 중, 사용자가 이미 보유(USER_SPECS)하지 않았고 난이도가 가장 낮은 것을 고른다.
    private CertificationDto findSuggestedCertification(Long userId, JobDto job) throws SQLException {
        if (job == null || job.getJobCategory() == null) {
            return null;
        }
        List<CertificationDto> candidates = certificationDao.findByJobCategory(job.getJobCategory());
        if (candidates.isEmpty()) {
            return null;
        }
        Set<String> owned = userSpecDao.findByUserId(userId).stream()
                .filter(spec -> "CERT".equals(spec.getSpecType()) && spec.getTitle() != null)
                .map(spec -> spec.getTitle().trim().toLowerCase())
                .collect(Collectors.toSet());

        return candidates.stream()
                .filter(cert -> !owned.contains(cert.getCertName().trim().toLowerCase()))
                .findFirst() // findByJobCategory가 이미 난이도 오름차순으로 정렬해서 준다
                .orElse(null);
    }

    private int nextVersion(Long userId) throws SQLException {
        List<RoadmapDto> existing = roadmapDao.findByUserId(userId);
        return existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;
    }

    private String buildCertReason(JobDto job, CertificationDto cert) {
        String category = job == null || job.getJobCategory() == null ? "이 직무" : job.getJobCategory();
        return category + " 직무에서 기본 요건으로 자주 요구되는 자격증(" + cert.getCertName() + ")입니다.";
    }

    private String buildProjectReason(List<GapAnalysisItemDto> rankedMissing) throws SQLException {
        List<String> names = new ArrayList<>();
        for (GapAnalysisItemDto item : rankedMissing) {
            if (names.size() >= 3) {
                break;
            }
            SkillDto skill = skillDao.findById(item.getSkillId());
            if (skill != null) {
                names.add(skill.getSkillName());
            }
        }
        String topSkills = String.join(", ", names);
        return "부족한 기술을 실제로 다뤄볼 프로젝트를 진행해보세요. 우선순위가 높은 기술: " + topSkills;
    }

    private String buildSkillReason(Long skillId, String importance) throws SQLException {
        SkillDto skill = skillDao.findById(skillId);
        String skillName = skill == null ? "이 기술" : skill.getSkillName();
        if (SCORE_REQUIRED == scoreOf(importance)) {
            return skillName + "은(는) 이 직무에서 필수로 요구하는 기술인데 아직 부족합니다. 우선적으로 채워야 합니다.";
        }
        if (SCORE_PREFERRED == scoreOf(importance)) {
            return skillName + "은(는) 이 직무에서 우대하는 기술입니다. 여유가 되면 채워두면 좋습니다.";
        }
        return skillName + "은(는) 목표 직무와 관련된 기술로 파악되어 로드맵에 포함했습니다.";
    }
}
