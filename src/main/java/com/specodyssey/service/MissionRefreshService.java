package com.specodyssey.service;

import com.specodyssey.dao.MissionDao;
import com.specodyssey.dao.UserDailyMissionDao;
import com.specodyssey.dto.ProblemDto;
import com.specodyssey.dto.UserDailyMissionDto;
import com.specodyssey.util.AppClock;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 오늘의 미션 "새로 불러오기" (2026-10-12) — 오늘 아직 안 푼 미션을 지금 등급의 난이도로 다시 뽑는다.
 * 푼 미션·실패한 미션은 그대로 두고, 바꾼 개수만큼만 새로 넣는다. 점수·연속 기록은 건드리지 않는다.
 * 미션은 하루에 한 번 배정되면 다시 뽑지 않아서, 배정 뒤에 등급이 오르면 그날은 낮은 난이도가 남는다 — 그때 쓴다.
 * 바꾼 미션은 논리 삭제하고, 같은 날 같은 문제가 다시 나오지 않게 한다(MissionDao.findRefreshableProblems).
 */
public class MissionRefreshService {

    static final int MIN_PROBLEM_LEVEL = 0;
    static final int MAX_PROBLEM_LEVEL = 5;

    private final MissionDao missionDao = new MissionDao();
    private final UserDailyMissionDao userDailyMissionDao = new UserDailyMissionDao();

    /** 바꾼 미션 수 — 안 푼 미션이 없으면 0 */
    public int refreshToday(Long userId) throws SQLException {
        LocalDate today = AppClock.today();
        return TransactionUtil.runInTransaction(conn -> {
            int replaced = userDailyMissionDao.softDeleteUncompletedByDate(conn, userId, today);
            if (replaced == 0) {
                return 0;
            }
            int[] raw = missionDao.findUserLevelRange(conn, userId);
            if (raw == null) {
                raw = missionDao.findLowestTierLevelRange(conn);
            }
            int[] range = levelRange(raw);

            // 등급 범위에서 먼저 뽑고, 모자라면 전체 난이도에서 채운다 (DailyMissionService와 같은 순서)
            List<ProblemDto> picked = new ArrayList<>();
            Set<Long> pickedIds = new HashSet<>();
            for (int[] r : new int[][]{range, {MIN_PROBLEM_LEVEL, MAX_PROBLEM_LEVEL}}) {
                for (ProblemDto p : missionDao.findRefreshableProblems(conn, userId, today, r[0], r[1], replaced)) {
                    if (picked.size() < replaced && pickedIds.add(p.getId())) {
                        picked.add(p);
                    }
                }
            }
            for (ProblemDto problem : picked) {
                UserDailyMissionDto mission = new UserDailyMissionDto();
                mission.setUserId(userId);
                mission.setProblemId(problem.getId());
                mission.setAssignedDate(today);
                userDailyMissionDao.insert(conn, mission);
            }
            return replaced;
        });
    }

    /** 등급의 문제 레벨 범위를 0~5 안으로 맞춘다. 등급 정보가 없으면 Lv0~1 */
    static int[] levelRange(int[] raw) {
        if (raw == null) {
            return new int[]{MIN_PROBLEM_LEVEL, MIN_PROBLEM_LEVEL + 1};
        }
        int min = Math.min(Math.max(raw[0], MIN_PROBLEM_LEVEL), MAX_PROBLEM_LEVEL);
        int max = Math.min(Math.max(raw[1], min), MAX_PROBLEM_LEVEL);
        return new int[]{min, max};
    }
}
