package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.GapAnalysisItemDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
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
    private static final String TIER_ADVANCED = "ADVANCED";
    private static final String TIER_EXPERT = "EXPERT";
    // db-design.md: "ENTRY를 다 걸으면 CORE가, CORE를 마치면 ADVANCED가 열리는 식으로 로드맵이
    // 계속 연장된다" — 끝없는 여정 구조. 부족 기술을 점수 내림차순으로 정렬한 뒤 이 순서대로
    // 5개씩 잘라 담고, 마지막(EXPERT)은 남은 걸 전부 받는다(팀 합의, 2026-09-29).
    private static final List<String> SKILL_TIER_ORDER = List.of(TIER_ENTRY, TIER_CORE, TIER_ADVANCED, TIER_EXPERT);
    private static final int SCORE_REQUIRED = 2;
    private static final int SCORE_PREFERRED = 1;

    // 부족한 기술이 아무리 많아도 한 티어엔 상위 N개만 노출한다 — 신규 사용자에게 수십 단계가
    // 한꺼번에 쏟아지는 걸 막기 위함(db-design.md의 tier 단계적 노출 원칙). 나머지는 버리지 않고
    // 다음 티어로 넘겨서 "다음 단계"에서 볼 수 있게 한다.
    private static final int MAX_SKILL_STEPS_PER_TIER = 5;

    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final GapAnalysisItemDao gapAnalysisItemDao = new GapAnalysisItemDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final ScoreService scoreService = new ScoreService();
    private final ProjectIdeaService projectIdeaService = new ProjectIdeaService();
    private final DdayAutoGenerationService ddayAutoGenerationService = new DdayAutoGenerationService();

    // TD-5 배점: 로드맵 단계 완료당 +100 (여정 서비스의 핵심이라 배점 최상)
    private static final int ROADMAP_STEP_COMPLETE_POINTS = 100;
    private static final String SIGNAL_TYPE_ROADMAP = "ROADMAP";

    // SKILL 단계 학습 검증(2026-09-30 팀 결정) — tier별 증빙 방식. ROADMAP_STEP.proof_type에 저장.
    private static final String PROOF_NOTE = "NOTE";
    private static final String PROOF_PROJECT_LINK = "PROJECT_LINK";
    private static final String PROOF_TEACHING_POST = "TEACHING_POST";
    private static final String PROOF_CERT_DOCUMENT = "CERT_DOCUMENT";

    // SKILL 단계 완료 → USER_SKILLS 숙련도 자동 승급 기준 (팀 합의, 2026-09-23).
    // 완료를 취소해도 이미 오른 숙련도는 안 내린다 — 점수 정책과 같은 원칙.
    private static final int PROFICIENCY_INTERMEDIATE_THRESHOLD = 10;
    private static final int PROFICIENCY_ADVANCED_THRESHOLD = 30;
    private static final String PROFICIENCY_BEGINNER = "BEGINNER";
    private static final String PROFICIENCY_INTERMEDIATE = "INTERMEDIATE";
    private static final String PROFICIENCY_ADVANCED = "ADVANCED";

    public RoadmapDto getPrimaryRoadmap(Long userId) throws SQLException {
        return roadmapDao.findPrimaryByUserId(userId);
    }

    // "로드맵이 한 번 만들면 고정되는 문제" 해결책(2026-09-30 팀 결정) — 대표 로드맵이 기준으로 삼은
    // 분석(GAP_ANALYSIS.job_requirement_version)이 JOB의 현재 requirement_version보다 낡았으면
    // true. DB 비교만으로 판단해서 비용이 0원이다(AI 호출 없음) — 실제 재분석·재생성은 사용자가
    // 배너를 보고 직접 눌러야만 일어난다.
    public boolean isJobRequirementOutdated(Long userId) throws SQLException {
        RoadmapDto primary = roadmapDao.findPrimaryByUserId(userId);
        if (primary == null || primary.getGapAnalysisId() == null) {
            return false;
        }
        GapAnalysisDto analysis = gapAnalysisDao.findById(primary.getGapAnalysisId());
        if (analysis == null || analysis.getJobRequirementVersion() == null) {
            return false;
        }
        JobDto job = jobDao.findById(analysis.getJobId());
        return job != null && job.getRequirementVersion() != analysis.getJobRequirementVersion();
    }

    public List<RoadmapStepDto> getSteps(Long roadmapId) throws SQLException {
        return roadmapStepDao.findByRoadmapId(roadmapId);
    }

    // 사용자가 로드맵의 "완료하기"를 거치지 않고 프로필에서 직접 자격증을 추가했을 때 호출한다
    // (ProfileSpecServlet에서 스펙 추가가 CERT 타입일 때 호출). 대표 로드맵에 같은 자격증을 요구하는
    // 미완료 CERT 단계가 있으면 그걸 바로 완료 처리한다 — 안 그러면 이미 딴 자격증을 로드맵이 계속
    // "할 일"로 보여준다(팀 합의, 2026-09-23). completeStep을 그대로 타므로 점수도 정상 적립되고,
    // USER_SPECS 재삽입은 syncCertification의 중복 가드가 막아준다(이미 방금 넣은 값이라 그대로 스킵).
    public void syncCertAddedFromProfile(Long userId, String certTitle) throws SQLException {
        if (certTitle == null || certTitle.isBlank()) {
            return;
        }
        RoadmapDto primary = roadmapDao.findPrimaryByUserId(userId);
        if (primary == null) {
            return;
        }
        String normalizedTitle = certTitle.trim().toLowerCase();
        for (RoadmapStepDto step : roadmapStepDao.findByRoadmapId(primary.getId())) {
            if (!"CERT".equals(step.getStepType()) || step.isCompleted() || step.getCertificationId() == null) {
                continue;
            }
            CertificationDto cert = certificationDao.findById(step.getCertificationId());
            if (cert != null && cert.getCertName() != null
                    && normalizedTitle.equals(cert.getCertName().trim().toLowerCase())) {
                completeStep(userId, step.getId(), true);
                return; // 대표 로드맵 안에서 같은 자격증 CERT 단계는 하나뿐이라 찾으면 바로 끝낸다.
            }
        }
    }

    // 사용자가 로드맵의 "완료하기"를 거치지 않고 프로필에서 직접 기술 스택을 추가했을 때 호출한다
    // (ProfileSkillServlet에서 스킬 추가 시 호출). syncCertAddedFromProfile과 같은 이유 — 자격증뿐
    // 아니라 스킬도 똑같이 겹칠 수 있다(팀 합의, 2026-09-23). completeStep을 그대로 태우면
    // syncSkill이 raw_input 기준으로 기존 행을 찾아 skill_id를 채워 넣어서 중복 행도 안 생긴다.
    public void syncSkillAddedFromProfile(Long userId, String rawSkillName) throws SQLException {
        if (rawSkillName == null || rawSkillName.isBlank()) {
            return;
        }
        RoadmapDto primary = roadmapDao.findPrimaryByUserId(userId);
        if (primary == null) {
            return;
        }
        String normalizedName = rawSkillName.trim().toLowerCase();
        for (RoadmapStepDto step : roadmapStepDao.findByRoadmapId(primary.getId())) {
            if (!"SKILL".equals(step.getStepType()) || step.isCompleted() || step.getRelatedSkillId() == null) {
                continue;
            }
            SkillDto skill = skillDao.findById(step.getRelatedSkillId());
            if (skill != null && skill.getSkillName() != null
                    && normalizedName.equals(skill.getSkillName().trim().toLowerCase())) {
                // rankMissingSkills는 GAP_ANALYSIS_ITEM당 한 단계만 만들어서 같은 스킬이 한 로드맵에
                // 두 번 나올 수 없다 — 찾으면 바로 끝낸다 (CERT 쪽과 동일한 전제).
                completeStep(userId, step.getId(), true);
                return;
            }
        }
    }

    // FR-36 진행도. target_level이 기본 EXPERT라 길이 계속 늘어나는 구조(db-design.md 설계 판단)라서
    // "전체 대비 %"는 분모가 계속 바뀌어 의미가 없다 — 그래서 티어별로 계산하고, 앞 티어를 다
    // 끝내야(또는 그 티어에 단계가 아예 없으면) 다음 티어가 풀리는 계단식 잠금으로 표현한다
    // (팀 합의, 2026-09-23 / ADVANCED·EXPERT 확장 2026-09-29). ENTRY는 항상 열려 있다.
    // 매번 steps 원본에서 새로 계산하기 때문에, 완료 취소로 이전 티어가 다시 미완료가 되면
    // 이후 티어도 그 즉시 다시 잠긴다 — 잠기기 전에 이미 완료한 단계 자체는 그대로 완료로 남는다.
    public RoadmapProgress computeProgress(List<RoadmapStepDto> steps) {
        List<TierProgress> tiers = new ArrayList<>();
        boolean unlocked = true;
        for (String tier : SKILL_TIER_ORDER) {
            long total = steps.stream().filter(s -> tier.equals(s.getTier())).count();
            long done = steps.stream().filter(s -> tier.equals(s.getTier()) && s.isCompleted()).count();
            int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
            tiers.add(new TierProgress(tier, (int) total, (int) done, percent, unlocked));
            boolean cleared = total == 0 || done == total;
            unlocked = unlocked && cleared;
        }
        return new RoadmapProgress(tiers);
    }

    public static final class TierProgress {
        private final String tier;
        private final int total;
        private final int done;
        private final int percent;
        private final boolean unlocked;

        public TierProgress(String tier, int total, int done, int percent, boolean unlocked) {
            this.tier = tier;
            this.total = total;
            this.done = done;
            this.percent = percent;
            this.unlocked = unlocked;
        }

        public String getTier() {
            return tier;
        }

        public int getTotal() {
            return total;
        }

        public int getDone() {
            return done;
        }

        public int getPercent() {
            return percent;
        }

        public boolean isUnlocked() {
            return unlocked;
        }

        // "empty"는 EL 예약어라 JSP에서 t.empty로 접근하면 태그 검증 단계에서 컴파일 자체가
        // 깨진다(2026-09-29 실제 배포 중 발견) — isEmptyTier로 이름을 피해서 짓는다.
        public boolean isEmptyTier() {
            return total == 0;
        }

        public boolean isComplete() {
            return total > 0 && done == total;
        }
    }

    public static final class RoadmapProgress {
        private final List<TierProgress> tiers;

        public RoadmapProgress(List<TierProgress> tiers) {
            this.tiers = tiers;
        }

        public List<TierProgress> getTiers() {
            return tiers;
        }

        public TierProgress getTier(String tierName) {
            return tiers.stream().filter(t -> t.getTier().equals(tierName)).findFirst().orElse(null);
        }

        // 화면의 "지금 할 일" 섹션 — 열려 있고, 비어 있지 않고, 아직 다 안 끝난 첫 번째 티어.
        public TierProgress getCurrentTier() {
            return tiers.stream()
                    .filter(t -> t.isUnlocked() && !t.isEmptyTier() && !t.isComplete())
                    .findFirst().orElse(null);
        }

        // "다음 단계 미리보기" 섹션 — 아직 잠겨 있고 비어 있지 않은 첫 번째 티어.
        public TierProgress getNextLockedTier() {
            return tiers.stream()
                    .filter(t -> !t.isUnlocked() && !t.isEmptyTier())
                    .findFirst().orElse(null);
        }

        // 지금 할 일도, 다음에 풀릴 잠긴 단계도 없다 — 부족 기술을 전부 채웠거나 애초에 없었던 것.
        public boolean isJourneyComplete() {
            return getCurrentTier() == null && getNextLockedTier() == null;
        }
    }

    // 완료 취소해도 이미 적립된 점수·스펙·숙련도는 깎지 않는다
    // (TD-5: 상한 없는 게임식 누적, 완료 취소해도 실수로 배운 게 없어지진 않는다 — 팀 합의, 2026-09-23).
    // 완료 처리 + 점수 적립 + 스펙/스킬 반영을 한 트랜잭션으로 묶어 일부만 반영되는 불일치를 막는다.
    // CERT는 화면상으로는 증빙 서류 제출(submitCertProof)로만 완료하도록 유도한다(roadmap.jsp에
    // 더 이상 CERT용 완료 체크 버튼이 없음, 2026-09-30 팀 결정). 다만 이 메서드 자체는 계속 막지
    // 않는다 — syncCertAddedFromProfile(프로필에서 직접 자격증을 추가했을 때 매칭되는 CERT 단계를
    // 자동 완료)이 내부적으로 이 메서드를 그대로 쓰고 있어서, 여기서 CERT를 막으면 그 기능이 깨진다.
    public void completeStep(Long userId, Long stepId, boolean completed) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            int updatedRows = roadmapStepDao.updateCompleted(conn, stepId, userId, completed,
                    completed ? LocalDateTime.now() : null);
            // updatedRows == 0이면 소유자가 아니라서 애초에 반영이 안 된 것 — 점수도 스펙도 주면 안 된다.
            if (completed && updatedRows > 0) {
                scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                        ROADMAP_STEP_COMPLETE_POINTS);
                RoadmapStepDto step = roadmapStepDao.findById(conn, stepId);
                syncProfileOnComplete(conn, userId, step);
            }
            return null;
        });
    }

    // PROJECT 단계 완료 — 제목/설명/기술스택 + 증빙 파일 1개 이상을 함께 받아야 완료할 수 있다(팀 합의).
    // SKILL/CERT와 달리 자동으로 채울 수 있는 값이 없어 completeStep과는 별도 진입점으로 뒀다.
    // 파일은 호출부(RoadmapServlet)가 FileStorageUtil로 디스크에 이미 저장한 뒤 DocumentDto로 넘겨준다
    // — 디스크 쓰기는 DB 트랜잭션 대상이 아니라서 여기 안에서 하지 않는다.
    // 반환값 false(이미 완료됐거나 소유자가 아님)면 아무 것도 반영 안 됐다는 뜻이라, 호출부가 그때
    // 디스크에 이미 써놓은 파일을 지워야 한다 — 안 그러면 DB에 참조 없는 고아 파일이 남는다.
    public boolean completeProjectStep(Long userId, Long stepId, UserProjectDto projectInput,
            List<DocumentDto> uploadedFiles) throws SQLException {
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            throw new IllegalArgumentException("증빙 파일을 최소 1개 첨부해야 합니다.");
        }
        return TransactionUtil.runInTransaction(conn -> {
            RoadmapStepDto step = roadmapStepDao.findById(conn, stepId);
            if (step == null || !"PROJECT".equals(step.getStepType())) {
                throw new IllegalArgumentException("PROJECT 단계가 아닙니다.");
            }
            if (step.isCompleted()) {
                // 이미 완료된 단계를 다시 제출한 것 — 프로젝트가 중복 생성되지 않게 조용히 무시한다.
                return false;
            }

            int updatedRows = roadmapStepDao.updateCompleted(conn, stepId, userId, true, LocalDateTime.now());
            if (updatedRows == 0) {
                // 소유자가 아니면(다른 사용자 id) 프로젝트도 파일도 만들면 안 된다.
                return false;
            }

            projectInput.setUserId(userId);
            Long projectId = userProjectDao.insert(conn, projectInput);
            for (DocumentDto file : uploadedFiles) {
                file.setUserId(userId);
                file.setProjectId(projectId);
                documentDao.insert(conn, file);
            }

            scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                    ROADMAP_STEP_COMPLETE_POINTS);
            return true;
        });
    }

    // SKILL 단계 학습 검증(2026-09-30 팀 결정, 규칙 기반) — ENTRY(공부노트)/EXPERT(기술 설명 글)
    // 단계에 제출한 PDF(호출부가 이미 디스크에 저장하고, PdfTextUtil로 텍스트까지 뽑아서 넘겨준다)를
    // SkillProofGrader로 자동 판정한다. 통과하면 completeStep과 동일하게 점수 적립 + 프로필 반영까지
    // 한 트랜잭션으로 묶는다. 미통과(NEEDS_REVISION)면 완료 처리는 안 하고 판정 근거만 저장해서
    // 사용자가 고쳐서 다시 제출할 수 있게 한다. PDF 원본은 통과 여부와 관계없이 DOCUMENTS에 남긴다
    // (제출 이력 자체가 증빙이라 실패한 시도도 지우지 않는다).
    public SkillProofGrader.GradeResult submitSkillNote(Long userId, Long stepId, String extractedText,
            DocumentDto proofFile) throws SQLException {
        return TransactionUtil.runInTransaction(conn -> {
            RoadmapStepDto step = roadmapStepDao.findByIdForUser(conn, stepId, userId);
            if (step == null) {
                throw new IllegalArgumentException("본인의 로드맵 단계만 제출할 수 있습니다.");
            }
            if (!"SKILL".equals(step.getStepType())
                    || !(TIER_ENTRY.equals(step.getTier()) || TIER_EXPERT.equals(step.getTier()))) {
                throw new IllegalArgumentException("공부노트/기술 글 제출 대상이 아닌 단계입니다.");
            }
            if (step.isCompleted()) {
                throw new IllegalArgumentException("이미 완료된 단계입니다.");
            }

            SkillDto skill = step.getRelatedSkillId() == null ? null : skillDao.findById(step.getRelatedSkillId());
            String skillName = skill == null ? "" : skill.getSkillName();
            boolean isExpert = TIER_EXPERT.equals(step.getTier());
            String proofType = isExpert ? PROOF_TEACHING_POST : PROOF_NOTE;
            SkillProofGrader.GradeResult result = isExpert
                    ? SkillProofGrader.gradeExpertArticle(extractedText, skillName)
                    : SkillProofGrader.gradeEntryNote(extractedText, skillName);

            boolean passed = result.passed();
            roadmapStepDao.updateProof(conn, stepId, userId, proofType, extractedText, null,
                    result.status(), result.note(), passed, passed ? LocalDateTime.now() : null);

            proofFile.setUserId(userId);
            proofFile.setRoadmapStepId(stepId);
            documentDao.insert(conn, proofFile);

            if (passed) {
                scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                        ROADMAP_STEP_COMPLETE_POINTS);
                syncProfileOnComplete(conn, userId, roadmapStepDao.findById(conn, stepId));
            }
            return result;
        });
    }

    // CORE/ADVANCED SKILL 단계 완료 — "프로젝트 등록 또는 기존 프로젝트 업그레이드 + 증빙 파일"로
    // 자동 확인한다(팀 결정, 2026-09-30). completeProjectStep(PROJECT 타입 전용 단계)과 달리 이건
    // SKILL 타입 단계에 evidence_project_id로 프로젝트를 연결한다 — 별도 판정 규칙 없이 등록 자체가
    // 증빙이다. upgradeFromProjectId가 있으면 본인 소유가 맞는지 먼저 확인한다.
    // CORE/ADVANCED 구분(2026-09-30 팀 확정): CORE는 신규/업그레이드 둘 다 허용하지만, ADVANCED는
    // "심화" 단계 취지상 반드시 기존 프로젝트를 업그레이드해야 한다 — 신규 프로젝트로는 완료할 수 없다.
    public boolean submitSkillProjectStep(Long userId, Long stepId, UserProjectDto projectInput,
            List<DocumentDto> uploadedFiles, Long upgradeFromProjectId) throws SQLException {
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            throw new IllegalArgumentException("증빙 파일을 최소 1개 첨부해야 합니다.");
        }
        return TransactionUtil.runInTransaction(conn -> {
            RoadmapStepDto step = roadmapStepDao.findByIdForUser(conn, stepId, userId);
            if (step == null) {
                throw new IllegalArgumentException("본인의 로드맵 단계만 제출할 수 있습니다.");
            }
            if (!"SKILL".equals(step.getStepType())
                    || !(TIER_CORE.equals(step.getTier()) || TIER_ADVANCED.equals(step.getTier()))) {
                throw new IllegalArgumentException("프로젝트 등록 대상이 아닌 단계입니다.");
            }
            if (TIER_ADVANCED.equals(step.getTier()) && upgradeFromProjectId == null) {
                throw new IllegalArgumentException("ADVANCED 단계는 기존 프로젝트를 업그레이드해야만 완료할 수 있습니다.");
            }
            if (step.isCompleted()) {
                return false;
            }

            projectInput.setUserId(userId);
            if (upgradeFromProjectId != null) {
                UserProjectDto source = userProjectDao.findById(conn, upgradeFromProjectId, userId);
                if (source == null) {
                    throw new IllegalArgumentException("업그레이드할 프로젝트를 찾을 수 없습니다.");
                }
                projectInput.setUpgradedFromProjectId(upgradeFromProjectId);
            }
            Long projectId = userProjectDao.insert(conn, projectInput);
            for (DocumentDto file : uploadedFiles) {
                file.setUserId(userId);
                file.setProjectId(projectId);
                documentDao.insert(conn, file);
            }

            roadmapStepDao.updateProof(conn, stepId, userId, PROOF_PROJECT_LINK, null, projectId,
                    SkillProofGrader.PASSED, "프로젝트 등록/업그레이드로 자동 확인", true, LocalDateTime.now());
            scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                    ROADMAP_STEP_COMPLETE_POINTS);
            syncProfileOnComplete(conn, userId, roadmapStepDao.findById(conn, stepId));
            return true;
        });
    }

    // CERT 단계 완료 — 자격증 취득을 증명하는 서류(합격 확인서·자격증 사진 등)를 첨부해야만 완료할 수
    // 있다(2026-09-30 팀 결정, "그냥 완료 체크만 있던 걸 뒤늦게 발견해서 고침"). CORE/ADVANCED 프로젝트
    // 등록과 같은 트레이드오프 — 별도 자동 판정 규칙 없이 서류 첨부 자체를 증빙으로 신뢰한다.
    public boolean submitCertProof(Long userId, Long stepId, DocumentDto certificateFile) throws SQLException {
        if (certificateFile == null) {
            throw new IllegalArgumentException("자격증 증빙 서류를 첨부해야 합니다.");
        }
        return TransactionUtil.runInTransaction(conn -> {
            RoadmapStepDto step = roadmapStepDao.findByIdForUser(conn, stepId, userId);
            if (step == null) {
                throw new IllegalArgumentException("본인의 로드맵 단계만 제출할 수 있습니다.");
            }
            if (!"CERT".equals(step.getStepType())) {
                throw new IllegalArgumentException("자격증 단계가 아닙니다.");
            }
            if (step.isCompleted()) {
                return false;
            }

            certificateFile.setUserId(userId);
            certificateFile.setRoadmapStepId(stepId);
            documentDao.insert(conn, certificateFile);

            roadmapStepDao.updateProof(conn, stepId, userId, PROOF_CERT_DOCUMENT, null, null,
                    SkillProofGrader.PASSED, "자격증 증빙 서류 제출로 확인", true, LocalDateTime.now());
            scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                    ROADMAP_STEP_COMPLETE_POINTS);
            syncProfileOnComplete(conn, userId, roadmapStepDao.findById(conn, stepId));
            return true;
        });
    }

    // step_type별로 뭘 프로필에 반영할지 분기 — PROJECT는 completeProjectStep이 별도로 처리한다
    // (사용자 입력이 필요해서 여기서 자동으로 채울 수 없다).
    private void syncProfileOnComplete(Connection conn, Long userId, RoadmapStepDto step) throws SQLException {
        if ("SKILL".equals(step.getStepType()) && step.getRelatedSkillId() != null) {
            syncSkill(conn, userId, step.getRelatedSkillId());
        } else if ("CERT".equals(step.getStepType()) && step.getCertificationId() != null) {
            syncCertification(conn, userId, step.getCertificationId());
        }
    }

    private void syncSkill(Connection conn, Long userId, Long skillId) throws SQLException {
        int completedCount = roadmapStepDao.countCompletedByUserAndSkill(conn, userId, skillId);
        String proficiency = resolveProficiency(completedCount);

        UserSkillDto existing = userSkillDao.findByUserIdAndSkillId(conn, userId, skillId);
        if (existing != null) {
            // 완료 취소로 나중에 completedCount가 줄어도 이미 딴 숙련도보다 낮게는 절대 내리지 않는다.
            if (proficiencyRank(proficiency) > proficiencyRank(existing.getProficiency())) {
                userSkillDao.updateProficiency(conn, existing.getId(), proficiency);
            }
            return;
        }

        SkillDto skill = skillDao.findById(skillId);
        if (skill == null) {
            return; // SKILL 마스터가 삭제된 예외 상황 — 참조할 이름이 없어 만들 수 없다.
        }

        // skill_id로 못 찾아도, 사용자가 프로필에서 같은 이름을 이미 수동으로 입력해놨을 수 있다
        // (1주차 설계상 수동 입력은 skill_id가 항상 NULL이라 위 조회로는 안 걸린다). 이때 새로
        // insert하면 프로필에 같은 기술이 두 줄로 보이니, 그 행에 skill_id를 채우는 쪽으로 합친다.
        UserSkillDto byName = userSkillDao.findByUserIdAndRawInput(conn, userId, skill.getSkillName());
        if (byName != null) {
            // >= 로 동점(둘 다 0점, 즉 byName.proficiency가 미입력 null인 경우 포함)이면 새로 계산한
            // proficiency(항상 null이 아님)를 쓴다 — 안 그러면 null이 그대로 남는다.
            String mergedProficiency = proficiencyRank(proficiency) >= proficiencyRank(byName.getProficiency())
                    ? proficiency : byName.getProficiency();
            userSkillDao.attachSkillId(conn, byName.getId(), skillId, mergedProficiency);
            return;
        }

        UserSkillDto userSkill = new UserSkillDto();
        userSkill.setUserId(userId);
        userSkill.setSkillId(skillId);
        userSkill.setRawInput(skill.getSkillName());
        userSkill.setProficiency(proficiency);
        userSkillDao.insert(conn, userSkill);
    }

    private String resolveProficiency(int completedCount) {
        if (completedCount >= PROFICIENCY_ADVANCED_THRESHOLD) {
            return PROFICIENCY_ADVANCED;
        }
        if (completedCount >= PROFICIENCY_INTERMEDIATE_THRESHOLD) {
            return PROFICIENCY_INTERMEDIATE;
        }
        return PROFICIENCY_BEGINNER;
    }

    private int proficiencyRank(String proficiency) {
        if (PROFICIENCY_ADVANCED.equals(proficiency)) {
            return 2;
        }
        if (PROFICIENCY_INTERMEDIATE.equals(proficiency)) {
            return 1;
        }
        return 0;
    }

    private void syncCertification(Connection conn, Long userId, Long certificationId) throws SQLException {
        CertificationDto cert = certificationDao.findById(conn, certificationId);
        if (cert == null || userSpecDao.existsActiveByUserAndTitle(conn, userId, "CERT", cert.getCertName())) {
            return;
        }
        UserSpecDto spec = new UserSpecDto();
        spec.setUserId(userId);
        spec.setSpecType("CERT");
        spec.setTitle(cert.getCertName());
        spec.setIssuer(cert.getIssuer());
        spec.setAcquiredDate(LocalDateTime.now().toLocalDate());
        userSpecDao.insert(conn, spec);
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
        // ENTRY 티어의 PROJECT 단계 안내 문구를 미리 만들어둔다 — LLM 호출은 DB 트랜잭션을 열기
        // 전에 끝내야 한다(claude.md: 외부 API 호출에 타임아웃을 직접 두고, 느리거나 실패해도
        // DB 커넥션을 물고 있으면 안 됨). 실패해도 로드맵 생성 자체는 막지 않는다(FR-111).
        List<GapAnalysisItemDto> entryTierSkills = chunkForTier(rankedMissing, 0);
        String projectReason = entryTierSkills.isEmpty() ? null : buildProjectReason(job, entryTierSkills);
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

            // 점수 내림차순으로 정렬된 부족 기술을 티어당 5개씩(마지막 EXPERT는 남은 전부) 잘라 담는다
            // — "끝없는 여정" 구조(db-design.md), ENTRY가 끝나야 CORE가, CORE가 끝나야 ADVANCED가
            // 열리는 계단식 잠금은 computeProgress에서 매 조회 시 계산한다.
            int order = 1;
            for (int tierIndex = 0; tierIndex < SKILL_TIER_ORDER.size(); tierIndex++) {
                String tier = SKILL_TIER_ORDER.get(tierIndex);
                List<GapAnalysisItemDto> tierSkills = chunkForTier(rankedMissing, tierIndex);

                if (TIER_ENTRY.equals(tier)) {
                    // CERT/PROJECT는 여정의 첫 진입점 성격이라 ENTRY 티어에만 둔다 — 뒤 티어는 SKILL로만 구성.
                    if (suggestedCert != null) {
                        order = insertStep(conn, roadmapId, order, "CERT", tier, suggestedCert.getId(), null,
                                buildCertReason(job, suggestedCert));
                        // FR-71 D-day 자동 생성 — 이 자격증에 접수 마감 전 가까운 시험 회차가 있으면
                        // 알림을 자동으로 만든다. 일정이 없으면(수집 전·상시시험) 조용히 넘어간다.
                        ddayAutoGenerationService.autoCreateCertDday(conn, userId, suggestedCert.getId());
                    }
                    if (!tierSkills.isEmpty()) {
                        order = insertStep(conn, roadmapId, order, "PROJECT", tier, null, null, projectReason);
                    }
                }
                for (GapAnalysisItemDto item : tierSkills) {
                    String importance = importanceOf(analysis.getJobId(), item.getSkillId());
                    boolean alreadyLearned = roadmapStepDao.countCompletedByUserAndSkill(conn, userId, item.getSkillId()) > 0;
                    order = insertStep(conn, roadmapId, order, "SKILL", tier, null, item.getSkillId(),
                            buildSkillReason(item.getSkillId(), importance), alreadyLearned);
                }
            }

            return roadmapId;
        });
    }

    // rankedMissing을 SKILL_TIER_ORDER 순서대로 MAX_SKILL_STEPS_PER_TIER개씩 잘라준다.
    // 마지막 티어(EXPERT)는 남은 걸 전부 받아서 기술이 버려지는 일이 없게 한다.
    private List<GapAnalysisItemDto> chunkForTier(List<GapAnalysisItemDto> rankedMissing, int tierIndex) {
        int from = Math.min(tierIndex * MAX_SKILL_STEPS_PER_TIER, rankedMissing.size());
        boolean lastTier = tierIndex == SKILL_TIER_ORDER.size() - 1;
        int to = lastTier ? rankedMissing.size() : Math.min(from + MAX_SKILL_STEPS_PER_TIER, rankedMissing.size());
        return rankedMissing.subList(from, to);
    }

    private int insertStep(Connection conn, Long roadmapId, int order, String stepType, String tier,
                            Long certificationId, Long relatedSkillId, String reason) throws SQLException {
        return insertStep(conn, roadmapId, order, stepType, tier, certificationId, relatedSkillId, reason, false);
    }

    // alreadyDone: 재분석으로 새 버전이 만들어질 때 예전에 이미 완료했던 스킬을 다시 미완료로 되돌리지
    // 않기 위한 승계 플래그(팀 합의, 2026-09-23) — "딴 걸 또 따라고 시키면 안 된다". CERT는
    // findSuggestedCertification이 이미 보유한 자격증을 애초에 후보에서 빼기 때문에 여기서 따로
    // 다룰 필요가 없다. 승계된 단계는 새로 완료한 게 아니라서 점수를 다시 주지 않는다(호출부 참고).
    private int insertStep(Connection conn, Long roadmapId, int order, String stepType, String tier,
                            Long certificationId, Long relatedSkillId, String reason, boolean alreadyDone)
            throws SQLException {
        RoadmapStepDto step = new RoadmapStepDto();
        step.setRoadmapId(roadmapId);
        step.setStepOrder(order);
        step.setStepType(stepType);
        step.setTier(tier);
        step.setCertificationId(certificationId);
        step.setRelatedSkillId(relatedSkillId);
        step.setReason(reason);
        step.setCompleted(alreadyDone);
        step.setCompletedAt(alreadyDone ? LocalDateTime.now() : null);
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

    // LLM(ProjectIdeaService)이 목표 직무 + 부족 기술로 구체적인 프로젝트 아이디어를 만들어준다.
    // 실패(API 키 없음·타임아웃·응답 형식 오류 등)해도 로드맵 생성 자체를 막으면 안 되므로(FR-111),
    // 여기서 예외를 잡아 기존 고정 문구로 조용히 대체한다 — 2026-09-30, 집 PC 작업에서 신규 도입.
    private String buildProjectReason(JobDto job, List<GapAnalysisItemDto> rankedMissing) throws SQLException {
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
        try {
            ProjectIdeaService.ProjectIdea idea = projectIdeaService.suggest(
                    job == null || job.getJobName() == null ? "이 직무" : job.getJobName(), names);
            return "💡 " + idea.title() + " — " + idea.description();
        } catch (Exception e) {
            String topSkills = String.join(", ", names);
            return "부족한 기술을 실제로 다뤄볼 프로젝트를 진행해보세요. 우선순위가 높은 기술: " + topSkills;
        }
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
