package com.specodyssey.service;

import com.specodyssey.dao.LevelTierDao;
import com.specodyssey.dao.MissionDao;
import com.specodyssey.dto.DailyMissionViewDto;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.service.CodeCompileService.CompileCheck;
import com.specodyssey.service.CodeCompileService.Language;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.TransactionUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 일일 미션 "정답 입력하기" — 풀이 코드를 받아 컴파일되는지 확인하고, 통과하면 USER_DAILY_MISSION에 저장한다.
 * "실패" 버튼도 여기서 처리한다(완료 + 오답으로 표시, 점수 없음).
 * 저장되면 제출 시점 등급에 따라 문제 풀이 점수를 적립한다(SCORE_LOG signal_type=PROBLEM, ref_id=미션 id).
 * 제출·실패 모두 그날 배정분이 다 끝나면 스트릭(MissionStreakService)을 갱신한다.
 * 관련 요구사항: FR-53 완료 체크·스트릭, TD-5 스코어링
 * 항상 세션의 본인 userId로만 미션을 찾는다(다른 사람 미션 id를 넣어도 찾지 못한다).
 */
public class MissionSubmitService {

    private static final Logger LOG = Logger.getLogger(MissionSubmitService.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int MAX_CODE_LENGTH = 50_000;

    public enum Status { SAVED, INVALID, COMPILE_ERROR, UNAVAILABLE }

    /** 제출 결과. SAVED가 아니면 message를 화면에 보여 준다. */
    public record SubmitResult(Status status, String message) {
        // JSP의 EL이 읽을 수 있게 getter를 같이 둔다 — Tomcat 10.1(BeanELResolver)은 getX()만, Tomcat 11(RecordELResolver)은 x()만 찾는다.
        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSaved() {
            return status == Status.SAVED;
        }
    }

    private static final String SIGNAL_TYPE_PROBLEM = "PROBLEM";
    private static final int[] POINTS_BY_TIER_ORDER = {15, 15, 20, 25, 30};

    private final MissionDao missionDao = new MissionDao();
    private final CodeCompileService compileService = new CodeCompileService();
    private final ScoreService scoreService = new ScoreService();
    private final LevelTierDao levelTierDao = new LevelTierDao();
    private final MissionStreakService streakService = new MissionStreakService();

    /** 본인 미션이 아니거나 없으면 null. */
    public DailyMissionViewDto findOwnMission(Long userId, Long missionId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return missionDao.findMissionById(conn, userId, missionId);
        }
    }

    /** 처음 들어왔을 때 골라 둘 언어 — 이전 제출 언어, 없으면 SQL 문제는 SQL, 나머지는 Java. */
    public static Language defaultLanguage(DailyMissionViewDto mission) {
        Language previous = Language.from(mission.getSubmittedLanguage());
        if (previous != null) {
            return previous;
        }
        return "SQL".equals(mission.getCategory()) ? Language.SQL : Language.JAVA;
    }

    /** "실패" 버튼 — 본인 미션이면 실패(완료 + 오답)로 표시하고 true, 없거나 남의 미션이면 false. */
    public boolean markFailed(Long userId, Long missionId) throws SQLException {
        return TransactionUtil.runInTransaction(conn -> {
            if (missionDao.markFailed(conn, userId, missionId, LocalDateTime.now(ZONE)) != 1) {
                return false;
            }
            // 실패로 끝낸 문제도 "끝낸 문제"로 쳐서 스트릭에 반영한다 (점수는 없음)
            streakService.recordIfDayComplete(conn, userId, missionId);
            return true;
        });
    }

