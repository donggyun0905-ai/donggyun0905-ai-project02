package com.specodyssey.service.simulation;

import com.specodyssey.dao.SimulationDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.DailyMissionViewDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SimulationStateDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.service.CodeCompileService.Language;
import com.specodyssey.service.DailyMissionService;
import com.specodyssey.service.GapAnalysisService;
import com.specodyssey.service.MissionSubmitService;
import com.specodyssey.service.RoadmapService;
import com.specodyssey.service.ScoreService;
import com.specodyssey.service.SpecScoreService;
import com.specodyssey.util.AppClock;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 테스트 계정 70일 시뮬레이션 — 하루치를 실제 서비스 코드로, 그날 날짜(AppClock.runOn)로 돌린다.
 *
 * 범위: 총점이 목표 점수에 닿을 때까지 하루씩 (최대 MAX_DAYS일). 첫날은 성향별 하루 평균 점수로 필요한 날 수를 어림잡아
 * 그만큼 전으로 잡는다 — 보통 어제 무렵에 닿는다. 다 끝난 뒤 더 높은 목표를 넣으면 다음 날부터 이어 간다.
 * 하루치: ① 미션 배정 → 성향대로 맞히거나 실패(점수·연속 보너스) ② 때가 된 복습·트렌딩 학습 단계 추가 ③ 하는 날이면
 * 열린 복습·트렌딩 학습 하나와, 며칠에 한 번 로드맵 다음 단계 완료 ④ 그날 스펙 점수 기록 ⑤ 그날 생긴 행의 날짜 맞추기.
 * 테스트 계정이 아니면 어떤 동작도 하지 않는다 (서블릿도 확인하지만 여기서 다시 확인한다).
 */
public class SimulationService {

    /** 목표에 끝내 못 닿아도 이 날 수에서 멈춘다 */
    public static final int MAX_DAYS = 365;
    /** 입력할 수 있는 목표 점수 범위 */
    public static final int MIN_TARGET = 1;
    public static final int MAX_TARGET = 100_000;

    private static final Logger LOG = Logger.getLogger(SimulationService.class.getName());

    // 기술 노트처럼 길이 규칙(20자 이상)을 지키는 기록 문구
    private static final String REVIEW_NOTE = "시뮬레이션 복습 기록 — 지난번에 배운 내용을 다시 정리하고 예제를 직접 따라 쳐 보았다.";
    private static final String UPKEEP_NOTE = "시뮬레이션 학습 기록 — 요즘 많이 쓰는 기술의 공식 문서를 읽고 핵심 개념을 정리했다.";
    private static final String JAVA_CODE = "class Solution {\n    public int solution(int n) {\n        return n;\n    }\n}";
    private static final String SQL_CODE = "SELECT 1;";

    private final SimulationDao simulationDao;
    private final UserDao userDao = new UserDao();
    private final DailyMissionService dailyMissionService = new DailyMissionService();
    private final MissionSubmitService missionSubmitService = new MissionSubmitService();
    private final GapAnalysisService gapAnalysisService = new GapAnalysisService();
    private final RoadmapService roadmapService = new RoadmapService();
    private final SpecScoreService specScoreService = new SpecScoreService();
    private final ScoreService scoreService = new ScoreService();

    /** 계정별 마지막으로 돌린 하루의 요약 — 패널 표시와 "화면 따라가기"가 읽는다 (메모리에만 둔다) */
    private static final Map<Long, DayReport> LAST_REPORTS = new ConcurrentHashMap<>();

    public SimulationService() {
        this(new SimulationDao());
    }

    SimulationService(SimulationDao simulationDao) {
        this.simulationDao = simulationDao;
    }

    // ---------------------------------------------------------------- 조회

    /** 화면 오른쪽 위 패널에 보여줄 상태 */
    public record Status(String status, int daysDone, int maxDays, String currentDate, String persona, String personaCode,
                         String message, Integer targetScore, int score, boolean reached) {
    }

    /**
     * 이 판의 난수 씨앗 — 판을 처음 시작한 시각(started_at)과 계정으로 만든다. 초기화 뒤 새로 시작하면 시각이 달라
     * 결과가 매번 달라지고, 같은 판은 일시정지·더 하기로 이어 가도 같은 흐름이다 (따로 저장할 컬럼이 필요 없다).
     */
    static long seedOf(SimulationStateDto state) {
        return state.getStartedAt().atZone(AppClock.ZONE).toEpochSecond() * 31 + state.getUserId();
    }

    /**
     * 하루 요약. page는 "화면 따라가기"가 보여줄 화면 — 로드맵 단계를 끝낸 날은 /roadmap(그 단계 stepId로 스크롤), 아니면 /dashboard.
     */
    public record DayReport(String date, boolean active, int solved, int failed, int points, int totalScore,
                            String tierName, boolean tierUp, Long stepId, int stepsCompleted, String page, String text) {
    }

