package com.specodyssey.service;

import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.TrendCollectDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.TrendTechDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.TransactionUtil;
import com.specodyssey.util.UrlRules;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.specodyssey.service.RoadmapConstants.*;

/**
 * 끝없는 로드맵의 "유지·성장" 단계 — 시간이 지나면 기존 여정 뒤에 이어 붙는다.
 * (기술 복습은 RoadmapReviewService, 여기는 그 밖의 세 가지)
 *
 * - 프로젝트 업데이트(PROJECT_UPDATE): 프로젝트를 마지막으로 손본 지 90일이 지나면. README·회고를 보강하거나 기능을 더한 기록을 낸다.
 * - 기술 글 업데이트(ARTICLE_UPDATE): 전문가(EXPERT) 단계에서 쓴 기술 글이 150일 지나면. 보강한 글(PDF)을 다시 내서 규칙 판정을 통과해야 한다.
 * - 트렌딩 학습(TREND_STUDY): 목표 직무와 연결된 요즘 뜨는 기술 중 아직 모르는 것을, 로드맵을 만든 뒤 30일마다 하나씩.
 *
 * 점수는 복습과 같은 감쇠 규칙(같은 대상을 다시 할수록 줄어든다)이고, 트렌딩 학습은 매번 새 주제라 감쇠 없이 일정하다.
 * 로드맵 화면을 열 때(세션당 하루 한 번) 만들며 DB 조회만 한다 — 별도 스케줄러·LLM 비용이 없다.
 */
public class RoadmapUpkeepService {

    static final int PROJECT_UPDATE_DAYS = 90;
    static final int ARTICLE_UPDATE_DAYS = 150; // 기술 복습(전문가 120일)과 같은 날 겹쳐 생기지 않게 어긋나게 둔다
    static final int TREND_STUDY_DAYS = 30;
    static final int MAX_OPEN_PROJECT_UPDATES = 2;
    static final int MAX_OPEN_ARTICLE_UPDATES = 2;
    static final int MAX_OPEN_TREND_STUDIES = 1;
    static final int PROJECT_UPDATE_POINTS_BASE = 60;
    static final int ARTICLE_UPDATE_POINTS_BASE = 60;
    static final int TREND_STUDY_POINTS = 40;
    static final int POINTS_DECAY = 10;
    static final int POINTS_MIN = 5;
    static final int NOTE_MIN_LENGTH = 20;
    static final int NOTE_MAX_LENGTH = 1000;
    static final String TREND_REASON_PREFIX = "📈 트렌딩 학습 — ";
    private static final String PROOF_UPKEEP_NOTE = "UPKEEP_NOTE";
    private static final int TREND_CANDIDATES = 10;
    private static final int TREND_SUMMARY_MAX = 200;

    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final UserDao userDao = new UserDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final SkillDao skillDao = new SkillDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final TrendCollectDao trendCollectDao = new TrendCollectDao();
    private final ScoreService scoreService = new ScoreService();
    private final RoadmapStepWriter stepWriter = new RoadmapStepWriter();

    /** 같은 대상을 prior번 이미 했을 때의 점수 — base에서 10점씩 줄고 최저 5점 */
    static int decayedPoints(int base, int prior) {
        return Math.max(POINTS_MIN, base - POINTS_DECAY * Math.max(0, prior));
    }

    static int pointsFor(String stepType, int prior) {
        switch (stepType) {
            case STEP_TYPE_PROJECT_UPDATE:
                return decayedPoints(PROJECT_UPDATE_POINTS_BASE, prior);
            case STEP_TYPE_ARTICLE_UPDATE:
                return decayedPoints(ARTICLE_UPDATE_POINTS_BASE, prior);
            case STEP_TYPE_TREND_STUDY:
                return TREND_STUDY_POINTS;
            default:
                throw new IllegalArgumentException("점수를 정할 수 없는 단계 종류입니다: " + stepType);
        }
    }

