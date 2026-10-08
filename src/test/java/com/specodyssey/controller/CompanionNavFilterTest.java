package com.specodyssey.controller;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.PersonalInfo;
import com.specodyssey.service.UserService;
import com.specodyssey.service.companion.CompanionAuthService;
import com.specodyssey.util.DBUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 메뉴의 데스크톱 캐릭터 항목이 어떤 모습으로 나올지 (2026-10-08).
 * 설치 파일이 있는지는 1분 캐시라서, DB에 올리는 대신 캐시를 직접 채워 상황을 만든다 —
 * 공유 DB에 올라간 버전이 있든 없든 결과가 같아야 하므로.
 */
class CompanionNavFilterTest {

    private final UserService userService = new UserService();
    private final UserDao userDao = new UserDao();
    private final CompanionAuthService authService = new CompanionAuthService();
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : created) {
                TestFixtures.hardDeleteByColumn(conn, "COMPANION_DEVICE", "user_id", id);
                TestFixtures.hardDeleteByColumn(conn, "EVALUATION_SESSION", "user_id", id);
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    @Test
    @DisplayName("설치 파일을 받아간 적이 없으면 [캐릭터 내려받기]")
    void 받아간_적이_없으면_내려받기() throws Exception {
        FakeWeb.Request req = loggedInRequest(newUser());
        filterWithRelease(true).doFilter(req.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
        assertEquals("download", req.attributes.get("companionNavState"));
    }

    @Test
    @DisplayName("받아갔으면 [캐릭터 켜기]로 바뀐다 — 프로필 버튼과 같은 일을 한다")
    void 받아갔으면_켜기로_바뀐다() throws Exception {
        FakeWeb.Request req = loggedInRequest(newUser());
        req.session.attributes.put(CompanionServlet.DOWNLOADED_ATTR, Boolean.TRUE);
        filterWithRelease(true).doFilter(req.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
        assertEquals("launch", req.attributes.get("companionNavState"));
    }

    @Test
    @DisplayName("연결된 PC가 있으면 항목을 아예 숨긴다 — 설치·연결까지 끝났으니 할 일이 없다")
    void 연결된_PC가_있으면_숨긴다() throws Exception {
        UserDto user = newUser();
        String code = authService.issueCode(user.getId());
        assertTrue(authService.exchange(code, "테스트 PC") != null, "연결이 만들어져야 한다");

        FakeWeb.Request req = loggedInRequest(user);
        req.session.attributes.put(CompanionServlet.DOWNLOADED_ATTR, Boolean.TRUE);
        filterWithRelease(true).doFilter(req.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
        assertNull(req.attributes.get("companionNavState"));
    }

    @Test
    @DisplayName("올라간 설치 파일이 없으면 숨긴다 — 눌러도 받을 게 없다")
    void 설치_파일이_없으면_숨긴다() throws Exception {
        FakeWeb.Request req = loggedInRequest(newUser());
        filterWithRelease(false).doFilter(req.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
        assertNull(req.attributes.get("companionNavState"));
    }

    @Test
    @DisplayName("면접관 계정에는 안 보인다 — 캐릭터는 구직자용이다")
    void 면접관에게는_안_보인다() throws Exception {
        UserDto user = newUser();
        user.setUserType(RoleFilter.INTERVIEWER);
        FakeWeb.Request req = loggedInRequest(user);
        filterWithRelease(true).doFilter(req.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
        assertNull(req.attributes.get("companionNavState"));
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 건드리지 않고, 체인은 언제나 이어진다")
    void 로그인_전에는_건드리지_않는다() throws Exception {
        FakeWeb.Request req = FakeWeb.request();
        req.servletPath = "/login";
        FakeWeb.Chain chain = new FakeWeb.Chain();
        filterWithRelease(true).doFilter(req.http(), FakeWeb.response().http(), chain.chain());
        assertNull(req.attributes.get("companionNavState"));
        assertTrue(chain.called(), "메뉴 판단이 요청을 막아서는 안 된다");
    }

    @Test
    @DisplayName("정적 자산·API 요청에서는 DB를 보지 않는다")
    void 정적_요청은_건너뛴다() throws Exception {
        for (String path : List.of("/css/style.css", "/js/app.js", "/image/logo.png", "/api/companion/messages")) {
            FakeWeb.Request req = loggedInRequest(newUser());
            req.servletPath = path;
            filterWithRelease(true).doFilter(req.http(), FakeWeb.response().http(), new FakeWeb.Chain().chain());
            assertNull(req.attributes.get("companionNavState"), path);
        }
    }

    /** 설치 파일 유무 캐시를 원하는 값으로 채운 필터 — DB 상태와 무관하게 만든다. */
    private static CompanionNavFilter filterWithRelease(boolean exists) throws Exception {
        CompanionNavFilter filter = new CompanionNavFilter();
        set(filter, "releaseExists", exists);
        set(filter, "releaseCheckedAt", System.currentTimeMillis());
        return filter;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = CompanionNavFilter.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private UserDto newUser() throws Exception {
        Long id = userService.register("test_nav_" + System.nanoTime(), "Passw0rd!x", null,
                PersonalInfo.of("캐릭터메뉴", "25", "JOB_SEEKER", null), null, null);
        created.add(id);
        return userDao.findById(id);
    }

    private static FakeWeb.Request loggedInRequest(UserDto user) {
        FakeWeb.Request r = FakeWeb.request().loggedIn(user);
        r.servletPath = "/dashboard";
        return r;
    }
}
