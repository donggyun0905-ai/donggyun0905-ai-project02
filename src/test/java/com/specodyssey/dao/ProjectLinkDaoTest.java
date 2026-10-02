package com.specodyssey.dao;

import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.TransactionUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectLinkDaoTest {

    private static final UserDao userDao = new UserDao();
    private final ProjectLinkDao dao = new ProjectLinkDao();
    private static Long userId;
    private static Long projectA;
    private static Long projectB;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_plink_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
        projectA = newProject("A");
        projectB = newProject("B");
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : new Long[] {projectA, projectB}) {
                TestFixtures.hardDeleteByColumn(conn, "PROJECT_LINK", "project_id", id);
            }
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    private static Long newProject(String title) throws Exception {
        UserProjectDto p = new UserProjectDto();
        p.setUserId(userId);
        p.setTitle(title);
        return new UserProjectDao().insert(p);
    }

    private void replace(Long projectId, ProjectLinkDto... links) throws Exception {
        TransactionUtil.runInTransaction(conn -> {
            dao.replaceForProject(conn, projectId, List.of(links));
            return null;
        });
    }

    @Test
    void 통째로_바꾸면_입력_순서가_유지되고_이전_줄은_지워진다() throws Exception {
        replace(projectA, new ProjectLinkDto("블로그", "https://blog.example.com"), new ProjectLinkDto(null, "https://youtu.be/x"));
        List<ProjectLinkDto> first = dao.findByProjectId(projectA);
        assertEquals(2, first.size());
        assertEquals("블로그", first.get(0).getLabel());
        assertEquals(0, first.get(0).getSortOrder());
        assertEquals(1, first.get(1).getSortOrder());

        replace(projectA, new ProjectLinkDto("노션", "https://notion.so/p"));
        List<ProjectLinkDto> second = dao.findByProjectId(projectA);
        assertEquals(1, second.size());
        assertEquals("노션", second.get(0).getLabel());

        replace(projectA); // 빈 목록 = 모두 지움
        assertTrue(dao.findByProjectId(projectA).isEmpty());
    }

    @Test
    void 여러_프로젝트의_링크를_한_번에_묶어_읽고_다른_프로젝트와_섞이지_않는다() throws Exception {
        replace(projectA, new ProjectLinkDto("가", "https://a.example.com"));
        replace(projectB, new ProjectLinkDto("나", "https://b.example.com"), new ProjectLinkDto("다", "https://c.example.com"));

        Map<Long, List<ProjectLinkDto>> byProject = dao.findByProjectIds(List.of(projectA, projectB));

        assertEquals(1, byProject.get(projectA).size());
        assertEquals(2, byProject.get(projectB).size());
        assertEquals(projectB, byProject.get(projectB).get(0).getProjectId());
        assertTrue(dao.findByProjectIds(List.of()).isEmpty());
        assertFalse(dao.findByProjectIds(List.of(-1L)).containsKey(-1L));
    }
}
