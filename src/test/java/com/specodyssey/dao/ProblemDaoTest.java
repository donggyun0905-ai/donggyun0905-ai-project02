package com.specodyssey.dao;

import com.specodyssey.dto.ProblemDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProblemDaoTest {

    private final ProblemDao dao = new ProblemDao();

    @Test
    void insert_findAll_findByDifficultyLevel() throws Exception {
        ProblemDto problem = new ProblemDto();
        problem.setTitle("두 수의 합");
        problem.setDescription("A+B를 출력하시오");
        problem.setDifficultyLevel(1);
        problem.setSourceType("AI_GENERATED");
        problem.setAnswerKey("3");

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, problem);
        }
        try {
            assertNotNull(id);
            assertTrue(dao.findAll().stream().anyMatch(p -> p.getId().equals(id)));

            List<ProblemDto> lv1 = dao.findByDifficultyLevel(1);
            assertTrue(lv1.stream().anyMatch(p -> p.getId().equals(id)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "PROBLEM", id);
            }
        }
    }
}
