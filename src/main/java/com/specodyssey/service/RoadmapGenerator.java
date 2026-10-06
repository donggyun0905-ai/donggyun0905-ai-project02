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
import com.specodyssey.dto.SkillDto;
import com.specodyssey.service.RoadmapService.NoGapAnalysisException;
import com.specodyssey.util.AiNotices;
import com.specodyssey.util.TransactionUtil;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static com.specodyssey.service.RoadmapConstants.*;

/**
 * 로드맵 생성(FR-32·33·37) — 격차 분석에서 단계를 골라 새 버전의 로드맵을 만든다.
 * RoadmapService 안에 있던 생성 부분을 그대로 옮겼다.
 */
public class RoadmapGenerator {

    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisItemDao gapAnalysisItemDao = new GapAnalysisItemDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final RoadmapStepWriter stepWriter = new RoadmapStepWriter();
    private final ProjectIdeaService projectIdeaService;
    private final SkillDeepenService skillDeepenService;

    public RoadmapGenerator(ProjectIdeaService projectIdeaService, SkillDeepenService skillDeepenService) {
        this.projectIdeaService = projectIdeaService;
        this.skillDeepenService = skillDeepenService;
    }

    private static final int SCORE_REQUIRED = 2;
    private static final int SCORE_PREFERRED = 1;
    // 프로젝트 추천 LLM이 실패해 고정 문구로 대체했을 때 다음 화면에 띄우는 안내 (FR-111)
    static final String PROJECT_FALLBACK_NOTICE =
            "AI 프로젝트 추천을 받지 못해 기본 안내로 대신했습니다. 로드맵을 다시 만들면 새로 추천받을 수 있어요.";
    // AI 프로젝트 아이디어 단계의 reason 앞머리 — roadmap.jsp가 이걸로 "제목 — 설명"을 나눠 카드엔 제목만 보여준다.
    // 예전에는 💡 이모지였다(2026-10-03 이모지 제거, 저장된 값은 sql/22로 바꿈).
    public static final String PROJECT_IDEA_PREFIX = "아이디어: ";
    private static final String COMMON_CATEGORY = "COMMON";

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
        Map<Long, String> importanceBySkillId = importanceMap(analysis.getJobId());
        // 이번 라운드에 담을 기술 — 부족 기술 상위 N개, 모자라면 이미 갖춘 직무 요구 기술로 보충한다.
        // LLM 호출이 들어갈 수 있어서 DB 트랜잭션을 열기 전에 끝낸다.
        List<Long> roundSkillIds = selectRoundSkills(job, rankedMissing, importanceBySkillId);
        CertificationDto suggestedCert = findSuggestedCertification(userId, job);
        // 직무와 상관없이 많은 기업이 보는 공통 자격증(어학·컴활)도 하나 함께 제안한다(2026-10-03 사용자 요청 —
        // 직무 자격증이 다 떨어져야만 공통 자격증이 나오던 탓에 사실상 보이지 않았다).
        CertificationDto commonCert = findSuggestedCommonCertification(userId);
        // ENTRY 티어의 PROJECT 단계 안내 문구를 미리 만들어둔다 — LLM 호출은 DB 트랜잭션을 열기
        // 전에 끝내야 한다(claude.md: 외부 API 호출에 타임아웃을 직접 두고, 느리거나 실패해도
        // DB 커넥션을 물고 있으면 안 됨). 실패해도 로드맵 생성 자체는 막지 않는다(FR-111).
        String projectReason = roundSkillIds.isEmpty() ? null : buildProjectReason(job, roundSkillIds);
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

