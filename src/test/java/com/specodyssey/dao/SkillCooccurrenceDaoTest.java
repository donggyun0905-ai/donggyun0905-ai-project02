package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 함께 익힌 기술 추천의 입력 조회 (2026-10-08).
 * SQL이 실제 컬럼 이름과 맞는지, 그리고 제외 조건(본인·탈퇴·테스트 계정)이 실제로 걸리는지 본다.
 */
class SkillCooccurrenceDaoTest {

    private static final SkillCooccurrenceDao dao = new SkillCooccurrenceDao();
    private static final UserDao userDao = new UserDao();
    private static final UserSkillDao userSkillDao = new UserSkillDao();

    private static Long jobId;
    private static Long me;
    private static Long peer;
    private static Long withdrawn;
    private static Long testAccount;
    private static Long otherJobUser;
    private static long skillA;
    private static long skillB;

    @BeforeAll
    static void setUp() throws Exception {
        jobId = new JobDao().findAll().stream().map(JobDto::getId).findFirst().orElseThrow();
        Long otherJobId = new JobDao().findAll().stream().map(JobDto::getId)
                .filter(id -> !id.equals(jobId)).findFirst().orElseThrow();

        try (Connection conn = DBUtil.getConnection()) {
            long stamp = System.nanoTime();
            skillA = TestFixtures.insertSkill(conn, "동시출현A_" + stamp);
            skillB = TestFixtures.insertSkill(conn, "동시출현B_" + stamp);
        }
        me = newUser(jobId);
        peer = newUser(jobId);
        withdrawn = newUser(jobId);
        testAccount = newUser(jobId);
        otherJobUser = newUser(otherJobId);

        addSkill(me, skillA);
        addSkill(peer, skillA);
        addSkill(peer, skillB);
        addSkill(withdrawn, skillB);
        addSkill(testAccount, skillB);
        addSkill(otherJobUser, skillB);

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement(
                     "UPDATE USERS SET withdraw_requested_at = NOW() WHERE id = ?")) {
            p.setLong(1, withdrawn);
            p.executeUpdate();
        }
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement("UPDATE USERS SET is_test = TRUE WHERE id = ?")) {
            p.setLong(1, testAccount);
            p.executeUpdate();
        }
    }

    private static Long newUser(Long desiredJobId) throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_cooc_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobId(desiredJobId);
        user.setDesiredJobStatus("SET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        return userDao.insert(user);
    }

    private static void addSkill(Long userId, long skillId) throws Exception {
        UserSkillDto skill = new UserSkillDto();
        skill.setUserId(userId);
        skill.setRawInput("동시출현테스트_" + skillId);
        skill.setSkillId(skillId);
        userSkillDao.insert(skill);
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long userId : new Long[] {me, peer, withdrawn, testAccount, otherJobUser}) {
                if (userId == null) {
                    continue;
                }
                TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
                TestFixtures.hardDelete(conn, "USERS", userId);
            }
            for (long skillId : new long[] {skillA, skillB}) {
                TestFixtures.hardDelete(conn, "SKILL", skillId);
            }
        }
    }

    @Test
    void 내_기술을_SKILL과_연결된_것만_읽는다() throws Exception {
        Set<Long> mine = dao.mySkillIds(me);

        assertTrue(mine.contains(skillA), "실제: " + mine);
        assertFalse(mine.contains(skillB));
    }

    @Test
    void 같은_직무_또래만_읽고_본인은_빼고_탈퇴_테스트_계정도_뺀다() throws Exception {
        Map<Long, Set<Long>> peers = dao.peerSkillsByJob(jobId, me);

        assertTrue(peers.containsKey(peer), "같은 직무 또래는 들어와야 한다");
        assertEquals(Set.of(skillA, skillB), peers.get(peer));
        assertFalse(peers.containsKey(me), "본인을 또래로 세면 안 된다");
        assertFalse(peers.containsKey(withdrawn), "탈퇴 신청한 계정은 빼야 한다");
        assertFalse(peers.containsKey(testAccount), "테스트 계정이 추천에 섞이면 안 된다");
        assertFalse(peers.containsKey(otherJobUser), "다른 직무를 목표로 한 사람은 또래가 아니다");
    }
}
