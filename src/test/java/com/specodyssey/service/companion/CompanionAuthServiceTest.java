package com.specodyssey.service.companion;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.CompanionDeviceDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 데스크톱 캐릭터 연결 — 일회용 코드 → 토큰, 만료·재사용·해제·남의 연결 (실제 DB) */
class CompanionAuthServiceTest {

    private final UserDao userDao = new UserDao();
    private final CompanionAuthService service = new CompanionAuthService();
    private Long userId;
    private Long otherId;

    @BeforeEach
    void setUp() throws Exception {
        userId = newUser("companion_test_" + System.nanoTime());
        otherId = newUser("companion_other_" + System.nanoTime());
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (Long id : new Long[]{userId, otherId}) {
                TestFixtures.hardDeleteByColumn(conn, "COMPANION_DEVICE", "user_id", id);
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    @Test
    void 코드를_토큰으로_바꾸면_그_토큰으로_사용자를_찾는다() throws Exception {
        String code = service.issueCode(userId);
        CompanionAuthService.Connected connected = service.exchange(code, "TEST-PC");
        assertNotNull(connected);
        assertEquals(userId, connected.userId());

        CompanionDeviceDto device = service.authenticate(connected.token());
        assertNotNull(device);
        assertEquals(userId, device.getUserId());
        assertEquals("TEST-PC", device.getDeviceName());
        assertEquals(1, service.connectedDevices(userId).size());
    }

    @Test
    void 코드는_한_번만_쓸_수_있다() throws Exception {
        String code = service.issueCode(userId);
        assertNotNull(service.exchange(code, "PC"));
        assertNull(service.exchange(code, "PC"), "같은 코드로 두 번 연결되면 안 된다");
    }

    @Test
    void 만료된_코드는_거절한다() throws Exception {
        String code = service.issueCode(userId);
        sql("UPDATE COMPANION_DEVICE SET code_expires_at = ? WHERE user_id = ?",
                LocalDateTime.now().minusMinutes(5), userId);
        assertNull(service.exchange(code, "PC"));
    }

    @Test
    void 없는_토큰이나_빈_값은_거절한다() throws Exception {
        assertNull(service.authenticate("없는토큰"));
        assertNull(service.authenticate(null));
        assertNull(service.exchange("없는코드", "PC"));
        assertNull(service.exchange("", "PC"));
    }

    @Test
    void 연결_해제하면_토큰이_더_이상_통하지_않고_남은_해제할_수_없다() throws Exception {
        String token = service.exchange(service.issueCode(userId), "PC").token();
        CompanionDeviceDto device = service.authenticate(token);

        assertFalse(service.revoke(otherId, device.getId()), "남의 연결은 해제할 수 없다");
        assertNotNull(service.authenticate(token));

        assertTrue(service.revoke(userId, device.getId()));
        assertNull(service.authenticate(token));
        assertTrue(service.connectedDevices(userId).isEmpty());
    }

    @Test
    void DB에는_코드와_토큰_원문이_없다() throws Exception {
        String code = service.issueCode(userId);
        assertEquals(0, count("SELECT COUNT(*) FROM COMPANION_DEVICE WHERE connect_code_hash = ?", code));
        String token = service.exchange(code, "PC").token();
        assertEquals(0, count("SELECT COUNT(*) FROM COMPANION_DEVICE WHERE token_hash = ?", token));
        assertEquals(1, count("SELECT COUNT(*) FROM COMPANION_DEVICE WHERE token_hash = ?", CompanionAuthService.hash(token)));
    }

    // ----------------------------------------------------------------

    private Long newUser(String loginId) throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(loginId);
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        return userDao.insert(user);
    }

    private static int count(String sql, Object... params) throws Exception {
        try (Connection conn = DBUtil.getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                p.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = p.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void sql(String sql, Object... params) throws Exception {
        try (Connection conn = DBUtil.getConnection(); PreparedStatement p = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                p.setObject(i + 1, params[i]);
            }
            p.executeUpdate();
        }
    }
}
