package com.specodyssey.dao;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserProjectDaoTest {

    private static final UserDao userDao = new UserDao();
    private static final UserProjectDao projectDao = new UserProjectDao();
    private static Long userId;

    @BeforeAll
    static void setUpUser() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_project_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterAll
    static void tearDownUser() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            // delete()는 논리 삭제라 행이 물리적으로 남아있다 — USERS를 지우기 전에 자식부터 정리
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_update_delete() throws Exception {
        UserProjectDto project = new UserProjectDto();
        project.setUserId(userId);
        project.setTitle("스펙 오디세이");
        project.setDescription("취준생 진단 서비스");
        project.setTechStack("Java, JSP, MySQL");
        project.setStartDate(LocalDate.of(2026, 1, 1));
        project.setEndDate(LocalDate.of(2026, 2, 1));

        Long projectId;
        try (Connection conn = DBUtil.getConnection()) {
            projectId = projectDao.insert(conn, project);
        }
        assertNotNull(projectId);

        List<UserProjectDto> projects = projectDao.findByUserId(userId);
        assertEquals(1, projects.size());
        assertEquals("스펙 오디세이", projects.get(0).getTitle());

        UserProjectDto toUpdate = projects.get(0);
        toUpdate.setTitle("스펙 오디세이 v2");
        try (Connection conn = DBUtil.getConnection()) {
            projectDao.update(conn, toUpdate, userId);
        }
        assertEquals("스펙 오디세이 v2", projectDao.findByUserId(userId).get(0).getTitle());

        try (Connection conn = DBUtil.getConnection()) {
            projectDao.delete(conn, projectId, userId);
        }
        assertTrue(projectDao.findByUserId(userId).isEmpty());
    }
}