    public SubmitResult submit(Long userId, Long missionId, String languageCode, String code) throws SQLException {
        Language language = Language.from(languageCode);
        if (language == null) {
            return new SubmitResult(Status.INVALID, "언어를 선택해 주세요.");
        }
        if (code == null || code.isBlank()) {
            return new SubmitResult(Status.INVALID, "풀이 코드를 입력해 주세요.");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return new SubmitResult(Status.INVALID, "코드가 너무 깁니다. (" + MAX_CODE_LENGTH + "자 이하)");
        }
        if (findOwnMission(userId, missionId) == null) {
            return new SubmitResult(Status.INVALID, "미션을 찾을 수 없습니다.");
        }

        // 외부 API 호출 동안 DB 커넥션을 잡고 있지 않도록 확인을 먼저 하고 저장은 따로 한다
        CompileCheck check;
        try {
            check = compileService.check(language, code);
        } catch (ExternalApiException e) {
            LOG.log(Level.WARNING, "코드 컴파일 확인 실패 — 저장하지 않고 안내만 합니다", e);
            return new SubmitResult(Status.UNAVAILABLE,
                    "지금은 컴파일 확인을 할 수 없습니다. 잠시 후 다시 시도해 주세요. 입력한 코드는 아래에 그대로 남아 있습니다.");
        }
        if (!check.passed()) {
            return new SubmitResult(Status.COMPILE_ERROR, check.message());
        }

        // 코드 저장과 점수 적립은 한 트랜잭션 — 둘 중 하나만 반영되지 않게 한다
        int points = pointsForCurrentTier(userId);
        boolean saved = TransactionUtil.runInTransaction(conn -> {
            if (missionDao.saveSubmission(conn, userId, missionId, language.name(), code,
                    LocalDateTime.now(ZONE)) != 1) {
                return false;
            }
            // (user, PROBLEM, 미션 id)로 한 번만 적립된다 — 다시 제출해도 점수는 그대로
            scoreService.awardWithinTransaction(conn, userId, SIGNAL_TYPE_PROBLEM, missionId, points);
            // 적립이 요약 행 전체를 다시 쓰므로 스트릭은 반드시 그 뒤에 갱신한다
            streakService.recordIfDayComplete(conn, userId, missionId);
            return true;
        });
        return saved
                ? new SubmitResult(Status.SAVED, null)
                : new SubmitResult(Status.INVALID, "미션을 찾을 수 없습니다.");
    }

    /** 사용자의 현재 등급 이름과, 지금 문제를 풀면 받는 점수. 등급 마스터가 비어 있으면 name은 null. */
    public record CurrentTier(String name, int points) {
        // JSP의 EL이 읽을 수 있게 getter를 같이 둔다 — Tomcat 10.1(BeanELResolver)은 getX()만, Tomcat 11(RecordELResolver)은 x()만 찾는다.
        public String getName() {
            return name;
        }

        public int getPoints() {
            return points;
        }
    }

    // TD-5 현재 등급 — 점수 기록이 없으면 0점(가장 낮은 등급)으로 본다. 미션 화면 표시와 점수 적립이 같은 기준을 쓴다.
    public CurrentTier currentTier(Long userId) throws SQLException {
        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        int total = summary == null || summary.getTotalScore() == null ? 0 : summary.getTotalScore();
        List<LevelTierDto> tiers = levelTierDao.findAll();
        for (int i = 0; i < tiers.size(); i++) {
            LevelTierDto tier = tiers.get(i);
            if (total >= tier.getMinScore() && (tier.getMaxScore() == null || total <= tier.getMaxScore())) {
                return new CurrentTier(tier.getTierName(), pointsForTierOrder(i));
            }
        }
        return new CurrentTier(tiers.isEmpty() ? null : tiers.get(0).getTierName(), pointsForTierOrder(0));
    }

    private int pointsForCurrentTier(Long userId) throws SQLException {
        return currentTier(userId).points();
    }

    /**
     * 등급 순서(LEVEL_TIER min_score 오름차순, 0부터)별 문제 풀이 점수.
     * 비기너 15 · 취준생 15 · 실전러 20 · 취뽀 임박 25 · 취뽀 30 — ScoreService 로고처럼 순서로 대응한다.
     */
    static int pointsForTierOrder(int order) {
        int i = Math.max(0, Math.min(order, POINTS_BY_TIER_ORDER.length - 1));
        return POINTS_BY_TIER_ORDER[i];
    }
}
