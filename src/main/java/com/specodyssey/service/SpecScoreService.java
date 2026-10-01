package com.specodyssey.service;

import com.specodyssey.dao.SpecScoreHistoryDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.SpecScoreHistoryDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SPEC_SCORE_HISTORY 실제 연결 — FR-41(완성도 게이지) · FR-45(또래 비교) · FR-84(성장 잠재력).
 * 관련 팀 결정: docs/db-design.md "SPEC_SCORE_HISTORY" 설계 판단 — 이 테이블은 다른 테이블을
 * FK로 참조하지 않는다(시점별 점수 스냅샷일 뿐). "면접관에게 뭘 했는지 보여주는" 쪽(FR-81 타임라인)은
 * USER_PROJECTS·USER_SPECS·USER_SKILLS를 직접 시간순으로 읽어서 보여주는 별개 기능이고,
 * 이 서비스는 그 성취들을 종합한 "완성도 숫자"만 시계열로 쌓는 역할이다 (2026-09-30 확인).
 *
 * completeness_score 공식(팀에 공식이 없어 이번에 새로 정함, 0~100):
 *   - 40점: 목표 직무 격차 분석 충족률(GapAnalysisDto.matchRate, 없으면 0)
 *   - 60점: 스펙 총량 — 자격증(최대 5개×4점=20) + 프로젝트(최대 5개×4점=20) + 보유기술(최대 20개×1점=20)
 * 목표 직무가 없으면 40점 몫을 스펙 총량 쪽으로 재배분한다(총량 가중치를 60→100으로 스케일).
 */
