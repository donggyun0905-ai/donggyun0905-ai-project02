package com.specodyssey.service;

import com.specodyssey.dao.MissionDao;
import com.specodyssey.dao.MissionDao.TargetJob;
import com.specodyssey.dao.UserDailyMissionDao;
import com.specodyssey.dto.DailyMissionViewDto;
import com.specodyssey.dto.ProblemDto;
import com.specodyssey.dto.UserDailyMissionDto;
import com.specodyssey.util.TransactionUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 오늘의 미션 — 사용자 등급(실력)과 목표 직무에 맞는 추천 문제 3개를 배정하고 조회한다.
 * 관련 요구사항: FR-51 하루 N개 제시, FR-52 현재 수준에 맞춘 난이도
 * 배정은 한국 시간 날짜(assigned_date) 단위라 00시가 지나면 다음 접속 때 새 문제로 바뀐다.
 * 완료 체크·정답 기록·스트릭(FR-53)은 이 서비스 범위 밖이다. 여기서는 배정과 조회만 한다.
 */
public class DailyMissionService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int DAILY_COUNT = 3;
    // 0 = 프로그래머스 Lv.0 입문 문제 (LEVEL_TIER '비기너'가 0~1을 쓴다)
    private static final int MIN_PROBLEM_LEVEL = 0;
    private static final int MAX_PROBLEM_LEVEL = 5;
    static final String CATEGORY_SQL = "SQL";
    static final String CATEGORY_ALGORITHM = "ALGORITHM";

    // 같은 사용자의 동시 요청(새로고침 연타 등)이 3개를 넘게 배정하지 않도록 사용자별로 직렬화한다.
    private static final ConcurrentHashMap<Long, Object> USER_LOCKS = new ConcurrentHashMap<>();

    private final MissionDao missionDao = new MissionDao();
    private final UserDailyMissionDao userDailyMissionDao = new UserDailyMissionDao();
    private final MissionSubmitService missionSubmitService = new MissionSubmitService();

    /** 화면에 넘길 오늘의 미션 묶음. */
    public static class TodayMissions {
        private final List<DailyMissionViewDto> missions;
        private final long doneCount;
        private final int levelMin;
        private final int levelMax;
        private final String targetJobName;
        private final int sqlQuota;
        private final String tierName;
        private final int solvePoints;

        TodayMissions(List<DailyMissionViewDto> missions, int[] range, TargetJob job,
                      MissionSubmitService.CurrentTier tier) {
            this.missions = missions;
            this.doneCount = missions.stream().filter(DailyMissionViewDto::isCompleted).count();
            this.levelMin = range[0];
            this.levelMax = range[1];
            this.targetJobName = job == null ? null : job.getJobName();
            this.sqlQuota = sqlQuota(job == null ? null : job.getJobCategory());
            this.tierName = tier.name();
            this.solvePoints = tier.points();
        }

        /** 현재 등급 이름 (점수 기록이 없으면 가장 낮은 등급). */
        public String getTierName() {
            return tierName;
        }

        /** 지금 문제를 풀어 제출하면 받는 점수. */
        public int getSolvePoints() {
            return solvePoints;
        }

        public List<DailyMissionViewDto> getMissions() {
            return missions;
        }

        public long getDoneCount() {
            return doneCount;
        }

        public int getLevelMin() {
            return levelMin;
        }

        public int getLevelMax() {
            return levelMax;
        }

        public String getTargetJobName() {
            return targetJobName;
        }

        public int getSqlQuota() {
            return sqlQuota;
        }

        public int getAlgorithmQuota() {
            return DAILY_COUNT - sqlQuota;
        }
    }

    /** 오늘 배정분이 모자라면 채워서 돌려준다. 문제 풀이 비어 있으면 있는 만큼만 돌려준다. */
    public TodayMissions getOrAssignToday(Long userId) throws SQLException {
        LocalDate today = LocalDate.now(ZONE);
        // 등급 표시·점수는 제출 적립과 같은 기준(MissionSubmitService.currentTier)으로 계산한다
        MissionSubmitService.CurrentTier tier = missionSubmitService.currentTier(userId);
        synchronized (USER_LOCKS.computeIfAbsent(userId, id -> new Object())) {
            return TransactionUtil.runInTransaction(conn -> {
                int[] range = levelRange(conn, userId);
                TargetJob job = missionDao.findTargetJob(conn, userId);

                List<DailyMissionViewDto> missions = missionDao.findMissionsByDate(conn, userId, today);
                int missing = DAILY_COUNT - missions.size();
                if (missing > 0) {
                    int sqlNeeded = Math.max(0, sqlQuota(job == null ? null : job.getJobCategory())
                            - countSql(missions));
                    if (assign(conn, userId, today, missing, Math.min(sqlNeeded, missing), range) > 0) {
                        missions = missionDao.findMissionsByDate(conn, userId, today);
                    }
                }
                missions.forEach(m -> m.setSourceLabel(sourceLabel(m.getExternalUrl())));
                return new TodayMissions(missions, range, job, tier);
            });
        }
    }

    /**
     * 목표 직무 분류별 하루 3문제 중 SQL 문제 수. 나머지는 알고리즘.
     * 데이터 직무는 SQL이 실무 핵심이라 2문제, 백엔드·기획은 DB 조회를 자주 다뤄 1문제, 나머지는 알고리즘만.
     */
    static int sqlQuota(String jobCategory) {
        if (jobCategory == null) {
            return 0;
        }
        return switch (jobCategory) {
            case "DATA" -> 2;
            case "BACKEND", "PM" -> 1;
            default -> 0;
        };
    }

    // FR-52 등급의 레벨 범위 [min, max]. 등급이 없으면 가장 낮은 등급, 그것도 없으면 Lv0~1.
    private int[] levelRange(Connection conn, Long userId) throws SQLException {
        int[] range = missionDao.findUserLevelRange(conn, userId);
        if (range == null) {
            range = missionDao.findLowestTierLevelRange(conn);
        }
        int min = range == null ? MIN_PROBLEM_LEVEL : Math.max(range[0], MIN_PROBLEM_LEVEL);
        int max = range == null ? MIN_PROBLEM_LEVEL + 1 : Math.min(Math.max(range[1], min), MAX_PROBLEM_LEVEL);
        return new int[]{min, max};
    }

    // 유형별 할당량만큼 등급 범위에서 먼저 뽑고, 모자라면 유형 → 난이도 순으로 조건을 풀어 채운다
    private int assign(Connection conn, Long userId, LocalDate date, int count, int sqlCount, int[] range)
            throws SQLException {
        List<ProblemDto> picked = new ArrayList<>();
        Set<Long> pickedIds = new HashSet<>();
        pick(conn, userId, CATEGORY_SQL, range[0], range[1], sqlCount, picked, pickedIds);
        pick(conn, userId, CATEGORY_ALGORITHM, range[0], range[1], count, picked, pickedIds);
        pick(conn, userId, null, range[0], range[1], count, picked, pickedIds);
        pick(conn, userId, null, MIN_PROBLEM_LEVEL, MAX_PROBLEM_LEVEL, count, picked, pickedIds);

        for (ProblemDto problem : picked) {
            UserDailyMissionDto mission = new UserDailyMissionDto();
            mission.setUserId(userId);
            mission.setProblemId(problem.getId());
            mission.setAssignedDate(date);
            userDailyMissionDao.insert(conn, mission);
        }
        return picked.size();
    }

    // picked가 upTo개가 될 때까지 조건에 맞는 문제를 더한다
    private void pick(Connection conn, Long userId, String category, int min, int max, int upTo,
                      List<ProblemDto> picked, Set<Long> pickedIds) throws SQLException {
        int need = upTo - picked.size();
        if (need <= 0) {
            return;
        }
        // 이미 고른 문제와 겹칠 수 있어 그만큼 여유를 두고 조회한다
        for (ProblemDto p : missionDao.findAssignableProblems(conn, userId, category, min, max,
                need + picked.size())) {
            if (picked.size() < upTo && pickedIds.add(p.getId())) {
                picked.add(p);
            }
        }
    }

    private static int countSql(List<DailyMissionViewDto> missions) {
        return (int) missions.stream().filter(m -> CATEGORY_SQL.equals(m.getCategory())).count();
    }

    static String sourceLabel(String url) {
        if (url == null) {
            return "외부 링크";
        }
        if (url.contains("programmers.co.kr")) {
            return "프로그래머스";
        }
        return url.contains("acmicpc.net") ? "백준" : "외부 링크";
    }
}
