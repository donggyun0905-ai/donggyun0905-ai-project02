package com.specodyssey.service;

import com.specodyssey.dao.MissionDao;
import com.specodyssey.dao.ScoreLogDao;
import com.specodyssey.dao.UserDailyMissionDao;
import com.specodyssey.dao.UserScoreSummaryDao;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.util.AppClock;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.time.LocalDate;

/**
 * 오늘의 미션 완료·실패 취소 (2026-10-12) — 끝낸 미션을 안 푼 상태로 되돌린다.
 * 안 한 것처럼 돌리려고 그 문제로 받은 점수를 빼고, 이 미션 때문에 오늘이 "다 끝낸 날"이 됐었다면
 * 오늘 연속 기록(+1)과 연속 보너스도 되돌린다. 다시 풀면 점수·연속 기록이 처음처럼 다시 붙는다.
 * 지난 날짜의 미션은 연속 기록이 꼬이므로 오늘 미션만 취소할 수 있다.
 */
public class MissionUndoService {

    private static final String SIGNAL_TYPE_PROBLEM = "PROBLEM";

    private final MissionDao missionDao = new MissionDao();
    private final UserDailyMissionDao userDailyMissionDao = new UserDailyMissionDao();
    private final ScoreLogDao scoreLogDao = new ScoreLogDao();
    private final UserScoreSummaryDao userScoreSummaryDao = new UserScoreSummaryDao();
    private final ScoreService scoreService = new ScoreService();

    /** 취소했으면 true — 본인의 오늘 미션이 아니거나 아직 안 끝낸 미션이면 false */
    public boolean undo(Long userId, Long missionId) throws SQLException {
        LocalDate today = AppClock.today();
        return TransactionUtil.runInTransaction(conn -> {
            if (!today.equals(missionDao.findAssignedDate(conn, userId, missionId))) {
                return false;
            }
            boolean dayWasDone = MissionStreakService.isDayDone(
                    missionDao.countMissionsByDate(conn, userId, today, today).get(today));
            if (userDailyMissionDao.resetCompletion(conn, userId, missionId) != 1) {
                return false;
            }

            int removed = scoreLogDao.softDeleteByUserSignalRef(conn, userId, SIGNAL_TYPE_PROBLEM, missionId);
            UserScoreSummaryDto summary = userScoreSummaryDao.findByUserId(conn, userId, true);
            if (summary == null) {
                return true; // 점수·연속 기록이 한 번도 없던 사용자 — 미션만 되돌리면 끝
            }
            // 오늘을 다 끝낸 날로 기록했었는데 이번 취소로 다시 미완료가 됐으면 오늘 몫의 연속 기록·보너스를 되돌린다
            if (dayWasDone && today.equals(summary.getLastMissionDate())) {
                removed += scoreLogDao.softDeleteByUserSignalRef(conn, userId,
                        MissionStreakService.SIGNAL_TYPE_STREAK, today.toEpochDay());
                int streak = revertedStreak(summary.getStreakCount() == null ? 0 : summary.getStreakCount());
                summary.setStreakCount(streak);
                summary.setLastMissionDate(streak > 0 ? today.minusDays(1) : null);
            }
            int total = Math.max(0, summary.getTotalScore() - removed);
            LevelTierDto tier = scoreService.getTierForScore(total);
            summary.setTotalScore(total);
            summary.setCurrentTierId(tier == null ? summary.getCurrentTierId() : tier.getId());
            userScoreSummaryDao.update(conn, summary);
            return true;
        });
    }

    /** 오늘 몫(+1)을 뺀 연속 기록. 오늘이 첫날이었으면 0 */
    static int revertedStreak(int streak) {
        return Math.max(0, streak - 1);
    }
}