    /**
     * 주기가 지난 유지·성장 단계를 대표 로드맵 맨 뒤에 만든다. 이미 열려 있는 같은 대상의 단계는 건너뛰고, 종류마다
     * 열려 있는 개수에 상한이 있다. 몇 번을 불러도 같은 결과(멱등)다.
     * @return 새로 만든 단계 수
     */
    public int appendDueUpkeep(Long userId, LocalDateTime now) throws SQLException {
        RoadmapDto primary = roadmapDao.findPrimaryByUserId(userId);
        if (primary == null) {
            return 0;
        }
        List<RoadmapStepDto> inRoadmap = roadmapStepDao.findByRoadmapId(primary.getId());
        int maxOrder = 0;
        for (RoadmapStepDto step : inRoadmap) {
            maxOrder = Math.max(maxOrder, step.getStepOrder() == null ? 0 : step.getStepOrder());
        }
        List<RoadmapStepDto> upkeepRows = roadmapStepDao.findByUserAndTypes(userId,
                List.of(STEP_TYPE_PROJECT_UPDATE, STEP_TYPE_ARTICLE_UPDATE, STEP_TYPE_TREND_STUDY));

        List<PlannedStep> planned = new ArrayList<>();
        planProjectUpdates(userId, now, upkeepRows, planned);
        planArticleUpdates(userId, now, upkeepRows, planned);
        planTrendStudy(userId, now, primary, upkeepRows, planned);
        if (planned.isEmpty()) {
            return 0;
        }

        final int startOrder = maxOrder + 1;
        return TransactionUtil.runInTransaction(conn -> {
            int order = startOrder;
            for (PlannedStep p : planned) {
                order = stepWriter.insertStep(conn, primary.getId(), order, p.stepType, RoadmapReviewService.TIER_REVIEW, null, p.skillId,
                        p.reason, false, p.projectId);
            }
            return planned.size();
        });
    }

    private static final class PlannedStep {
        final String stepType;
        final Long skillId;
        final Long projectId;
        final String reason;

        PlannedStep(String stepType, Long skillId, Long projectId, String reason) {
            this.stepType = stepType;
            this.skillId = skillId;
            this.projectId = projectId;
            this.reason = reason;
        }
    }

    private static Set<Long> openIds(List<RoadmapStepDto> rows, String type, boolean byProject) {
        Set<Long> ids = new HashSet<>();
        for (RoadmapStepDto row : rows) {
            if (type.equals(row.getStepType()) && !row.isCompleted()) {
                ids.add(byProject ? row.getEvidenceProjectId() : row.getRelatedSkillId());
            }
        }
        return ids;
    }

    private static int openCount(List<RoadmapStepDto> rows, String type) {
        return (int) rows.stream().filter(r -> type.equals(r.getStepType()) && !r.isCompleted()).count();
    }

