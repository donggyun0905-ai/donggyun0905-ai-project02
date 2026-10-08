package com.specodyssey.service.companion;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.CompanionDeviceDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 한 PC = 한 연결, 그리고 캐릭터가 그 PC 브라우저의 로그인을 따라가는지 (2026-10-08, 실제 DB).
 */
class CompanionBrowserLinkTest {

    private final UserDao userDao = new UserDao();
    private final CompanionAuthService service = new CompanionAuthService();
    private Long alice;
    private Long bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = newUser("companion_link_a_" + System.nanoTime());
        bob = newUser("companion_link_b_" + System.nanoTime());
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : new Long[]{alice, bob}) {
                TestFixtures.hardDeleteByColumn(conn, "COMPANION_DEVICE", "user_id", id);
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    @Test
    @DisplayName("같은 PC에서 다시 연결하면 예전 연결은 끊긴다 — 연결된 PC가 하나만 남는다")
    void 다시_연결하면_예전_연결이_끊긴다() throws Exception {
        CompanionAuthService.Connected first = service.exchange(service.issueCode(alice), "MY-PC");
        CompanionAuthService.Connected second = service.exchange(service.issueCode(alice), "MY-PC", first.token());

        assertNull(service.authenticate(first.token()), "예전 토큰은 더 쓸 수 없어야 한다");
        assertNotNull(service.authenticate(second.token()));
        assertEquals(1, service.connectedDevices(alice).size());
    }

    @Test
    @DisplayName("다른 계정으로 다시 연결해도 그 PC의 예전 연결은 끊긴다")
    void 다른_계정으로_다시_연결해도_예전_연결이_끊긴다() throws Exception {
        CompanionAuthService.Connected first = service.exchange(service.issueCode(alice), "MY-PC");
        service.exchange(service.issueCode(bob), "MY-PC", first.token());

        assertEquals(0, service.connectedDevices(alice).size());
        assertEquals(1, service.connectedDevices(bob).size());
    }

    @Test
    @DisplayName("코드가 틀리면 예전 연결은 그대로 둔다")
    void 코드가_틀리면_예전_연결은_그대로() throws Exception {
        CompanionAuthService.Connected first = service.exchange(service.issueCode(alice), "MY-PC");
        assertNull(service.exchange("틀린코드", "MY-PC", first.token()));
        assertNotNull(service.authenticate(first.token()));
    }

    @Test
    @DisplayName("연결을 마치기 전에는 브라우저 쿠키를 만들지 않는다")
    void 연결_전에는_쿠키가_없다() throws Exception {
        CompanionAuthService.Issued issued = service.issue(alice);
        assertNull(service.browserLink(issued.deviceId()));
        service.exchange(issued.code(), "MY-PC");
        assertNotNull(service.browserLink(issued.deviceId()));
    }

    @Test
    @DisplayName("그 PC 브라우저에서 다른 계정으로 로그인하면 캐릭터가 그 계정으로 옮겨 간다 — 토큰은 그대로")
    void 다른_계정으로_로그인하면_따라간다() throws Exception {
        CompanionAuthService.Issued issued = service.issue(alice);
        CompanionAuthService.Connected connected = service.exchange(issued.code(), "MY-PC");
        String link = service.browserLink(issued.deviceId());

        assertTrue(service.onLogin(link, bob, true));

        CompanionDeviceDto device = service.authenticate(connected.token());
        assertEquals(bob, device.getUserId());
        assertFalse(device.isSignedOut());
        assertEquals(0, service.connectedDevices(alice).size(), "앞 계정의 연결 목록에서는 빠진다");
    }

    @Test
    @DisplayName("로그아웃하면 쉬고, 다시 로그인하면 저절로 이어진다")
    void 로그아웃하면_쉬고_다시_로그인하면_이어진다() throws Exception {
        CompanionAuthService.Issued issued = service.issue(alice);
        CompanionAuthService.Connected connected = service.exchange(issued.code(), "MY-PC");
        String link = service.browserLink(issued.deviceId());

        assertTrue(service.onLogout(link));
        assertTrue(service.authenticate(connected.token()).isSignedOut(), "연결은 남고 쉬는 표시만 붙는다");

        assertTrue(service.onLogin(link, alice, true));
        assertFalse(service.authenticate(connected.token()).isSignedOut());
    }

    @Test
    @DisplayName("면접관·관리자로 로그인하면 캐릭터는 옮겨 가지 않고 쉰다")
    void 캐릭터를_안_쓰는_계정이면_쉰다() throws Exception {
        CompanionAuthService.Issued issued = service.issue(alice);
        CompanionAuthService.Connected connected = service.exchange(issued.code(), "MY-PC");

        assertFalse(service.onLogin(service.browserLink(issued.deviceId()), bob, false));

        CompanionDeviceDto device = service.authenticate(connected.token());
        assertEquals(alice, device.getUserId());
        assertTrue(device.isSignedOut());
    }

    @Test
    @DisplayName("직접 연결 해제한 PC는 로그인해도 되살리지 않는다")
    void 해제한_연결은_되살리지_않는다() throws Exception {
        CompanionAuthService.Issued issued = service.issue(alice);
        service.exchange(issued.code(), "MY-PC");
        String link = service.browserLink(issued.deviceId());
        service.revoke(alice, issued.deviceId());

        assertFalse(service.onLogin(link, bob, true));
        assertEquals(0, service.connectedDevices(bob).size());
    }

    @Test
    @DisplayName("쿠키를 지어내거나 고치면 받아 주지 않는다")
    void 지어낸_쿠키는_거절한다() throws Exception {
        CompanionAuthService.Issued issued = service.issue(alice);
        service.exchange(issued.code(), "MY-PC");
        String link = service.browserLink(issued.deviceId());

        assertNotNull(service.deviceForBrowserLink(link));
        assertNull(service.deviceForBrowserLink(issued.deviceId() + ".지어낸서명"));
        assertNull(service.deviceForBrowserLink((issued.deviceId() + 1) + link.substring(link.indexOf('.'))),
                "다른 행 id에 같은 서명을 붙여도 안 된다");
        assertNull(service.deviceForBrowserLink("abc"));
        assertNull(service.deviceForBrowserLink(null));
        assertFalse(service.onLogin("1.가짜", bob, true));
    }

    private Long newUser(String loginId) throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(loginId);
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        return userDao.insert(user);
    }
}
