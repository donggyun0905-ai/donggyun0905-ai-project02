package com.specodyssey.service;

import com.specodyssey.dao.ScoreLogDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.ScoreLogDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ScoreService 통합테스트. 실제 spec_odyssey_test DB로 검증한다.
 * LEVEL_TIER는 sql/04_seed_extended.sql로 이미 시드되어 있다는 전제(0~499 비기너 ... 5000~ 취뽀).
 */
class ScoreServiceTest {

    private final UserDao userDao = new UserDao();
    private final ScoreLogDao scoreLogDao = new ScoreLogDao();
    private final ScoreService scoreService = new ScoreService();

    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("score_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            try (var pstmt = conn.prepareStatement("DELETE FROM SCORE_LOG WHERE user_id = ?")) {
                pstmt.setLong(1, userId);
                pstmt.executeUpdate();
            }
            try (var pstmt = conn.prepareStatement("DELETE FROM USER_SCORE_SUMMARY WHERE user_id = ?")) {
                pstmt.setLong(1, userId);
                pstmt.executeUpdate();
            }
            try (var pstmt = conn.prepareStatement("DELETE FROM USERS WHERE id = ?")) {
                pstmt.setLong(1, userId);
                pstmt.executeUpdate();
            }
        }
    }

    @Test
    void 처음_적립하면_요약행이_없어도_자동으로_생기고_등급이_비기너로_잡힌다() throws Exception {
        assertNull(scoreService.getSummary(userId));

        scoreService.award(userId, "ROADMAP", 1001L, 100);

        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        assertNotNull(summary);
        assertEquals(100, summary.getTotalScore());

        LevelTierDto tier = scoreService.getTier(summary.getCurrentTierId());
        assertEquals("비기너", tier.getTierName());
    }

    @Test
    void 같은_이벤트로_두번_적립해도_한번만_반영된다() throws Exception {
        scoreService.award(userId, "ROADMAP", 2001L, 100);
        scoreService.award(userId, "ROADMAP", 2001L, 100); // 같은 ref_id — 무시돼야 함

        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        assertEquals(100, summary.getTotalScore());

        List<ScoreLogDto> logs = scoreLogDao.findByUserId(userId);
        assertEquals(1, logs.size());
    }

    @Test
    void 점수가_쌓이면_등급이_올라간다() throws Exception {
        scoreService.award(userId, "ROADMAP", 3001L, 100);
        scoreService.award(userId, "ROADMAP", 3002L, 100);
        scoreService.award(userId, "ROADMAP", 3003L, 100);
        scoreService.award(userId, "ROADMAP", 3004L, 100);
        scoreService.award(userId, "ROADMAP", 3005L, 100); // 누적 500점 → 취준생 구간 진입

        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        assertEquals(500, summary.getTotalScore());

        LevelTierDto tier = scoreService.getTier(summary.getCurrentTierId());
        assertEquals("취준생", tier.getTierName());
        assertEquals("방랑자", tier.getTitleName());
    }

    @Test
    void 서로_다른_신호끼리는_ref_id가_같아도_별개로_적립된다() throws Exception {
        scoreService.award(userId, "ROADMAP", 5000L, 100);
        scoreService.award(userId, "PROBLEM", 5000L, 5); // 같은 ref_id, 다른 signal_type

        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        assertEquals(105, summary.getTotalScore());
    }
}