    private void planProjectUpdates(Long userId, LocalDateTime now, List<RoadmapStepDto> rows, List<PlannedStep> out)
            throws SQLException {
        int room = MAX_OPEN_PROJECT_UPDATES - openCount(rows, STEP_TYPE_PROJECT_UPDATE);
        if (room <= 0) {
            return;
        }
        Set<Long> open = openIds(rows, STEP_TYPE_PROJECT_UPDATE, true);
        Map<Long, LocalDateTime> lastUpdate = new HashMap<>();
        for (RoadmapStepDto row : rows) {
            if (STEP_TYPE_PROJECT_UPDATE.equals(row.getStepType()) && row.isCompleted() && row.getCompletedAt() != null
                    && row.getEvidenceProjectId() != null) {
                lastUpdate.merge(row.getEvidenceProjectId(), row.getCompletedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        List<Object[]> due = new ArrayList<>(); // {project, lastActivity}
        for (UserProjectDto project : userProjectDao.findByUserId(userId)) {
            if (open.contains(project.getId())) {
                continue;
            }
            LocalDateTime last = project.getUpdatedAt() != null ? project.getUpdatedAt() : project.getCreatedAt();
            LocalDateTime recorded = lastUpdate.get(project.getId());
            if (recorded != null && (last == null || recorded.isAfter(last))) {
                last = recorded;
            }
            if (last != null && !now.isBefore(last.plusDays(PROJECT_UPDATE_DAYS))) {
                due.add(new Object[] {project, last});
            }
        }
        due.sort(Comparator.comparing(o -> (LocalDateTime) o[1])); // 가장 오래 손 안 댄 것부터
        for (Object[] o : due) {
            if (room-- <= 0) {
                break;
            }
            UserProjectDto project = (UserProjectDto) o[0];
            long days = Duration.between((LocalDateTime) o[1], now).toDays();
            out.add(new PlannedStep(STEP_TYPE_PROJECT_UPDATE, null, project.getId(),
                    "🛠 " + project.getTitle() + " 프로젝트 업데이트 — 마지막으로 손본 지 " + days
                            + "일이 지났어요. README·회고를 보강하거나 기능·리팩터링을 더하고, 무엇을 바꿨는지 적어 주세요."));
        }
    }

    private void planArticleUpdates(Long userId, LocalDateTime now, List<RoadmapStepDto> rows, List<PlannedStep> out)
            throws SQLException {
        int room = MAX_OPEN_ARTICLE_UPDATES - openCount(rows, STEP_TYPE_ARTICLE_UPDATE);
        if (room <= 0) {
            return;
        }
        Set<Long> open = openIds(rows, STEP_TYPE_ARTICLE_UPDATE, false);
        // 기술마다: 실제로 글(PDF)을 내서 끝낸 전문가 단계의 완료 시각 + 이후 글 업데이트를 끝낸 시각 중 가장 늦은 것
        Map<Long, LocalDateTime> lastDone = new HashMap<>();
        for (RoadmapStepDto row : roadmapStepDao.findByUserAndTypes(userId, List.of("SKILL", STEP_TYPE_ARTICLE_UPDATE))) {
            if (!row.isCompleted() || row.getCompletedAt() == null || row.getRelatedSkillId() == null) {
                continue;
            }
            boolean article = "SKILL".equals(row.getStepType())
                    ? TIER_EXPERT.equals(row.getTier()) && PROOF_TEACHING_POST.equals(row.getProofType())
                    : true;
            if (article) {
                lastDone.merge(row.getRelatedSkillId(), row.getCompletedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        List<Map.Entry<Long, LocalDateTime>> due = lastDone.entrySet().stream()
                .filter(e -> !open.contains(e.getKey()) && !now.isBefore(e.getValue().plusDays(ARTICLE_UPDATE_DAYS)))
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toList());
        for (Map.Entry<Long, LocalDateTime> e : due) {
            if (room-- <= 0) {
                break;
            }
            SkillDto skill = skillDao.findById(e.getKey());
            String name = skill == null ? "기술" : skill.getSkillName();
            long days = Duration.between(e.getValue(), now).toDays();
            out.add(new PlannedStep(STEP_TYPE_ARTICLE_UPDATE, e.getKey(), null,
                    "📝 " + name + " 기술 글 업데이트 — 글을 낸 지 " + days
                            + "일이 지났어요. 바뀐 내용·새로 알게 된 점을 보강한 글(PDF)을 다시 제출해 주세요."));
        }
    }

    private void planTrendStudy(Long userId, LocalDateTime now, RoadmapDto primary, List<RoadmapStepDto> rows,
            List<PlannedStep> out) throws SQLException {
        if (openCount(rows, STEP_TYPE_TREND_STUDY) >= MAX_OPEN_TREND_STUDIES) {
            return;
        }
        LocalDateTime reference = primary.getCreatedAt();
        Set<String> studied = new HashSet<>();
        for (RoadmapStepDto row : rows) {
            if (STEP_TYPE_TREND_STUDY.equals(row.getStepType())) {
                if (row.getCreatedAt() != null && (reference == null || row.getCreatedAt().isAfter(reference))) {
                    reference = row.getCreatedAt();
                }
                if (row.getReason() != null && row.getReason().startsWith(TREND_REASON_PREFIX)) {
                    int colon = row.getReason().indexOf(':', TREND_REASON_PREFIX.length());
                    if (colon > 0) {
                        studied.add(row.getReason().substring(TREND_REASON_PREFIX.length(), colon).trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        if (reference == null || now.isBefore(reference.plusDays(TREND_STUDY_DAYS))) {
            return;
        }
        UserDto user = userDao.findById(userId);
        if (user == null || user.getDesiredJobId() == null) {
            return;
        }
        Set<String> known = new HashSet<>();
        for (UserSkillDto skill : userSkillDao.findByUserId(userId)) {
            if (skill.getRawInput() != null) {
                known.add(skill.getRawInput().trim().toLowerCase(Locale.ROOT));
            }
        }
        for (TrendTechDto tech : trendCollectDao.findTopByJobId(user.getDesiredJobId(), TREND_CANDIDATES)) {
            String key = tech.getTechName() == null ? "" : tech.getTechName().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || known.contains(key) || studied.contains(key) || key.contains(":")) {
                continue;
            }
            String summary = tech.getSummary() == null ? "" : tech.getSummary().trim();
            if (summary.length() > TREND_SUMMARY_MAX) {
                summary = summary.substring(0, TREND_SUMMARY_MAX) + "…";
            }
            String source = UrlRules.isWebUrl(tech.getSourceUrl()) ? " (출처: " + tech.getSourceUrl() + ")" : "";
            out.add(new PlannedStep(STEP_TYPE_TREND_STUDY, null, null,
                    TREND_REASON_PREFIX + tech.getTechName().trim() + ": " + (summary.isEmpty() ? "요즘 뜨는 기술이에요." : summary)
                            + source + " 공식 문서·튜토리얼로 직접 해 보고 배운 점을 기록해 주세요."));
            return; // 한 번에 하나씩
        }
    }

    /**
     * 프로젝트 업데이트·트렌딩 학습 단계를 끝낸다 — 기록(NOTE_MIN_LENGTH자 이상)을 내야 한다. 프로젝트 업데이트는 프로젝트의
     * "마지막으로 손본 시각"도 지금으로 옮긴다. 점수는 pointsFor.
     * @return 받은 점수. 이미 끝났거나 소유자가 아니면 0
     */
    public int completeUpkeep(Long userId, Long stepId, String note) throws SQLException {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() < NOTE_MIN_LENGTH) {
            throw new IllegalArgumentException("기록을 " + NOTE_MIN_LENGTH + "자 이상 적어주세요.");
        }
        if (trimmed.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("기록은 " + NOTE_MAX_LENGTH + "자 이내로 적어주세요.");
        }
        return TransactionUtil.runInTransaction(conn -> {
            RoadmapStepDto step = roadmapStepDao.findByIdForUser(conn, stepId, userId);
            if (step == null) {
                return 0;
            }
            String type = step.getStepType();
            if (!STEP_TYPE_PROJECT_UPDATE.equals(type) && !STEP_TYPE_TREND_STUDY.equals(type)) {
                throw new IllegalArgumentException("기록으로 끝내는 단계가 아닙니다.");
            }
            if (step.isCompleted()) {
                return 0;
            }
            int prior = 0;
            if (STEP_TYPE_PROJECT_UPDATE.equals(type)) {
                if (step.getEvidenceProjectId() == null
                        || userProjectDao.findById(conn, step.getEvidenceProjectId(), userId) == null) {
                    throw new IllegalArgumentException("업데이트할 프로젝트를 찾을 수 없습니다. (삭제됐을 수 있어요)");
                }
                prior = countCompleted(userId, type, step.getEvidenceProjectId(), null);
                userProjectDao.touch(conn, step.getEvidenceProjectId(), userId);
            }
            int points = pointsFor(type, prior);
            roadmapStepDao.updateProof(conn, stepId, userId, PROOF_UPKEEP_NOTE, trimmed, step.getEvidenceProjectId(),
                    SkillProofGrader.PASSED, "기록 제출", true, LocalDateTime.now());
            scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId, points);
            return points;
        });
    }

    /**
     * 기술 글 업데이트 단계 제출 — 보강한 글(PDF 텍스트)을 전문가 단계와 같은 규칙(SkillProofGrader.gradeExpertArticle)으로
     * 판정한다. 통과하면 끝나고 점수를 받는다. 미통과면 판정 근거만 남기고 다시 낼 수 있게 둔다. 낸 PDF는 통과 여부와
     * 관계없이 서류 보관함에 남는다(제출 이력).
     */
    public SkillProofGrader.GradeResult submitArticleUpdate(Long userId, Long stepId, String extractedText,
            DocumentDto proofFile) throws SQLException {
        return TransactionUtil.runInTransaction(conn -> {
            RoadmapStepDto step = roadmapStepDao.findByIdForUser(conn, stepId, userId);
            if (step == null) {
                throw new IllegalArgumentException("본인의 로드맵 단계만 제출할 수 있습니다.");
            }
            if (!STEP_TYPE_ARTICLE_UPDATE.equals(step.getStepType())) {
                throw new IllegalArgumentException("기술 글 업데이트 단계가 아닙니다.");
            }
            if (step.isCompleted()) {
                throw new IllegalArgumentException("이미 완료된 단계입니다.");
            }
            SkillDto skill = step.getRelatedSkillId() == null ? null : skillDao.findById(step.getRelatedSkillId());
            String skillName = skill == null ? "" : skill.getSkillName();
            SkillProofGrader.GradeResult result = SkillProofGrader.gradeExpertArticle(extractedText, skillName);

            boolean passed = result.passed();
            roadmapStepDao.updateProof(conn, stepId, userId, PROOF_TEACHING_POST, extractedText, null,
                    result.status(), result.note(), passed, passed ? LocalDateTime.now() : null);

            proofFile.setUserId(userId);
            proofFile.setRoadmapStepId(stepId);
            documentDao.insert(conn, proofFile);

            if (passed) {
                int prior = countCompleted(userId, STEP_TYPE_ARTICLE_UPDATE, null, step.getRelatedSkillId());
                scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_ROADMAP, stepId,
                        pointsFor(STEP_TYPE_ARTICLE_UPDATE, prior));
            }
            return result;
        });
    }

    // 같은 대상(프로젝트 또는 기술)으로 이미 끝낸 같은 종류 단계 수 — 점수 감쇠의 기준
    private int countCompleted(Long userId, String type, Long projectId, Long skillId) throws SQLException {
        int count = 0;
        for (RoadmapStepDto row : roadmapStepDao.findByUserAndTypes(userId, List.of(type))) {
            if (!row.isCompleted()) {
                continue;
            }
            if (projectId != null && !projectId.equals(row.getEvidenceProjectId())) {
                continue;
            }
            if (skillId != null && !skillId.equals(row.getRelatedSkillId())) {
                continue;
            }
            count++;
        }
        return count;
    }
}
