package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.TransactionUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import static com.specodyssey.service.RoadmapReviewService.STEP_TYPE_REVIEW;
import static com.specodyssey.service.RoadmapConstants.*;

/**
 * 로드맵 단계 완료 체크 + 완료에 따른 프로필(스킬·자격증) 반영. 관련 요구사항: FR-36
 * RoadmapService 안에 있던 완료·동기화 부분을 그대로 옮겼다.
 */
public class RoadmapCompletionService {

    // SKILL 단계 완료 → USER_SKILLS 숙련도 자동 승급 기준 (팀 합의, 2026-09-23).
    // 완료를 취소해도 이미 오른 숙련도는 안 내린다 — 점수 정책과 같은 원칙.
    private static final int PROFICIENCY_INTERMEDIATE_THRESHOLD = 10;
    private static final int PROFICIENCY_ADVANCED_THRESHOLD = 30;
    private static final String PROFICIENCY_BEGINNER = "BEGINNER";
    private static final String PROFICIENCY_INTERMEDIATE = "INTERMEDIATE";
    private static final String PROFICIENCY_ADVANCED = "ADVANCED";

    private final CertificationDao certificationDao = new CertificationDao();
    private final SkillDao skillDao = new SkillDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final ScoreService scoreService = new ScoreService();
    private final StepPointCalculator stepPoints = new StepPointCalculator();

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
                // 기술 하나에 입문→핵심→심화→전문가 단계가 모두 있으므로, 프로필에 추가한 것은 가장 앞의
                // 미완료 단계(입문, step_order 순) 하나만 완료로 본다 — 프로젝트·글까지 대신 끝낸 걸로
                // 치면 안 된다. 옛 로드맵(기술당 단계 1개)에서도 그 하나가 그대로 완료된다.
                completeStep(userId, step.getId(), true);
                return;
            }
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
            RoadmapStepDto target = roadmapStepDao.findByIdForUser(conn, stepId, userId);
            if (target != null && UPKEEP_STEP_TYPES.contains(target.getStepType())) {
                // 복습은 복습 기록을 내야만 끝난다(completeReview) — 체크만으로 점수를 받는 길을 막는다.
                throw new IllegalArgumentException("복습·업데이트·트렌딩 학습 단계는 기록을 제출해야 완료할 수 있습니다.");
            }
            int updatedRows = roadmapStepDao.updateCompleted(conn, stepId, userId, completed,
                    completed ? LocalDateTime.now() : null);
            // updatedRows == 0이면 소유자가 아니라서 애초에 반영이 안 된 것 — 점수도 스펙도 주면 안 된다.
            if (completed && updatedRows > 0) {
                scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                        stepPoints.pointsFor(conn, userId, target));
                RoadmapStepDto step = roadmapStepDao.findById(conn, stepId);
                syncProfileOnComplete(conn, userId, step);
            }
            return null;
        });
    }

    // step_type별로 뭘 프로필에 반영할지 분기 — PROJECT는 completeProjectStep이 별도로 처리한다
    // (사용자 입력이 필요해서 여기서 자동으로 채울 수 없다).
    void syncProfileOnComplete(Connection conn, Long userId, RoadmapStepDto step) throws SQLException {
        if ("SKILL".equals(step.getStepType()) && step.getRelatedSkillId() != null) {
            syncSkill(conn, userId, step.getRelatedSkillId(), step.getTier());
        } else if ("CERT".equals(step.getStepType()) && step.getCertificationId() != null) {
            syncCertification(conn, userId, step.getCertificationId());
        }
    }

    // 숙련도 = 완료 횟수 기준(옛 규칙)과 도달한 단계 기준 중 높은 쪽 — 입문 노트 BEGINNER, 핵심·심화 프로젝트
    // INTERMEDIATE, 전문가 글 ADVANCED(2026-10-01). 한 기술이 로드맵에 한 번씩만 나오면 횟수 기준은
    // 도달할 수 없어서 단계 기준을 추가했다.
    private void syncSkill(Connection conn, Long userId, Long skillId, String tier) throws SQLException {
        int completedCount = roadmapStepDao.countCompletedByUserAndSkill(conn, userId, skillId);
        String byCount = resolveProficiency(completedCount);
        String byTier = proficiencyForTier(tier);
        String proficiency = proficiencyRank(byTier) > proficiencyRank(byCount) ? byTier : byCount;

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

    private String proficiencyForTier(String tier) {
        if (TIER_EXPERT.equals(tier)) {
            return PROFICIENCY_ADVANCED;
        }
        if (TIER_CORE.equals(tier) || TIER_ADVANCED.equals(tier)) {
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
}