    public static DayReport lastReport(Long userId) {
        return LAST_REPORTS.get(userId);
    }

    static void clearReport(Long userId) {
        LAST_REPORTS.remove(userId);
    }

    public boolean isTester(Long userId) throws SQLException {
        return userId != null && simulationDao.isTestAccount(userId);
    }

    public Status status(Long userId) throws SQLException {
        SimulationStateDto state = simulationDao.findByUserId(userId);
        int score = totalScore(userId);
        if (state == null) {
            return new Status("IDLE", 0, MAX_DAYS, null, null, null, null, null, score, false);
        }
        LocalDate shown = state.getDaysDone() == 0 ? state.getStartDate() : state.getStartDate().plusDays(state.getDaysDone() - 1);
        boolean reached = state.getTargetScore() != null && score >= state.getTargetScore();
        return new Status(state.isDone() ? SimulationStateDto.DONE : state.getStatus(), state.getDaysDone(),
                state.getTotalDays(), shown.toString(), SimPersona.fromName(state.getPersona()).getLabel(), state.getPersona(),
                state.getLastError(),
                state.getTargetScore(), score, reached);
    }

    // ---------------------------------------------------------------- 시작·일시정지·초기화

    /**
     * 시작하거나 이어 간다.
     *  - 처음: 목표 점수가 꼭 있어야 한다. 필요한 날 수를 어림잡아 그만큼 전 날짜부터 RUNNING.
     *  - 일시정지: 그대로 이어 간다. 목표를 새로 넣었으면 그 목표로 바꾼다.
     *  - 끝남: 지금 점수보다 높은 목표를 넣으면 다음 날부터 이어 간다.
     * @param targetScore 목표 점수 (이어 갈 때는 null이면 원래 목표)
     * @return 실행할 상태 (작업 스레드가 이어서 돌린다)
     * @throws IllegalStateException 안내할 이유가 있어 시작하지 않음 (목표 없음·이미 넘음, 희망 직무 없음 등)
     */
    public SimulationStateDto prepareStart(Long userId, Integer targetScore) throws SQLException {
        return prepareStart(userId, targetScore, null);
    }

    /**
     * @param personaName 새로 시작할 때의 성향 (DILIGENT·STEADY·ON_OFF). 비었거나 RANDOM이면 무작위. 이어 할 때는 원래 성향 그대로
     */
    public SimulationStateDto prepareStart(Long userId, Integer targetScore, String personaName) throws SQLException {
        requireTester(userId);
        SimPersona chosen;
        try {
            chosen = SimPersona.parse(personaName);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("성향은 성실형·보통형·작심삼일형·무작위 중에서 골라 주세요.");
        }
        if (targetScore != null && (targetScore < MIN_TARGET || targetScore > MAX_TARGET)) {
            throw new IllegalStateException("목표 점수는 " + MIN_TARGET + "~" + MAX_TARGET + "점 사이로 넣어 주세요.");
        }
        SimulationStateDto state = simulationDao.findByUserId(userId);
        int score = totalScore(userId);
        if (state != null) {
            Integer target = targetScore != null ? targetScore : state.getTargetScore();
            if (target == null || score >= target) {
                throw new IllegalStateException("지금 " + score + "점이라 목표에 이미 닿았어요. 더 높은 목표 점수를 넣으면 이어서 진행합니다.");
            }
            if (state.isDone() || !target.equals(state.getTargetScore())) {
                // 끝난 뒤 이어 가기 — 최대 날 수도 지금부터 다시 MAX_DAYS
                int maxDays = state.getDaysDone() + MAX_DAYS;
                simulationDao.updateTarget(userId, target, maxDays);
                state.setTargetScore(target);
                state.setTotalDays(maxDays);
                state.setStatus(SimulationStateDto.RUNNING);
                return state;
            }
        }
        if (state == null) {
            if (targetScore == null) {
                throw new IllegalStateException("목표 점수를 넣어 주세요.");
            }
            if (score >= targetScore) {
                throw new IllegalStateException("지금 " + score + "점이라 목표에 이미 닿았어요. 더 높은 점수를 넣어 주세요.");
            }
            UserDto user = userDao.findById(userId);
            if (user == null || user.getDesiredJobId() == null) {
                throw new IllegalStateException("프로필에서 희망 직무를 먼저 골라 주세요. 그 직무로 격차 분석과 로드맵을 만들어 시작합니다.");
            }
            state = new SimulationStateDto();
            state.setUserId(userId);
            state.setStatus(SimulationStateDto.RUNNING);
            SimPersona persona = chosen != null ? chosen : SimPersona.random();
            state.setPersona(persona.name());
            state.setTargetScore(targetScore);
            // 어림잡은 날 수만큼 전부터 — 대개 어제 무렵에 목표에 닿는다 (빠르거나 늦으면 그 날에 멈춘다)
            state.setStartDate(AppClock.today().minusDays(persona.estimateDays(score, targetScore, MAX_DAYS)));
            state.setTotalDays(MAX_DAYS);
            state.setDaysDone(0);
            state.setStartedAt(LocalDateTime.now(AppClock.ZONE));
            simulationDao.insert(state);
            return simulationDao.findByUserId(userId);
        }
        simulationDao.updateStatus(userId, SimulationStateDto.RUNNING, null);
        state.setStatus(SimulationStateDto.RUNNING);
        return state;
    }