public class SpecScoreService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_CERT_FOR_SCORE = 5;
    private static final int MAX_PROJECT_FOR_SCORE = 5;
    private static final int MAX_SKILL_FOR_SCORE = 20;
    private static final BigDecimal GAP_WEIGHT = BigDecimal.valueOf(40);
    private static final BigDecimal RICHNESS_WEIGHT_WITH_JOB = BigDecimal.valueOf(60);
    private static final BigDecimal RICHNESS_WEIGHT_WITHOUT_JOB = BigDecimal.valueOf(100);

    private final UserDao userDao;
    private final UserSpecDao userSpecDao;
    private final UserProjectDao userProjectDao;
    private final UserSkillDao userSkillDao;
    private final SpecScoreHistoryDao specScoreHistoryDao;
    private final GapAnalysisService gapAnalysisService;

    public SpecScoreService() {
        this(new UserDao(), new UserSpecDao(), new UserProjectDao(), new UserSkillDao(),
                new SpecScoreHistoryDao(), new GapAnalysisService());
    }

    public SpecScoreService(UserDao userDao, UserSpecDao userSpecDao, UserProjectDao userProjectDao,
                             UserSkillDao userSkillDao, SpecScoreHistoryDao specScoreHistoryDao,
                             GapAnalysisService gapAnalysisService) {
        this.userDao = userDao;
        this.userSpecDao = userSpecDao;
        this.userProjectDao = userProjectDao;
        this.userSkillDao = userSkillDao;
        this.specScoreHistoryDao = specScoreHistoryDao;
        this.gapAnalysisService = gapAnalysisService;
    }

    public BigDecimal computeCompletenessScore(Long userId) throws SQLException {
        long certCount = userSpecDao.findByUserId(userId).stream()
                .filter(s -> "CERT".equals(s.getSpecType())).count();
        long projectCount = userProjectDao.findByUserId(userId).size();
        long skillCount = userSkillDao.findByUserId(userId).size();

        GapAnalysisDto latest = gapAnalysisService.getLatest(userId);
        boolean hasJob = latest != null && latest.getMatchRate() != null;

        BigDecimal richnessWeight = hasJob ? RICHNESS_WEIGHT_WITH_JOB : RICHNESS_WEIGHT_WITHOUT_JOB;
        BigDecimal richnessRatio = BigDecimal.valueOf(
                Math.min(certCount, MAX_CERT_FOR_SCORE) / (double) MAX_CERT_FOR_SCORE
                        + Math.min(projectCount, MAX_PROJECT_FOR_SCORE) / (double) MAX_PROJECT_FOR_SCORE
                        + Math.min(skillCount, MAX_SKILL_FOR_SCORE) / (double) MAX_SKILL_FOR_SCORE
        ).divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
        BigDecimal richnessScore = richnessRatio.multiply(richnessWeight);

        BigDecimal gapScore = BigDecimal.ZERO;
        if (hasJob) {
            // matchRate는 0~100 스케일(GapAnalysisService)이라 40점 몫으로 다시 비례 배분한다.
            gapScore = latest.getMatchRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                    .multiply(GAP_WEIGHT);
        }

        return richnessScore.add(gapScore).setScale(2, RoundingMode.HALF_UP);
    }

    /** 오늘 아직 기록하지 않았으면 스냅샷을 하나 남긴다. 기록했으면 false. */
    public boolean snapshotIfNotYetToday(Long userId) throws SQLException {
        LocalDate today = LocalDate.now(ZONE);
        try (Connection conn = DBUtil.getConnection()) {
            if (specScoreHistoryDao.existsForUserOnDate(conn, userId, today)) {
                return false;
            }
        }
        UserDto user = userDao.findById(userId);
        if (user == null) {
            return false;
        }
        BigDecimal score = computeCompletenessScore(userId);

        SpecScoreHistoryDto snapshot = new SpecScoreHistoryDto();
        snapshot.setUserId(userId);
        snapshot.setSnapshotDate(today);
        snapshot.setCompletenessScore(score);
        snapshot.setMajor(user.getMajor());
        snapshot.setGrade(user.getGrade());
        snapshot.setSeed(false);
        specScoreHistoryDao.insert(snapshot);
        return true;
    }

    /** SpecScoreScheduler 전용 — 전체 실사용자 대상 일 1회 배치. 한 명 실패해도 나머지는 계속한다. */
    public int snapshotAllIfNotYetToday() throws SQLException {
        int recorded = 0;
        for (UserDto user : userDao.findAll()) {
            if (snapshotIfNotYetToday(user.getId())) {
                recorded++;
            }
        }
        return recorded;
    }

    public record GrowthSummary(BigDecimal fromScore, BigDecimal toScore, LocalDate fromDate, LocalDate toDate,
                                 long certDelta, long projectDelta, long skillDelta) {
    }

    /**
     * FR-84 성장 잠재력 — 가장 오래된 스냅샷과 가장 최근 스냅샷 사이의 점수 변화 + 그 기간 동안
     * 새로 생긴 자격증/프로젝트/기술 개수. 스냅샷이 하나뿐이면(성장을 비교할 과거가 없으면) null.
     */
    public GrowthSummary getGrowthSummary(Long userId) throws SQLException {
        List<SpecScoreHistoryDto> history = specScoreHistoryDao.findByUserId(userId); // snapshot_date 오름차순
        if (history.size() < 2) {
            return null;
        }
        SpecScoreHistoryDto from = history.get(0);
        SpecScoreHistoryDto to = history.get(history.size() - 1);
        LocalDateTime windowStart = from.getSnapshotDate().atStartOfDay();

        long certDelta = userSpecDao.findByUserId(userId).stream()
                .filter(s -> "CERT".equals(s.getSpecType()))
                .filter(s -> s.getCreatedAt() != null && !s.getCreatedAt().isBefore(windowStart))
                .count();
        long projectDelta = userProjectDao.findByUserId(userId).stream()
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(windowStart))
                .count();
        long skillDelta = userSkillDao.findByUserId(userId).stream()
                .filter(s -> s.getCreatedAt() != null && !s.getCreatedAt().isBefore(windowStart))
                .count();

        return new GrowthSummary(from.getCompletenessScore(), to.getCompletenessScore(),
                from.getSnapshotDate(), to.getSnapshotDate(), certDelta, projectDelta, skillDelta);
    }

    public record MonthlyScorePoint(String monthLabel, BigDecimal score) {
    }

    // FR-81/84 면접관 뷰 "성장 잠재력" 막대 그래프 — 월별로 가장 최근 스냅샷 하나만 남긴다.
    // findByUserId가 snapshot_date 오름차순을 보장해서, 같은 월 키를 덮어쓰면 자연히 그 달의
    // 마지막 값이 남는다. LinkedHashMap이라 첫 등장 순서(=날짜 오름차순)가 그대로 출력 순서가 된다.
    public List<MonthlyScorePoint> getMonthlySeries(Long userId) throws SQLException {
        DateTimeFormatter keyFormat = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, SpecScoreHistoryDto> latestPerMonth = new LinkedHashMap<>();
        for (SpecScoreHistoryDto history : specScoreHistoryDao.findByUserId(userId)) {
            latestPerMonth.put(history.getSnapshotDate().format(keyFormat), history);
        }
        return latestPerMonth.values().stream()
                .map(h -> new MonthlyScorePoint(h.getSnapshotDate().getMonthValue() + "월", h.getCompletenessScore()))
                .collect(Collectors.toList());
    }

    public record PeerComparison(BigDecimal myScore, BigDecimal peerAverage, int peerCount) {
    }

    /**
     * FR-45 또래 비교 — 같은 전공·학년 사용자들의 "각자 가장 최근" 스냅샷 평균과 내 최신 점수를 비교한다.
     * 나와 같은 전공·학년의 스냅샷이 하나도 없으면(팀 결정: is_seed 데모 데이터로 보완 예정, 아직 미적재)
     * null을 돌려주고 화면에서 "비교할 또래 데이터가 아직 없습니다"로 안내한다.
     */
    public PeerComparison getPeerComparison(Long userId) throws SQLException {
        UserDto user = userDao.findById(userId);
        if (user == null || user.getMajor() == null || user.getGrade() == null) {
            return null;
        }
        List<SpecScoreHistoryDto> mine = specScoreHistoryDao.findByUserId(userId);
        if (mine.isEmpty()) {
            return null;
        }
        BigDecimal myScore = mine.get(mine.size() - 1).getCompletenessScore();

        List<SpecScoreHistoryDto> peers = specScoreHistoryDao.findByMajorAndGrade(user.getMajor(), user.getGrade())
                .stream()
                .filter(h -> !h.getUserId().equals(userId))
                .collect(Collectors.toList());
        if (peers.isEmpty()) {
            return new PeerComparison(myScore, null, 0);
        }

        // 같은 사람의 여러 스냅샷 중 가장 최근 것만 한 표로 센다.
        Map<Long, SpecScoreHistoryDto> latestPerPeer = peers.stream()
                .collect(Collectors.toMap(SpecScoreHistoryDto::getUserId, h -> h,
                        (a, b) -> a.getSnapshotDate().isAfter(b.getSnapshotDate()) ? a : b));

        BigDecimal sum = latestPerPeer.values().stream()
                .map(SpecScoreHistoryDto::getCompletenessScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(latestPerPeer.size()), 2, RoundingMode.HALF_UP);

        return new PeerComparison(myScore, average, latestPerPeer.size());
    }
}
