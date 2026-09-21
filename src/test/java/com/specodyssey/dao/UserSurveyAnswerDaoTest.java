package com.specodyssey.dao;

import com.specodyssey.dto.SurveyQuestionDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSurveyAnswerDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserSurveyAnswerDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final SurveyQuestionDao questionDao = new SurveyQuestionDao();
    private final UserSurveyAnswerDao dao = new UserSurveyAnswerDao();

    private static Long userId;
    private static Long questionId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_answer_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        SurveyQuestionDto question = new SurveyQuestionDto();
        question.setSurveyType("SELF_CHECK");
        question.setContent("코딩 경험이 있나요?");
        question.setScoreWeight(5);
        try (Connection conn = DBUtil.getConnection()) {
            questionId = questionDao.insert(conn, question);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "SURVEY_QUESTION", questionId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId() throws Exception {
        UserSurveyAnswerDto answer = new UserSurveyAnswerDto();
        answer.setUserId(userId);
        answer.setQuestionId(questionId);
        answer.setAnswerValue(4);
        answer.setAnsweredAt(LocalDateTime.now());

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, answer);
        }
        try {
            assertNotNull(id);
            List<UserSurveyAnswerDto> answers = dao.findByUserId(userId);
            assertEquals(1, answers.size());
            assertEquals(4, answers.get(0).getAnswerValue());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "USER_SURVEY_ANSWER", id);
            }
        }
    }
}
