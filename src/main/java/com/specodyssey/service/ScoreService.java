package com.specodyssey.service;

import com.specodyssey.dao.LevelTierDao;
import com.specodyssey.dao.ScoreLogDao;
import com.specodyssey.dao.UserScoreSummaryDao;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.ScoreLogDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.util.TransactionUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 점수 적립 · 등급 갱신 공용 서비스.
 * 관련 요구사항: TD-5 스코어링 (로드맵 완료 +100, 코테 정답 +10~30, 문제 풀이 +5, 서류 등록 +30, 자가진단 +0~50)
 *
 * 지금은 로드맵 단계 완료(signal_type=ROADMAP)만 실제로 이 서비스를 호출하지만, 나머지 신호(QUIZ·PROBLEM·
 * DOCUMENT·SELF_CHECK)를 만드는 담당자도 그대로 재사용할 수 있도록 signal_type을 파라미터로 받는
 * 범용 구조로 만들었다.
 *
 * 점수는 상한 없이 계속 쌓이는 게임식 설계(TD-5)라 취소해도 깎지 않는다 — SCORE_LOG 자체가
 * append-only(수정·삭제 없음)로 설계된 이유이기도 하다. 같은 이벤트(user_id, signal_type, ref_id)로
 * 두 번 호출해도 UNIQUE 제약 취지에 맞게 두 번째부터는 조용히 무시한다(중복 적립 방지).
 */
public class ScoreService {

    // LEVEL_TIER에 로고 컬럼을 따로 두지 않고, min_score 오름차순 순서(findAll 정렬 기준)와
    // 파일명을 1:1로 맞춘다 — 5단계 순서는 팀에서 확정했고(TD-5) 안 바뀐다는 전제.
    private static final String[] TIER_LOGO_FILES = {
            "tier-1-beginner.png",
            "tier-2-jobseeker.png",
            "tier-3-practitioner.png",
            "tier-4-almost.png",
            "tier-5-legend.png"
    };

    private final ScoreLogDao scoreLogDao = new ScoreLogDao();
    private final UserScoreSummaryDao userScoreSummaryDao = new UserScoreSummaryDao();
    private final LevelTierDao levelTierDao = new LevelTierDao();

    // 단독 호출용 — 자체 트랜잭션을 새로 연다.
    public void award(Long userId, String signalType, Long refId, int points) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            awardWithinTransaction(conn, userId, signalType, refId, points);
            return null;
        });
    }

    // 다른 서비스의 트랜잭션에 끼워 넣을 때 — 예: RoadmapService.completeStep은
    // "단계 완료 처리"와 "점수 적립"을 한 트랜잭션으로 묶어야 부분 반영을 막을 수 있다.
    public void awardWithinTransaction(Connection conn, Long userId, String signalType, Long refId, int points)
            throws SQLException {
        if (scoreLogDao.existsByUserSignalRef(conn, userId, signalType, refId)) {
            return;
        }

        ScoreLogDto log = new ScoreLogDto();
        log.setUserId(userId);
        log.setSignalType(signalType);
        log.setRefId(refId);
        log.setPoints(points);
        log.setEarnedAt(LocalDateTime.now());
        scoreLogDao.insert(conn, log);

        UserScoreSummaryDto summary = userScoreSummaryDao.findByUserId(conn, userId, true);
        if (summary == null) {
            // 가입 시 만들어지지 않으므로 첫 적립 시점에 없으면 여기서 생성한다.
            summary = new UserScoreSummaryDto();
            summary.setUserId(userId);
            summary.setTotalScore(0);
            summary.setStreakCount(0);
            userScoreSummaryDao.insert(conn, summary);
        }

        int newTotal = summary.getTotalScore() + points;
        summary.setTotalScore(newTotal);
        summary.setCurrentTierId(resolveTierId(newTotal));
        userScoreSummaryDao.update(conn, summary);
    }

    public UserScoreSummaryDto getSummary(Long userId) throws SQLException {
        return userScoreSummaryDao.findByUserId(userId);
    }

    public LevelTierDto getTier(Long tierId) throws SQLException {
        if (tierId == null) {
            return null;
        }
        return levelTierDao.findAll().stream()
                .filter(t -> t.getId().equals(tierId))
                .findFirst()
                .orElse(null);
    }

    // 헤더·프로필 등 화면 표시용 — 적립 이력이 아예 없는 사용자(요약행 없음)도 0점 기준으로
    // 현재 등급을 그대로 보여줄 수 있게 조회 전용으로 분리했다.
    public LevelTierDto getTierForScore(int totalScore) throws SQLException {
        List<LevelTierDto> tiers = levelTierDao.findAll();
        for (LevelTierDto tier : tiers) {
            boolean aboveMin = totalScore >= tier.getMinScore();
            boolean belowMax = tier.getMaxScore() == null || totalScore <= tier.getMaxScore();
            if (aboveMin && belowMax) {
                return tier;
            }
        }
        return null;
    }

    // /image/tier-N-*.png 중 이 등급에 해당하는 로고 경로 (contextPath 제외, 앞에 / 포함)
    public String getTierLogoPath(Long tierId) throws SQLException {
        if (tierId == null) {
            return null;
        }
        List<LevelTierDto> tiers = levelTierDao.findAll();
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).getId().equals(tierId)) {
                return i < TIER_LOGO_FILES.length ? "/image/" + TIER_LOGO_FILES[i] : null;
            }
        }
        return null;
    }

    private Long resolveTierId(int totalScore) throws SQLException {
        LevelTierDto tier = getTierForScore(totalScore);
        return tier == null ? null : tier.getId();
    }
}
