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
    void 잘못된_입력은_에러_페이지가_아니라_프로필_안내로_돌아가고_아무것도_저장하지_않는다() throws Exception {
        // 예전에는 sendError(400)을 썼는데, web.xml에 400 항목이 없어 컨테이너 기본 에러 페이지가 떴다 —
        // 적던 내용이 통째로 사라져서 세션 안내(ProfileNotice) + /profile 리다이렉트로 바꿨다 (2026-10-07).
        long before = projectDao.findByUserId(user.getId()).size();

        FakeWeb.Request bad = post("나쁜 입력").param("repoUrl", "javascript:alert(1)");
        FakeWeb.Response resp = FakeWeb.response();
        servlet.doPost(bad.http(), resp.http());

        assertEquals("/profile", resp.redirect);
        assertEquals(0, resp.errorStatus, "에러 페이지로 보내면 입력하던 내용이 날아간다");
        assertTrue(notice(bad).contains("코드 저장소"), "실제 문구: " + notice(bad));
        assertEquals(before, projectDao.findByUserId(user.getId()).size());

        FakeWeb.Request badLink = post("링크 나쁨").param("linkLabel_0", "x").param("linkUrl_0", "data:text/html,1");
        FakeWeb.Response resp2 = FakeWeb.response();
        servlet.doPost(badLink.http(), resp2.http());
        assertEquals("/profile", resp2.redirect);
        assertNotNull(notice(badLink));

        FakeWeb.Request badDate = post("날짜 이상").param("startDate", "어제");
        FakeWeb.Response resp3 = FakeWeb.response();
        servlet.doPost(badDate.http(), resp3.http());
        assertEquals("/profile", resp3.redirect);
        assertNotNull(notice(badDate));

        FakeWeb.Request noTitle = FakeWeb.request().post("/profile/projects").loggedIn(user);
        FakeWeb.Response resp4 = FakeWeb.response();
        servlet.doPost(noTitle.http(), resp4.http());
        assertEquals("/profile", resp4.redirect);
        assertTrue(notice(noTitle).contains("프로젝트명"));

        assertEquals(before, projectDao.findByUserId(user.getId()).size(), "실패한 요청은 아무것도 남기지 않는다");
    }

    /** ProfileNotice가 세션에 넣어 둔 문구 — 다음 /profile 조회에서 꺼내 쓴다 */
    private static String notice(FakeWeb.Request req) {
        return (String) req.session.attributes.get("profileErrorNotice");
    }
}
