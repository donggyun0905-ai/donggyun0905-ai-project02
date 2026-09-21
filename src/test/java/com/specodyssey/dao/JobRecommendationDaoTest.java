package com.specodyssey.dao;

import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRecommendationDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobRecommendationDaoTest {

    private static final UserDao userDao = new UserDao();
    private final JobRecommendationDao dao = new JobRecommendationDao();
    private static Long userId;
    private static Long jobId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_reco_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
        jobId = new JobDao().findAll().get(0).getId();
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_updateSelected() throws Exception {
        JobRecommendationDto recommendation = new JobRecommendationDto();
        recommendation.setUserId(userId);
        recommendation.setJobId(jobId);
        recommendation.setRankOrder(1);
        recommendation.setMatchReason("설문·임베딩 종합 근거");
        recommendation.setSelected(false);

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, recommendation);
        }
        try {
            assertNotNull(id);
            List<JobRecommendationDto> recs = dao.findByUserId(userId);
            assertEquals(1, recs.size());
            assertFalse(recs.get(0).isSelected());

            // 다른 사용자 id로는 갱신되지 않아야 한다 (소유자 확인)
            try (Connection conn = DBUtil.getConnection()) {
                dao.updateSelected(conn, id, userId + 999_999L, true);
            }
            assertFalse(dao.findByUserId(userId).get(0).isSelected());

            try (Connection conn = DBUtil.getConnection()) {
                dao.updateSelected(conn, id, userId, true);
            }
            assertTrue(dao.findByUserId(userId).get(0).isSelected());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "JOB_RECOMMENDATION", id);
            }
        }
    }
}
