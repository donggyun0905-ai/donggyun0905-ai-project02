package com.specodyssey.service;

import com.specodyssey.dao.SpecScoreHistoryDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.SpecScoreHistoryDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SpecScoreService 통합테스트 — SPEC_SCORE_HISTORY 실제 연결(FR-41·45·84) 검증.
 */
class SpecScoreServiceTest {

    private final UserDao userDao = new UserDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final SpecScoreHistoryDao specScoreHistoryDao = new SpecScoreHistoryDao();
    private final SpecScoreService service = new SpecScoreService();

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long userId : createdUserIds) {
                TestFixtures.hardDeleteByColumn(conn, "SPEC_SCORE_HISTORY", "user_id", userId);
                TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
                TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
                TestFixtures.hardDeleteByColumn(conn, "USER_SPECS", "user_id", userId);
                TestFixtures.hardDelete(conn, "USERS", userId);
            }
        }
        createdUserIds.clear();
    }

    private long createUser(String major, String grade) throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("spec_score_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setMajor(major);
        user.setGrade(grade);
        user.setPrivacyConsentAt(LocalDateTime.now());
        long id = userDao.insert(user);
        createdUserIds.add(id);
        return id;
    }

    private void addCert(long userId) throws Exception {
        UserSpecDto spec = new UserSpecDto();
        spec.setUserId(userId);
        spec.setSpecType("CERT");
        spec.setTitle("정보처리기사_" + System.nanoTime());
        userSpecDao.insert(spec);
    }

    private void addProject(long userId) throws Exception {
        UserProjectDto project = new UserProjectDto();
        project.setUserId(userId);
        project.setTitle("테스트 프로젝트_" + System.nanoTime());
        userProjectDao.insert(project);
    }

    private void addSkill(long userId) throws Exception {
        UserSkillDto skill = new UserSkillDto();
        skill.setUserId(userId);
        skill.setRawInput("TestSkill_" + System.nanoTime());
        userSkillDao.insert(skill);
    }

    @Test
    void 완성도_점수는_목표직무_없이_스펙_총량만으로_계산된다() throws Exception {
        long userId = createUser("완성도테스트전공_" + System.nanoTime(), "4학년");
        addCert(userId);
        addCert(userId);
        addProject(userId);
        addSkill(userId);
        addSkill(userId);
        addSkill(userId);

        // 자격증 2/5=0.4, 프로젝트 1/5=0.2, 기술 3/20=0.15 → 평균 0.25 → 100점 만점 중 25.00
        BigDecimal score = service.computeCompletenessScore(userId);
        assertEquals(0, new BigDecimal("25.00").compareTo(score));
    }

    @Test
    void 스냅샷은_하루에_한번만_기록된다() throws Exception {
        long userId = createUser("스냅샷테스트전공_" + System.nanoTime(), "3학년");
        addCert(userId);

        assertTrue(service.snapshotIfNotYetToday(userId));
        assertFalse(service.snapshotIfNotYetToday(userId));

        List<SpecScoreHistoryDto> history = specScoreHistoryDao.findByUserId(userId);
        assertEquals(1, history.size());
        assertFalse(history.get(0).isSeed());
    }

    @Test
    void 또래_비교는_같은_전공학년_평균과_비교한다() throws Exception {
        String major = "또래비교테스트전공_" + System.nanoTime();
        String grade = "2학년";
        long me = createUser(major, grade);
        long peer = createUser(major, grade);

        addCert(me);
        addCert(me);
        addCert(me);
        addCert(me);
        addCert(me); // 5/5=1.0 → 100점 만점 중 33.33 (1.0/3)

        service.snapshotIfNotYetToday(me);
        service.snapshotIfNotYetToday(peer); // peer는 스펙 없음 → 0점

        SpecScoreService.PeerComparison comparison = service.getPeerComparison(me);
        assertNotNull(comparison);
        assertEquals(1, comparison.peerCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(comparison.peerAverage()));
        assertTrue(comparison.myScore().compareTo(comparison.peerAverage()) > 0);
    }

    @Test
    void 또래가_없으면_null을_돌려준다() throws Exception {
        long userId = createUser("나홀로전공_" + System.nanoTime(), "1학년");
        service.snapshotIfNotYetToday(userId);

        SpecScoreService.PeerComparison comparison = service.getPeerComparison(userId);
        assertEquals(0, comparison.peerCount());
        assertNull(comparison.peerAverage());
    }

    @Test
    void 성장_요약은_가장_오래된_스냅샷과_최근_스냅샷을_비교한다() throws Exception {
        long userId = createUser("성장테스트전공_" + System.nanoTime(), "4학년");

        SpecScoreHistoryDto oldSnapshot = new SpecScoreHistoryDto();
        oldSnapshot.setUserId(userId);
        oldSnapshot.setSnapshotDate(LocalDate.now().minusDays(30));
        oldSnapshot.setCompletenessScore(new BigDecimal("40.00"));
        oldSnapshot.setSeed(false);
        specScoreHistoryDao.insert(oldSnapshot);

        addCert(userId); // 30일 전 스냅샷 이후에 생겼으니 성장분으로 잡혀야 함

        SpecScoreHistoryDto newSnapshot = new SpecScoreHistoryDto();
        newSnapshot.setUserId(userId);
        newSnapshot.setSnapshotDate(LocalDate.now());
        newSnapshot.setCompletenessScore(new BigDecimal("62.00"));
        newSnapshot.setSeed(false);
        specScoreHistoryDao.insert(newSnapshot);

        SpecScoreService.GrowthSummary growth = service.getGrowthSummary(userId);
        assertNotNull(growth);
        assertEquals(0, new BigDecimal("40.00").compareTo(growth.fromScore()));
        assertEquals(0, new BigDecimal("62.00").compareTo(growth.toScore()));
        assertEquals(1, growth.certDelta());
    }

    // FR-81/84 면접관 뷰 "성장 잠재력" 막대 그래프 — 같은 달 안의 스냅샷은 마지막 값만 남아야 한다.
    @Test
    void 월별_시리즈는_같은_달의_스냅샷_중_가장_최근_값만_남긴다() throws Exception {
        long userId = createUser("월별시리즈테스트전공_" + System.nanoTime(), "4학년");

        insertSnapshot(userId, LocalDate.of(2026, 7, 1), "44.00");
        insertSnapshot(userId, LocalDate.of(2026, 8, 15), "49.00");
        insertSnapshot(userId, LocalDate.of(2026, 9, 1), "55.00");
        insertSnapshot(userId, LocalDate.of(2026, 9, 30), "62.00"); // 9월 안에서는 이 값이 남아야 함

        List<SpecScoreService.MonthlyScorePoint> series = service.getMonthlySeries(userId);

        assertEquals(3, series.size());
        assertEquals("7월", series.get(0).monthLabel());
        assertEquals(0, new BigDecimal("44.00").compareTo(series.get(0).score()));
        assertEquals("9월", series.get(2).monthLabel());
        assertEquals(0, new BigDecimal("62.00").compareTo(series.get(2).score()));
    }

    private void insertSnapshot(long userId, LocalDate date, String score) throws Exception {
        SpecScoreHistoryDto snapshot = new SpecScoreHistoryDto();
        snapshot.setUserId(userId);
        snapshot.setSnapshotDate(date);
        snapshot.setCompletenessScore(new BigDecimal(score));
        snapshot.setSeed(false);
        specScoreHistoryDao.insert(snapshot);
    }
}
