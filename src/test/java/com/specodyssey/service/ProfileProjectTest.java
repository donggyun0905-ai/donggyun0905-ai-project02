package com.specodyssey.service;

import com.specodyssey.dao.ProjectLinkDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 프로필 화면의 프로젝트 추가·수정 — 저장소·배포·기타 링크, 그리고 수정이 로드맵에서 낸 값을 지우지 않는지. */
class ProfileProjectTest {

    private static final UserDao userDao = new UserDao();
    private final ProfileService service = new ProfileService();
    private final UserProjectDao projectDao = new UserProjectDao();
    private final ProjectLinkDao linkDao = new ProjectLinkDao();
    private static Long userId;
    private static Long otherUserId;

    @BeforeAll
    static void setUp() throws Exception {
        userId = newUser("test_profproj_a_");
        otherUserId = newUser("test_profproj_b_");
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : new Long[] {userId, otherUserId}) {
                for (UserProjectDto p : new UserProjectDao().findByUserId(id)) {
                    TestFixtures.hardDeleteByColumn(conn, "PROJECT_LINK", "project_id", p.getId());
                }
                TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", id);
                // SpecScoreScheduler는 웹앱이 뜨는 순간 전체 사용자에게 스냅샷을 남긴다 — 누가 같은 공유 DB로
                // 서버를 띄워 두면 테스트가 방금 만든 사용자 몫까지 생긴다. USERS 바로 앞에서 지운다.
                TestFixtures.hardDeleteByColumn(conn, "SPEC_SCORE_HISTORY", "user_id", id);
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    private static Long newUser(String prefix) throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(prefix + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        return userDao.insert(user);
    }

    private UserProjectDto project(String title) {
        UserProjectDto p = new UserProjectDto();
        p.setTitle(title);
        return p;
    }

    @Test
    void 추가할_때_저장소_배포_기타_링크가_함께_저장된다() throws Exception {
        UserProjectDto p = project("링크 있는 프로젝트");
        p.setRepoUrl("https://github.com/a/b");
        p.setDeployUrl("https://app.example.com");

        Long id = service.addProject(userId, p, List.of(new ProjectLinkDto("블로그", "https://blog.example.com/x")));

        UserProjectDto saved = projectDao.findById(id, userId);
        assertEquals("https://github.com/a/b", saved.getRepoUrl());
        assertEquals("https://app.example.com", saved.getDeployUrl());
        assertEquals(1, linkDao.findByProjectId(id).size());
        assertEquals(1, service.getProjectLinks(List.of(saved)).get(id).size());
    }

    @Test
    void 수정해도_로드맵에서_낸_완료_회고는_지워지지_않는다() throws Exception {
        UserProjectDto p = project("회고 있는 프로젝트");
        p.setRetrospective("로드맵에서 적은 회고");
        Long id = service.addProject(userId, p);

        UserProjectDto edit = project("제목만 바꿈");
        edit.setId(id);
        edit.setRepoUrl("https://github.com/new/repo");
        service.updateProject(userId, edit, null);

        UserProjectDto after = projectDao.findById(id, userId);
        assertEquals("제목만 바꿈", after.getTitle());
        assertEquals("https://github.com/new/repo", after.getRepoUrl());
        assertEquals("로드맵에서 적은 회고", after.getRetrospective(), "프로필 수정 화면에는 회고 칸이 없으므로 기존 값을 유지한다");
    }

    @Test
    void 링크_입력칸이_없으면_기존_링크를_두고_빈_목록이면_모두_지운다() throws Exception {
        Long id = service.addProject(userId, project("링크 관리"), List.of(new ProjectLinkDto("영상", "https://youtu.be/1")));

        UserProjectDto edit = project("링크 관리");
        edit.setId(id);
        service.updateProject(userId, edit, null);
        assertEquals(1, linkDao.findByProjectId(id).size());

        service.updateProject(userId, edit, List.of());
        assertTrue(linkDao.findByProjectId(id).isEmpty());
    }

    @Test
    void 남의_프로젝트는_수정할_수_없고_링크도_바뀌지_않는다() throws Exception {
        Long id = service.addProject(userId, project("내 프로젝트"), List.of(new ProjectLinkDto("내 글", "https://mine.example.com")));

        UserProjectDto hijack = project("탈취");
        hijack.setId(id);
        service.updateProject(otherUserId, hijack, List.of(new ProjectLinkDto("가짜", "https://evil.example.com")));

        assertEquals("내 프로젝트", projectDao.findById(id, userId).getTitle());
        assertEquals("내 글", linkDao.findByProjectId(id).get(0).getLabel());
    }

    @Test
    void 웹_주소가_아닌_값은_저장하지_않는다() throws Exception {
        UserProjectDto p = project("나쁜 주소");
        p.setRepoUrl("javascript:alert(1)");
        assertThrows(IllegalArgumentException.class, () -> service.addProject(userId, p));

        UserProjectDto q = project("나쁜 링크");
        assertThrows(IllegalArgumentException.class,
                () -> service.addProject(userId, q, List.of(new ProjectLinkDto("x", "data:text/html,1"))));
        assertNull(projectDao.findByUserId(userId).stream().filter(x -> x.getTitle().startsWith("나쁜")).findFirst().orElse(null));
    }
}
