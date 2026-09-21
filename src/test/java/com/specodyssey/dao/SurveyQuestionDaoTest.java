package com.specodyssey.dao;

import com.specodyssey.dto.SurveyQuestionDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SurveyQuestionDaoTest {

    private final SurveyQuestionDao dao = new SurveyQuestionDao();

    @Test
    void insert_findAll_findBySurveyType() throws Exception {
        SurveyQuestionDto question = new SurveyQuestionDto();
        question.setSurveyType("JOB_DISCOVERY");
        question.setContent("데이터 다루는 걸 좋아하나요?");
        question.setJobCategoryHint("DATA");
        question.setScoreWeight(10);

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, question);
        }
        try {
            assertNotNull(id);
            assertTrue(dao.findAll().stream().anyMatch(q -> q.getId().equals(id)));

            List<SurveyQuestionDto> discovery = dao.findBySurveyType("JOB_DISCOVERY");
            assertTrue(discovery.stream().anyMatch(q -> q.getId().equals(id)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SURVEY_QUESTION", id);
            }
        }
    }
}
