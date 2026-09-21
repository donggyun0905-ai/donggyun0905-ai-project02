package com.specodyssey.dao;

import com.specodyssey.dto.ProblemDto;
import com.specodyssey.dto.UserDailyMissionDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserDailyMissionDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final ProblemDao problemDao = new ProblemDao();
    private final UserDailyMissionDao dao = new UserDailyMissionDao();

    private static Long userId;
    private static Long problemId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_mission_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        ProblemDto problem = new ProblemDto();
        problem.setTitle("팩토리얼");
        problem.setDifficultyLevel(1);
        problem.setSourceType("AI_GENERATED");
        try (Connection conn = DBUtil.getConnection()) {
            problemId = problemDao.insert(conn, problem);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "PROBLEM", problemId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_updateCompleted() throws Exception {
        UserDailyMissionDto mission = new UserDailyMissionDto();
        mission.setUserId(userId);
        mission.setProblemId(problemId);
        mission.setAssignedDate(LocalDate.now());
        mission.setCompleted(false);

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, mission);
        }
        try {
            assertNotNull(id);
            List<UserDailyMissionDto> missions = dao.findByUserId(userId);
            assertEquals(1, missions.size());
            assertFalse(missions.get(0).isCompleted());

            try (Connection conn = DBUtil.getConnection()) {
                dao.updateCompleted(conn, id, true, LocalDateTime.now(), true);
            }
            UserDailyMissionDto updated = dao.findByUserId(userId).get(0);
            assertTrue(updated.isCompleted());
            assertTrue(updated.getCorrect());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "USER_DAILY_MISSION", id);
            }
        }
    }
}
