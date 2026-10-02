package com.specodyssey.service;

import com.specodyssey.dao.GapAnalysisDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.util.GroqLlmClient;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 로드맵 생성 · 조회 · 완료 체크 — 공개 창구.
 * 구현은 RoadmapGenerator(생성) · RoadmapCompletionService(완료·프로필 반영) · RoadmapSubmissionService(제출 완료) ·
 * RoadmapReviewService(복습) · RoadmapProgressCalculator(진행도)로 나뉘어 있다.
 * 관련 요구사항: FR-32(순서 있는 로드맵) · FR-33(단계별 이유) · FR-36(완료 체크 → 진행도) · FR-37(재분석 시 새 버전)
 *
 * 격차 분석(GAP_ANALYSIS)은 다른 담당자의 기능이지만, 이 서비스는 GapAnalysisDao/GapAnalysisItemDao를
 * 읽기 전용으로만 의존한다 — 그쪽에 Service/Controller가 없어도 DB에 데이터만 있으면 동작한다.
 *
 * 단계 우선순위 규칙 (팀 합의):
 *   1) JOB_REQUIRED_SKILL.importance가 REQUIRED면 +2점, PREFERRED면 +1점, 정보 없으면 0점
 *   2) 자격증(CERT) 단계는 커버리지를 정량화할 매핑 테이블이 없어 점수 경쟁에 넣지 않고 있으면 항상 1번으로 고정
 *   3) 프로젝트(PROJECT) 단계도 1개 생성해 2번에 고정 (상위 점수 기술을 반영한 안내 문구만 제공 — 구체 프로젝트 추천은 LLM 붙을 때 고도화)
 *   4) 점수 상위 기술 5개(라운드)를 골라, 기술마다 입문→핵심→심화→전문가 SKILL 단계를 티어 순으로 배치
 *      (부족 기술이 5개 미만이면 직무 요구 기술로 보충 — SkillDeepenService)
 */
public class RoadmapService {

    public static class NoGapAnalysisException extends Exception {
        public NoGapAnalysisException(String message) {
            super(message);
        }
    }

    private final GapAnalysisDao gapAnalysisDao = new GapAnalysisDao();
    private final JobDao jobDao = new JobDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    // 실제 일은 아래 협력 클래스가 한다 — 이 클래스는 컨트롤러·테스트가 쓰던 공개 메서드를 그대로 유지하는 창구다.
    private final RoadmapGenerator generator;
    private final RoadmapCompletionService completion = new RoadmapCompletionService();
    private final RoadmapSubmissionService submissions = new RoadmapSubmissionService();
    private final RoadmapReviewService reviews = new RoadmapReviewService();
    private final RoadmapProgressCalculator progressCalculator = new RoadmapProgressCalculator();

    public RoadmapService() {
        this(new ProjectIdeaService(), new SkillDeepenService(GroqLlmClient.fromConfig()));
    }

    // 테스트에서 StubLlmClient 기반 ProjectIdeaService를 넣어 실제 Groq 호출을 피하려고 열어둔 생성자
    // (2026-10-01, RoadmapServiceTest가 매번 실제 LLM을 불러 429로 1시간 넘게 걸리던 문제).
    // 기술 보충(SkillDeepenService)은 LLM 없이 중요도 순 대체만 쓴다.
    public RoadmapService(ProjectIdeaService projectIdeaService) {
        this(projectIdeaService, new SkillDeepenService(null));
    }

    public RoadmapService(ProjectIdeaService projectIdeaService, SkillDeepenService skillDeepenService) {
        this.generator = new RoadmapGenerator(projectIdeaService, skillDeepenService);
    }

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

    // 완료·프로필 동기화 → RoadmapCompletionService
    public void syncCertAddedFromProfile(Long userId, String certTitle) throws SQLException {
        completion.syncCertAddedFromProfile(userId, certTitle);
    }

    // 완료·프로필 동기화 → RoadmapCompletionService
    public void syncSkillAddedFromProfile(Long userId, String rawSkillName) throws SQLException {
        completion.syncSkillAddedFromProfile(userId, rawSkillName);
    }

    // 진행도 계산 → RoadmapProgressCalculator
    public RoadmapProgress computeProgress(List<RoadmapStepDto> steps) {
        return progressCalculator.computeProgress(steps);
    }

    // 진행도 계산 → RoadmapProgressCalculator
    public TierProgress findNewlyCompletedTier(RoadmapProgress before, RoadmapProgress after) {
        return progressCalculator.findNewlyCompletedTier(before, after);
    }

    // 완료·프로필 동기화 → RoadmapCompletionService
    public void completeStep(Long userId, Long stepId, boolean completed) throws SQLException {
        completion.completeStep(userId, stepId, completed);
    }

    // 제출 완료 → RoadmapSubmissionService
    public boolean completeProjectStep(Long userId, Long stepId, ProjectSubmission submission) throws SQLException {
        return submissions.completeProjectStep(userId, stepId, submission);
    }

    // 제출 완료 → RoadmapSubmissionService
    public SkillProofGrader.GradeResult submitSkillNote(Long userId, Long stepId, String extractedText, DocumentDto proofFile) throws SQLException {
        return submissions.submitSkillNote(userId, stepId, extractedText, proofFile);
    }

    // 제출 완료 → RoadmapSubmissionService
    public boolean submitSkillProjectStep(Long userId, Long stepId, ProjectSubmission submission, Long upgradeFromProjectId) throws SQLException {
        return submissions.submitSkillProjectStep(userId, stepId, submission, upgradeFromProjectId);
    }

    // 제출 완료 → RoadmapSubmissionService
    public boolean submitCertProof(Long userId, Long stepId, DocumentDto certificateFile) throws SQLException {
        return submissions.submitCertProof(userId, stepId, certificateFile);
    }

    // 복습 → RoadmapReviewService
    public int appendDueReviews(Long userId, LocalDateTime now) throws SQLException {
        return reviews.appendDueReviews(userId, now);
    }

    // 복습 → RoadmapReviewService
    public int completeReview(Long userId, Long stepId, String note) throws SQLException {
        return reviews.completeReview(userId, stepId, note);
    }

    // 로드맵 생성 → RoadmapGenerator
    public Long generate(Long userId) throws SQLException, NoGapAnalysisException {
        return generator.generate(userId);
    }

    // 테스트가 순수 계산을 직접 확인하는 용도 — 실제 규칙은 RoadmapReviewService에 있다
    static int reviewIntervalDays(String highestTier) {
        return RoadmapReviewService.reviewIntervalDays(highestTier);
    }

    static int reviewPoints(int priorReviews) {
        return RoadmapReviewService.reviewPoints(priorReviews);
    }
}
