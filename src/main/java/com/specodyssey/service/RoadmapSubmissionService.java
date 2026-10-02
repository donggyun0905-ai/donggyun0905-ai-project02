package com.specodyssey.service;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.TransactionUtil;
import java.sql.SQLException;
import java.time.LocalDateTime;
import static com.specodyssey.service.RoadmapConstants.*;

/**
 * 로드맵 단계 제출 완료 — 프로젝트(PROJECT)·기술 프로젝트(CORE/ADVANCED)·공부노트·자격증 증빙.
 * RoadmapService 안에 있던 제출 부분을 그대로 옮겼다.
 */
public class RoadmapSubmissionService {

    private final SkillDao skillDao = new SkillDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final ProjectSubmissionService projectSubmissionService = new ProjectSubmissionService();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final ScoreService scoreService = new ScoreService();
    private final StepPointCalculator stepPoints = new StepPointCalculator();
    private final RoadmapCompletionService completion = new RoadmapCompletionService();

    // PROJECT 단계 완료 — 프로젝트 정보 + 문서 체크리스트(README·실행 화면 필수) + 기술 활용 설명서를 받는다
    // (개발일지 4-4). SKILL/CERT와 달리 자동으로 채울 수 있는 값이 없어 completeStep과는 별도 진입점으로 뒀다.
    // 파일은 호출부(RoadmapServlet)가 FileStorageUtil로 디스크에 이미 저장한 뒤 넘겨준다
    // — 디스크 쓰기는 DB 트랜잭션 대상이 아니라서 여기 안에서 하지 않는다.
    // 완료를 취소해도 프로젝트·서류는 그대로 남고(파일 삭제는 서류 보관함에서), 다시 완료하면
    // 단계에 연결된 기존 프로젝트(evidence_project_id)를 갱신한다 — 프로젝트가 중복으로 생기지 않는다.
    // 반환값 false(이미 완료됐거나 소유자가 아님)면 아무 것도 반영 안 됐다는 뜻이라, 호출부가 그때
    // 디스크에 이미 써놓은 파일을 지워야 한다 — 안 그러면 DB에 참조 없는 고아 파일이 남는다.
    public boolean completeProjectStep(Long userId, Long stepId, ProjectSubmission submission) throws SQLException {
        return TransactionUtil.runInTransaction(conn -> {
            RoadmapStepDto step = roadmapStepDao.findByIdForUser(conn, stepId, userId);
            if (step == null) {
                return false;
            }
            if (!"PROJECT".equals(step.getStepType())) {
                throw new IllegalArgumentException("PROJECT 단계가 아닙니다.");
            }
            if (step.isCompleted()) {
                // 이미 완료된 단계를 다시 제출한 것 — 프로젝트가 중복 생성되지 않게 조용히 무시한다.
                return false;
            }
            projectSubmissionService.validate(submission, step.getEvidenceProjectId());

            int updatedRows = roadmapStepDao.updateCompleted(conn, stepId, userId, true, LocalDateTime.now());
            if (updatedRows == 0) {
                return false;
            }
            Long projectId = projectSubmissionService.save(conn, userId, stepId, step.getEvidenceProjectId(),
                    null, submission);
            roadmapStepDao.setEvidenceProject(conn, stepId, userId, projectId);

            scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                    stepPoints.pointsFor(conn, userId, step));
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
                        stepPoints.pointsFor(conn, userId, step));
                completion.syncProfileOnComplete(conn, userId, roadmapStepDao.findById(conn, stepId));
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
    public boolean submitSkillProjectStep(Long userId, Long stepId, ProjectSubmission submission,
            Long upgradeFromProjectId) throws SQLException {
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

            if (upgradeFromProjectId != null
                    && userProjectDao.findById(conn, upgradeFromProjectId, userId) == null) {
                throw new IllegalArgumentException("업그레이드할 프로젝트를 찾을 수 없습니다.");
            }
            // 완료를 취소했다가 다시 제출하는 경우 — 단계에 연결된 프로젝트를 갱신한다(새로 만들지 않는다)
            Long existingProjectId = step.getEvidenceProjectId();
            projectSubmissionService.validate(submission, existingProjectId);
            Long projectId = projectSubmissionService.save(conn, userId, stepId, existingProjectId,
                    upgradeFromProjectId, submission);

            roadmapStepDao.updateProof(conn, stepId, userId, PROOF_PROJECT_LINK, null, projectId,
                    SkillProofGrader.PASSED, "프로젝트 등록/업그레이드로 자동 확인", true, LocalDateTime.now());
            scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                    stepPoints.pointsFor(conn, userId, step));
            completion.syncProfileOnComplete(conn, userId, roadmapStepDao.findById(conn, stepId));
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
                    stepPoints.pointsFor(conn, userId, step));
            completion.syncProfileOnComplete(conn, userId, roadmapStepDao.findById(conn, stepId));
            return true;
        });
    }
}
