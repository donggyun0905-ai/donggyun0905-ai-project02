package com.specodyssey.controller;

import com.specodyssey.dao.ProjectLinkDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 프로필 화면의 프로젝트 추가·수정 폼 처리 — 새 입력칸(저장소·배포·기타 링크)과 잘못된 입력의 400 응답. */
class ProfileProjectServletTest {

    private static UserDto user;
    private final ProfileProjectServlet servlet = new ProfileProjectServlet();
    private final UserProjectDao projectDao = new UserProjectDao();

    @BeforeAll
    static void setUp() throws Exception {
        user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_profsv_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        user.setId(new UserDao().insert(user));
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (UserProjectDto p : new UserProjectDao().findByUserId(user.getId())) {
                TestFixtures.hardDeleteByColumn(conn, "PROJECT_LINK", "project_id", p.getId());
            }
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", user.getId());
            TestFixtures.hardDelete(conn, "USERS", user.getId());
        }
    }

    private FakeWeb.Request post(String title) {
        return FakeWeb.request().post("/profile/projects").loggedIn(user).param("title", title);
    }

    @Test
    void 추가_폼의_저장소_배포_기타_링크가_저장되고_프로필로_돌아간다() throws Exception {
        FakeWeb.Request req = post("서블릿 추가").param("repoUrl", " https://github.com/s/v ").param("deployUrl", "")
                .param("linkLabel_0", "글").param("linkUrl_0", "https://blog.example.com/1")
                .param("linkLabel_1", "").param("linkUrl_1", "");
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(req.http(), resp.http());

        assertEquals("/profile", resp.redirect);
        UserProjectDto saved = projectDao.findByUserId(user.getId()).stream()
                .filter(p -> "서블릿 추가".equals(p.getTitle())).findFirst().orElseThrow();
        assertEquals("https://github.com/s/v", saved.getRepoUrl());
        assertEquals(null, saved.getDeployUrl());
        assertEquals(1, new ProjectLinkDao().findByProjectId(saved.getId()).size());
    }

    @Test
    void 수정_폼에_링크_입력칸이_없으면_기존_링크가_유지된다() throws Exception {
        servlet.doPost(post("수정 대상").param("linkLabel_0", "글").param("linkUrl_0", "https://blog.example.com/2").http(),
                FakeWeb.response().http());
        UserProjectDto saved = projectDao.findByUserId(user.getId()).stream()
                .filter(p -> "수정 대상".equals(p.getTitle())).findFirst().orElseThrow();

        FakeWeb.Request edit = post("수정 대상(바뀜)").param("action", "update").param("projectId", String.valueOf(saved.getId()));
        servlet.doPost(edit.http(), FakeWeb.response().http());

        assertEquals("수정 대상(바뀜)", projectDao.findById(saved.getId(), user.getId()).getTitle());
        assertEquals(1, new ProjectLinkDao().findByProjectId(saved.getId()).size());
    }

    @Test
    void 잘못된_주소는_400이고_이유를_알려주며_아무것도_저장하지_않는다() throws Exception {
        long before = projectDao.findByUserId(user.getId()).size();
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doPost(post("나쁜 입력").param("repoUrl", "javascript:alert(1)").http(), resp.http());

        assertEquals(400, resp.errorStatus);
        assertTrue(resp.errorMessage.contains("코드 저장소"));
        assertEquals(before, projectDao.findByUserId(user.getId()).size());

        FakeWeb.Response resp2 = FakeWeb.response();
        servlet.doPost(post("링크 나쁨").param("linkLabel_0", "x").param("linkUrl_0", "data:text/html,1").http(), resp2.http());
        assertEquals(400, resp2.errorStatus);
        assertNotNull(resp2.errorMessage);

        FakeWeb.Response resp3 = FakeWeb.response();
        servlet.doPost(post("날짜 이상").param("startDate", "어제").http(), resp3.http());
        assertEquals(400, resp3.errorStatus);
    }
}