            // 티어 순서대로 라운드 기술 전부에 같은 티어 단계를 하나씩 만든다 — 같은 기술이 입문 노트 →
            // 핵심 프로젝트 → 심화 업그레이드 → 전문가 글로 이어진다. 앞 티어를 끝내야 다음 티어가
            // 열리는 계단식 잠금은 computeProgress에서 매 조회 시 계산한다.
            int order = 1;
            for (String tier : SKILL_TIER_ORDER) {
                if (TIER_ENTRY.equals(tier)) {
                    // CERT/PROJECT는 여정의 첫 진입점 성격이라 ENTRY 티어에만 둔다.
                    if (suggestedCert != null) {
                        order = stepWriter.insertStep(conn, roadmapId, order, "CERT", tier, suggestedCert.getId(), null,
                                buildCertReason(job, suggestedCert));
                    }
                    if (commonCert != null) {
                        order = stepWriter.insertStep(conn, roadmapId, order, "CERT", tier, commonCert.getId(), null,
                                buildCommonCertReason(commonCert));
                    }
                    if (!roundSkillIds.isEmpty()) {
                        order = stepWriter.insertStep(conn, roadmapId, order, "PROJECT", tier, null, null, projectReason);
                    }
                }
                for (Long skillId : roundSkillIds) {
                    // 같은 기술의 같은 단계를 이전 로드맵에서 이미 끝냈으면 완료로 승계한다(점수는 다시 안 줌).
                    boolean alreadyLearned =
                            roadmapStepDao.countCompletedByUserSkillAndTier(conn, userId, skillId, tier) > 0;
                    order = stepWriter.insertStep(conn, roadmapId, order, "SKILL", tier, null, skillId,
                            buildSkillReason(skillId, importanceBySkillId.get(skillId), tier), alreadyLearned);
                }
            }

