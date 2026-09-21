package com.specodyssey.dao;

import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationSessionDaoTest {

    private final EvaluationSessionDao dao = new EvaluationSessionDao();

    @Test
    void insert_findByToken_update_delete() throws Exception {
        String token = "test_session_token_" + System.nanoTime();
        EvaluationSessionDto session = new EvaluationSessionDto();
        session.setSessionToken(token);
        session.setCompanyName("A회사");

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, session);
        }
        try {
            assertNotNull(id);
            EvaluationSessionDto found = dao.findByToken(token);
            assertNotNull(found);
            assertEquals("A회사", found.getCompanyName());

            found.setCompanyName("B회사");
            try (Connection conn = DBUtil.getConnection()) {
                dao.update(conn, found);
            }
            assertEquals("B회사", dao.findByToken(token).getCompanyName());

            // 만료된 세션은 토큰으로 더 이상 조회되지 않아야 한다
            EvaluationSessionDto toExpire = dao.findByToken(token);
            toExpire.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            try (Connection conn = DBUtil.getConnection()) {
                dao.update(conn, toExpire);
            }
            assertNull(dao.findByToken(token));

            try (Connection conn = DBUtil.getConnection()) {
                dao.delete(conn, id, token);
            }
            assertNull(dao.findByToken(token));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "EVALUATION_SESSION", id);
            }
        }
    }
}
