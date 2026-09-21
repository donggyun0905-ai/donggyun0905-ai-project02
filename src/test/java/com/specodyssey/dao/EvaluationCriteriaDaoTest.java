package com.specodyssey.dao;

import com.specodyssey.dto.EvaluationCriteriaDto;
import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationCriteriaDaoTest {

    private static final EvaluationSessionDao sessionDao = new EvaluationSessionDao();
    private final EvaluationCriteriaDao dao = new EvaluationCriteriaDao();

    private static Long sessionId;
    private static String sessionToken;
    private static long skillId;

    @BeforeAll
    static void setUp() throws Exception {
        sessionToken = "test_session_criteria_" + System.nanoTime();
        EvaluationSessionDto session = new EvaluationSessionDto();
        session.setSessionToken(sessionToken);
        session.setCompanyName("테스트 회사");
        try (Connection conn = DBUtil.getConnection()) {
            sessionId = sessionDao.insert(conn, session);
            skillId = TestFixtures.insertSkill(conn, "TestSkill_criteria_" + System.nanoTime());
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "EVALUATION_SESSION", sessionId);
            TestFixtures.hardDelete(conn, "SKILL", skillId);
        }
    }

    @Test
    void insert_findBySessionId_update_delete() throws Exception {
        EvaluationCriteriaDto criteria = new EvaluationCriteriaDto();
        criteria.setSessionId(sessionId);
        criteria.setSkillId(skillId);
        criteria.setWeight(5);

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, criteria);
        }
        try {
            assertNotNull(id);
            List<EvaluationCriteriaDto> list = dao.findBySessionId(sessionId);
            assertEquals(1, list.size());
            assertEquals(5, list.get(0).getWeight());

            try (Connection conn = DBUtil.getConnection()) {
                dao.update(conn, id, 9, sessionToken);
            }
            assertEquals(9, dao.findBySessionId(sessionId).get(0).getWeight());

            try (Connection conn = DBUtil.getConnection()) {
                dao.delete(conn, id, sessionToken);
            }
            assertTrue(dao.findBySessionId(sessionId).isEmpty());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EVALUATION_CRITERIA", id);
            }
        }
    }
}