            return roadmapId;
        });
    }

    // 부족 기술 상위 ROUND_SKILL_COUNT(규칙)개를 담고, 모자라면 직무 요구 기술 중 아직 라운드에 없는 것으로
    // 보충한다. 보충 후보 선택은 LLM(SkillDeepenService)에 맡기고, 실패하거나 모자라면 중요도 순으로 채운다.
    private List<Long> selectRoundSkills(JobDto job, List<GapAnalysisItemDto> rankedMissing,
            Map<Long, String> importanceBySkillId) throws SQLException {
        List<Long> selected = new ArrayList<>();
        for (GapAnalysisItemDto item : rankedMissing) {
            if (selected.size() >= ScoringRules.get(ScoringRules.ROUND_SKILL_COUNT)) {
                break;
            }
            if (!selected.contains(item.getSkillId())) {
                selected.add(item.getSkillId());
            }
        }
        int needed = ScoringRules.get(ScoringRules.ROUND_SKILL_COUNT) - selected.size();
        if (needed <= 0) {
            return selected;
        }

        // 후보: 직무 요구 기술 중 라운드에 아직 없는 것, 중요도 높은 순(동점은 DB 순서 유지).
        Map<Long, SkillDto> candidates = new java.util.LinkedHashMap<>();
        importanceBySkillId.entrySet().stream()
                .sorted((x, y) -> Integer.compare(scoreOf(y.getValue()), scoreOf(x.getValue())))
                .map(Map.Entry::getKey)
                .filter(id -> !selected.contains(id))
                .forEach(id -> candidates.put(id, null));
        for (Long id : new ArrayList<>(candidates.keySet())) {
            SkillDto skill = skillDao.findById(id);
            if (skill == null || skill.getSkillName() == null) {
                candidates.remove(id);
            } else {
                candidates.put(id, skill);
            }
        }
        if (candidates.isEmpty()) {
            return selected;
        }

        List<Long> additions = new ArrayList<>();
        try {
            List<String> plannedNames = new ArrayList<>();
            for (Long id : selected) {
                SkillDto skill = skillDao.findById(id);
                if (skill != null) {
                    plannedNames.add(skill.getSkillName());
                }
            }
            List<String> picked = skillDeepenService.pick(
                    job == null || job.getJobName() == null ? "이 직무" : job.getJobName(), plannedNames,
                    candidates.values().stream().map(SkillDto::getSkillName).collect(Collectors.toList()), needed);
            for (String name : picked) {
                for (Map.Entry<Long, SkillDto> candidate : candidates.entrySet()) {
                    if (name.equals(candidate.getValue().getSkillName()) && !additions.contains(candidate.getKey())) {
                        additions.add(candidate.getKey());
                    }
                }
            }
        } catch (Exception e) {
            // LLM 없음·실패·형식 오류 — 로드맵 생성을 막지 않고 아래 중요도 순 보충으로 대체한다(FR-111).
        }
        for (Long id : candidates.keySet()) {
            if (additions.size() >= needed) {
                break;
            }
            if (!additions.contains(id)) {
                additions.add(id);
            }
        }
        selected.addAll(additions.subList(0, Math.min(needed, additions.size())));
        return selected;
    }

    // GAP_ANALYSIS_ITEM 중 MISSING만 골라 JOB_REQUIRED_SKILL.importance 기준 점수 내림차순 정렬.
    List<GapAnalysisItemDto> rankMissingSkills(GapAnalysisDto analysis) throws SQLException {
        Map<Long, String> importanceBySkillId = importanceMap(analysis.getJobId());
        return gapAnalysisItemDao.findByGapAnalysisId(analysis.getId()).stream()
                .filter(item -> "MISSING".equals(item.getStatus()))
                .sorted(Comparator.comparingInt(
                        (GapAnalysisItemDto item) -> scoreOf(importanceBySkillId.get(item.getSkillId()))).reversed())
                .collect(Collectors.toList());
    }

    Map<Long, String> importanceMap(Long jobId) throws SQLException {
        Map<Long, String> map = new HashMap<>();
        for (JobRequiredSkillDto req : jobRequiredSkillDao.findByJobId(jobId)) {
            map.put(req.getSkillId(), req.getImportance());
        }
        return map;
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
    // 공통(COMMON) 자격증은 findSuggestedCommonCertification이 따로 한 칸을 맡으므로 여기서는 고르지 않는다.
    CertificationDto findSuggestedCertification(Long userId, JobDto job) throws SQLException {
        if (job == null || job.getJobCategory() == null || COMMON_CATEGORY.equals(job.getJobCategory())) {
            return null;
        }
        List<CertificationDto> candidates = certificationDao.findByJobCategory(job.getJobCategory()).stream()
                .filter(cert -> !COMMON_CATEGORY.equals(cert.getJobCategory()))
                .toList();
        return firstNotOwned(userId, candidates, false);
    }

    // 직무와 상관없이 쓰는 공통 자격증(어학·컴활 등) 중 사용자가 아직 없는 것 하나 — 난이도 낮은 순.
    CertificationDto findSuggestedCommonCertification(Long userId) throws SQLException {
        return firstNotOwned(userId, certificationDao.findByJobCategory(COMMON_CATEGORY), true);
    }

    // 공통 자격증의 한글 표기 — 프로필에 "토익 850"처럼 적어 둔 것도 이미 가진 것으로 본다
    private static final Map<String, List<String>> COMMON_CERT_ALIASES = Map.of(
            "toeic", List.of("토익"),
            "toeicspeaking", List.of("토익스피킹", "토스"),
            "opic", List.of("오픽"));

    // includeLanguage: 공통 자격증(어학 등)은 프로필의 "어학" 항목에 점수와 함께 적는 경우가 많아
    // 자격증 이름이 그 제목에 들어 있으면(예: "TOEIC 850") 이미 가진 것으로 본다.
    private CertificationDto firstNotOwned(Long userId, List<CertificationDto> candidates, boolean includeLanguage)
            throws SQLException {
        if (candidates.isEmpty()) {
            return null;
        }
        Set<String> owned = userSpecDao.findByUserId(userId).stream()
                .filter(spec -> spec.getTitle() != null && ("CERT".equals(spec.getSpecType())
                        || (includeLanguage && "LANGUAGE".equals(spec.getSpecType()))))
                .map(spec -> spec.getTitle().trim().toLowerCase())
                .collect(Collectors.toSet());

        return candidates.stream()
                .filter(cert -> !owned.contains(cert.getCertName().trim().toLowerCase())
                        && !(includeLanguage && mentionedIn(owned, cert.getCertName())))
                .findFirst() // findByJobCategory가 이미 난이도 오름차순으로 정렬해서 준다
                .orElse(null);
    }

    // 자격증 이름(띄어쓰기 무시) 또는 한글 표기가 보유 스펙 제목에 들어 있는지 — "TOEIC Speaking"이 "TOEIC"보다 길어
    // 먼저 검사되지 않도록 별칭까지 포함해 단순 포함 여부만 본다.
    private static boolean mentionedIn(Set<String> ownedTitles, String certName) {
        String key = certName.toLowerCase().replace(" ", "");
        List<String> names = new ArrayList<>(COMMON_CERT_ALIASES.getOrDefault(key, List.of()));
        names.add(key);
        return ownedTitles.stream().map(title -> title.replace(" ", ""))
                .anyMatch(title -> names.stream().anyMatch(title::contains));
    }

    private int nextVersion(Long userId) throws SQLException {
        List<RoadmapDto> existing = roadmapDao.findByUserId(userId);
        return existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;
    }

    String buildCommonCertReason(CertificationDto cert) {
        return "직무와 상관없이 많은 기업이 서류에서 보는 공통 자격증(" + cert.getCertName() + ")입니다. "
                + "어학 점수나 컴퓨터 활용 능력은 지원 자격으로 자주 쓰여요.";
    }

    String buildCertReason(JobDto job, CertificationDto cert) {
        String category = job == null || job.getJobCategory() == null ? "이 직무" : job.getJobCategory();
        return category + " 직무에서 기본 요건으로 자주 요구되는 자격증(" + cert.getCertName() + ")입니다.";
    }

    // LLM(ProjectIdeaService)이 목표 직무 + 부족 기술로 구체적인 프로젝트 아이디어를 만들어준다.
    // 실패(API 키 없음·타임아웃·응답 형식 오류 등)해도 로드맵 생성 자체를 막으면 안 되므로(FR-111),
    // 여기서 예외를 잡아 기존 고정 문구로 조용히 대체한다 — 2026-09-30, 집 PC 작업에서 신규 도입.
    String buildProjectReason(JobDto job, List<Long> skillIds) throws SQLException {
        List<String> names = new ArrayList<>();
        for (Long skillId : skillIds) {
            if (names.size() >= 3) {
                break;
            }
            SkillDto skill = skillDao.findById(skillId);
            if (skill != null) {
                names.add(skill.getSkillName());
            }
        }
        try {
            ProjectIdeaService.ProjectIdea idea = projectIdeaService.suggest(
                    job == null || job.getJobName() == null ? "이 직무" : job.getJobName(), names);
            return PROJECT_IDEA_PREFIX + idea.title() + " — " + idea.description();
        } catch (Exception e) {
            // FR-111 — 조용히 넘기지 않고 다음 화면에 안내한다 (2026-10-02)
            AiNotices.add(PROJECT_FALLBACK_NOTICE);
            String topSkills = String.join(", ", names);
            return "부족한 기술을 실제로 다뤄볼 프로젝트를 진행해보세요. 우선순위가 높은 기술: " + topSkills;
        }
    }

    String buildSkillReason(Long skillId, String importance, String tier) throws SQLException {
        SkillDto skill = skillDao.findById(skillId);
        String skillName = skill == null ? "이 기술" : skill.getSkillName();
        if (TIER_CORE.equals(tier)) {
            return skillName + "을(를) 직접 써본 프로젝트를 등록해 배운 내용을 실전에 적용합니다.";
        }
        if (TIER_ADVANCED.equals(tier)) {
            return "앞서 만든 프로젝트를 " + skillName + " 기준으로 한 단계 업그레이드해 완성도를 높입니다.";
        }
        if (TIER_EXPERT.equals(tier)) {
            return skillName + "을(를) 다른 사람이 이해할 수 있게 설명하는 글을 써서 진짜 내 것으로 만듭니다.";
        }
        if (SCORE_REQUIRED == scoreOf(importance)) {
            return skillName + "은(는) 이 직무에서 필수로 요구하는 기술인데 아직 부족합니다. 우선적으로 채워야 합니다.";
        }
        if (SCORE_PREFERRED == scoreOf(importance)) {
            return skillName + "은(는) 이 직무에서 우대하는 기술입니다. 여유가 되면 채워두면 좋습니다.";
        }
        return skillName + "은(는) 목표 직무와 관련된 기술로 파악되어 로드맵에 포함했습니다.";
    }
}
