package com.specodyssey.dao;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserSkillDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final UserSkillDao skillDao = new UserSkillDao();
    private static Long userId;

    @BeforeAll
    static void setUpUser() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_skill_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterAll
    static void tearDownUser() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            // delete()는 논리 삭제라 행이 물리적으로 남아있다 — USERS를 지우기 전에 자식부터 정리
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_duplicateCheck_update_delete() throws Exception {
        UserSkillDto skill = new UserSkillDto();
        skill.setUserId(userId);
        skill.setRawInput("Python");
        skill.setProficiency("INTERMEDIATE");

        Long skillRowId;
        try (Connection conn = DBUtil.getConnection()) {
            skillRowId = skillDao.insert(conn, skill);
        }
        assertNotNull(skillRowId);

        assertTrue(skillDao.existsActiveRawInput(userId, "python"));
        assertFalse(skillDao.existsActiveRawInput(userId, "python", skillRowId));

        List<UserSkillDto> skills = skillDao.findByUserId(userId);
        assertEquals(1, skills.size());
        assertEquals("Python", skills.get(0).getRawInput());

        UserSkillDto toUpdate = skills.get(0);
        toUpdate.setRawInput("Java");
        try (Connection conn = DBUtil.getConnection()) {
            skillDao.update(conn, toUpdate, userId);
        }
        assertEquals("Java", skillDao.findByUserId(userId).get(0).getRawInput());

        try (Connection conn = DBUtil.getConnection()) {
            skillDao.delete(conn, skillRowId, userId);
        }
        assertTrue(skillDao.findByUserId(userId).isEmpty());
    }
}