    public void pause(Long userId) throws SQLException {
        requireTester(userId);
        simulationDao.updateStatus(userId, SimulationStateDto.PAUSED, null);
    }

    /** 이 테스트 계정이 쌓은 데이터를 지운다. 돌고 있으면 호출부(SimulationRunner)가 먼저 멈춘다. */
    public Map<String, Integer> reset(Long userId) throws SQLException {
        requireTester(userId);
        SimulationStateDto state = simulationDao.findByUserId(userId);
        Map<String, Integer> deleted = simulationDao.resetTestUserData(userId, state == null ? null : state.getStartedAt());
        clearReport(userId);
        return deleted;
    }

    // ---------------------------------------------------------------- 하루 돌리기

    /**
     * 다음 하루를 돌리고 진행을 저장한다. 첫날에는 격차 분석과 로드맵부터 만든다.
     * @return 저장한 뒤의 상태. 이미 끝났거나 RUNNING이 아니면 아무것도 하지 않고 그대로 돌려준다
     */
    public SimulationStateDto runNextDay(Long userId) throws Exception {
        requireTester(userId);
        SimulationStateDto state = simulationDao.findByUserId(userId);
        if (state == null || !state.isRunning() || state.isDone()) {
            return state;
        }
        int day = state.getDaysDone();
        LocalDate date = state.nextDate();
        SimPersona persona = SimPersona.fromName(state.getPersona());
        AppClock.runOn(date, () -> {
            if (day == 0) {
                setUpJourney(userId);
            }
            LAST_REPORTS.put(userId, simulateDay(userId, persona, day, seedOf(state)));
            return null;
        });
        simulationDao.backdateDay(userId, date);

        int done = day + 1;
        boolean reached = state.getTargetScore() != null && totalScore(userId) >= state.getTargetScore();
        boolean finished = reached || done >= state.getTotalDays();
        String status = finished ? SimulationStateDto.DONE : SimulationStateDto.RUNNING;
        // 그사이 일시정지를 눌렀으면 그 상태를 지킨다
        SimulationStateDto latest = simulationDao.findByUserId(userId);
        if (latest != null && SimulationStateDto.PAUSED.equals(latest.getStatus()) && !finished) {
            status = SimulationStateDto.PAUSED;
        }
        simulationDao.updateProgress(userId, done, status);
        state.setDaysDone(done);
        state.setStatus(status);
        return state;
    }

    // 첫날 — 희망 직무로 격차 분석 → 로드맵 (실제 화면의 "분석하기" → "로드맵 만들기"와 같은 코드)
    private void setUpJourney(Long userId) throws Exception {
        UserDto user = userDao.findById(userId);
        if (user == null || user.getDesiredJobId() == null) {
            throw new IllegalStateException("희망 직무가 없어 로드맵을 만들 수 없습니다.");
        }
        gapAnalysisService.analyze(userId, user.getDesiredJobId());
        roadmapService.refresh(userId);
    }

