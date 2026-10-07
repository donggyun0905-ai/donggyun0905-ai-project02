package com.specodyssey.controller;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 보유 기술 추가에서 입력 실수를 다루는 방식 — 중복·엉터리·빈 값에 에러 페이지를 띄우지 않고
 * 프로필로 돌려보내며 안내만 한다(2026-10-07 사용자 제보: 이미 있는 기술을 넣으면 409 에러 페이지).
 */
class ProfileSkillServletTest {

    private static UserDto user;
    private final ProfileSkillServlet servlet = new ProfileSkillServlet();

    @BeforeAll
    static void setUp() throws Exception {
        user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_skillsv_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        user.setId(new UserDao().insert(user));
    }

    @AfterEach
    void clearSkills() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", user.getId());
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "USER_SKILLS", "user_id", user.getId());
            TestFixtures.hardDelete(conn, "USERS", user.getId());
        }
    }

    private FakeWeb.Request post(String rawInput) {
        return FakeWeb.request().post("/profile/skills").loggedIn(user).param("rawInput", rawInput);
    }

    @Test
    void 같은_기술을_또_넣으면_에러_페이지_대신_프로필에서_안내한다() throws Exception {
        FakeWeb.Request first = post("Java");
        FakeWeb.Response firstResp = FakeWeb.response();
        servlet.doPost(first.http(), firstResp.http());
        assertEquals(0, firstResp.errorStatus, "처음 추가는 정상이어야 한다");

        FakeWeb.Request again = post("Java");
        FakeWeb.Response againResp = FakeWeb.response();
        servlet.doPost(again.http(), againResp.http());

        assertEquals(0, againResp.errorStatus, "409를 보내면 컨테이너 에러 페이지가 뜬다");
        assertTrue(againResp.redirect != null && againResp.redirect.endsWith("/profile"),
                "프로필로 돌려보내야 한다: " + againResp.redirect);
        Object notice = again.session.attributes.get("profileErrorNotice");
        assertNotNull(notice, "안내 문구를 세션에 남겨야 한다");
        assertTrue(notice.toString().contains("이미 등록된 기술"), notice.toString());
    }

    @Test
    void 엉터리_글자는_저장하지_않고_안내한다() throws Exception {
        FakeWeb.Request req = post("ㅇㅁ닒ㄴㅇㄹㅇㄴㄹ");
        FakeWeb.Response resp = FakeWeb.response();
        servlet.doPost(req.http(), resp.http());

        assertEquals(0, resp.errorStatus, "화면 검사를 건너뛴 요청도 에러 페이지로 끝내지 않는다");
        assertTrue(resp.redirect != null && resp.redirect.endsWith("/profile"), String.valueOf(resp.redirect));
        Object notice = req.session.attributes.get("profileErrorNotice");
        assertNotNull(notice, "안내 문구를 남겨야 한다");
        assertTrue(notice.toString().contains("의미 없는 글자"), notice.toString());
    }

    @Test
    void 빈_값도_에러_페이지_없이_안내한다() throws Exception {
        FakeWeb.Request req = post("   ");
        FakeWeb.Response resp = FakeWeb.response();
        servlet.doPost(req.http(), resp.http());

        assertEquals(0, resp.errorStatus);
        assertTrue(resp.redirect != null && resp.redirect.endsWith("/profile"), String.valueOf(resp.redirect));
        assertNotNull(req.session.attributes.get("profileErrorNotice"));
    }

    @Test
    void 안내_문구는_프로필을_한_번_보면_사라진다() throws Exception {
        FakeWeb.Request post = post("   ");
        servlet.doPost(post.http(), FakeWeb.response().http());
        assertNotNull(post.session.attributes.get("profileErrorNotice"));

        // 같은 세션으로 프로필을 열면 요청 속성으로 옮기고 세션에서는 지운다
        FakeWeb.Request view = FakeWeb.request().loggedIn(user);
        view.session.attributes.putAll(post.session.attributes);
        ProfileNotice.consume(view.http());

        assertNotNull(view.attributes.get("errorMessage"), "화면에 보여 줄 문구로 옮겨야 한다");
        assertEquals(null, view.session.attributes.get("profileErrorNotice"), "새로고침하면 다시 뜨지 않아야 한다");
    }
}