    DayReport simulateDay(Long userId, SimPersona persona, int day, long seed) throws SQLException {
        Random random = SimPersona.randomFor(seed, day);
        boolean active = persona.isActive(day, random);
        int scoreBefore = totalScore(userId);
        LevelTierDto tierBefore = scoreService.getTierForScore(scoreBefore);
        int solved = 0;
        int failed = 0;
        Long stepId = null;
        int stepsCompleted = 0;

        if (active) {
            for (DailyMissionViewDto mission : dailyMissionService.getOrAssignToday(userId).getMissions()) {
                if (mission.isCompleted()) {
                    continue;
                }
                if (persona.solves(random)) {
                    Language language = MissionSubmitService.defaultLanguage(mission);
                    missionSubmitService.saveCheckedSubmission(userId, mission.getMissionId(), language,
                            language == Language.SQL ? SQL_CODE : JAVA_CODE);
                    solved++;
                } else {
                    missionSubmitService.markFailed(userId, mission.getMissionId());
                    failed++;
                }
            }
        }

        LocalDateTime now = AppClock.now();
        roadmapService.appendDueReviews(userId, now);
        roadmapService.appendDueUpkeep(userId, now);

        if (active) {
            Long upkeepId = completeOpenUpkeep(userId);
            if (upkeepId != null) {
                stepId = upkeepId;
                stepsCompleted++;
            }
            if (persona.completesStep(day)) {
                Long nextId = completeNextStep(userId);
                if (nextId != null) {
                    stepId = nextId;
                    stepsCompleted++;
                }
            }
        }
        specScoreService.snapshotIfNotYetToday(userId);

        int scoreAfter = totalScore(userId);
        LevelTierDto tierAfter = scoreService.getTierForScore(scoreAfter);
        boolean tierUp = tierAfter != null && (tierBefore == null || !tierAfter.getId().equals(tierBefore.getId()));
        return report(AppClock.today(), active, solved, failed, scoreAfter - scoreBefore, scoreAfter,
                tierAfter == null ? null : tierAfter.getTierName(), tierUp, stepId, stepsCompleted);
    }

    static DayReport report(LocalDate date, boolean active, int solved, int failed, int points, int totalScore,
                            String tierName, boolean tierUp, Long stepId, int stepsCompleted) {
        StringBuilder text = new StringBuilder(String.format("%02d-%02d · ", date.getMonthValue(), date.getDayOfMonth()));
        if (!active) {
            text.append("쉬는 날");
        } else {
            text.append("미션 ").append(solved).append('/').append(solved + failed);
            if (stepsCompleted > 0) {
                text.append(" · 로드맵 ").append(stepsCompleted).append("단계 완료");
            }
        }
        if (points > 0) {
            text.append(" · +").append(points).append("점");
        }
        if (tierUp && tierName != null) {
            text.append(" · ").append(tierName).append(" 달성!");
        }
        String page = stepsCompleted > 0 ? "/roadmap" : "/dashboard";
        return new DayReport(date.toString(), active, solved, failed, points, totalScore, tierName, tierUp,
                stepId, stepsCompleted, page, text.toString());
    }

    private int totalScore(Long userId) throws SQLException {
        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        return summary == null || summary.getTotalScore() == null ? 0 : summary.getTotalScore();
    }

    // 열려 있는 복습·트렌딩 학습 하나를 기록으로 끝낸다 (프로젝트·글 업데이트는 실제 프로젝트·PDF가 있어야 해서 건너뛴다)
    private Long completeOpenUpkeep(Long userId) throws SQLException {
        Optional<RoadmapStepDto> open = openSteps(userId).stream()
                .filter(s -> "REVIEW".equals(s.getStepType()) || "TREND_STUDY".equals(s.getStepType()))
                .findFirst();
        if (open.isEmpty()) {
            return null;
        }
        RoadmapStepDto step = open.get();
        try {
            int points = "REVIEW".equals(step.getStepType())
                    ? roadmapService.completeReview(userId, step.getId(), REVIEW_NOTE)
                    : roadmapService.completeUpkeep(userId, step.getId(), UPKEEP_NOTE);
            return points > 0 ? step.getId() : null;
        } catch (IllegalArgumentException e) {
            LOG.log(Level.INFO, "시뮬레이션: 유지 단계를 끝내지 못해 건너뜁니다 — " + e.getMessage());
            return null;
        }
    }

    // 로드맵 순서대로 다음 단계 하나를 끝낸다 (기술·자격증·프로젝트 — 점수 적립과 프로필 반영은 실제 완료와 같은 코드)
    private Long completeNextStep(Long userId) throws SQLException {
        Optional<RoadmapStepDto> next = openSteps(userId).stream()
                .filter(s -> !s.isUpkeep())
                .findFirst();
        if (next.isEmpty()) {
            return null;
        }
        try {
            roadmapService.completeStep(userId, next.get().getId(), true);
            return next.get().getId();
        } catch (IllegalArgumentException e) {
            LOG.log(Level.INFO, "시뮬레이션: 로드맵 단계를 끝내지 못해 건너뜁니다 — " + e.getMessage());
            return null;
        }
    }

    private List<RoadmapStepDto> openSteps(Long userId) throws SQLException {
        RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(userId);
        if (roadmap == null) {
            return List.of();
        }
        return roadmapService.getSteps(roadmap.getId()).stream()
                .filter(s -> !s.isCompleted())
                .sorted(Comparator.comparing(s -> s.getStepOrder() == null ? Integer.MAX_VALUE : s.getStepOrder()))
                .toList();
    }

    private void requireTester(Long userId) throws SQLException {
        if (!isTester(userId)) {
            throw new SecurityException("테스트 계정만 쓸 수 있는 기능입니다.");
        }
    }
}
