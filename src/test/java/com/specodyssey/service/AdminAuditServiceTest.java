package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.AdminAuditLogDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 관리자 감사 로그 — 기록이 남고, 행동으로 걸러지고, 계정이 지워져도 누구였는지 남는다. */
class AdminAuditServiceTest {

    private static UserDto admin;
    private final AdminAuditService service = new AdminAuditService();

    @BeforeAll
    static void setUp() throws Exception {
        admin = new UserDto();
        admin.setUserType("ADMIN");
        admin.setLoginId("test_audit_" + System.nanoTime());
        admin.setPasswordHash("dummy_hash");
        admin.setDesiredJobStatus("UNSET");
        admin.setPrivacyConsentAt(LocalDateTime.now());
        admin.setId(new UserDao().insert(admin));
    }

    @AfterEach
    void clearLogs() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "ADMIN_AUDIT_LOG", "admin_user_id", admin.getId());
            // 계정이 지워져 admin_user_id가 NULL이 된 기록도 정리한다
            try (PreparedStatement p = conn.prepareStatement(
                    "DELETE FROM ADMIN_AUDIT_LOG WHERE admin_login_id = ?")) {
                p.setString(1, admin.getLoginId());
                p.executeUpdate();
            }
        }
    }

    private AdminAuditLogDto onlyLog(String action) throws Exception {
        List<AdminAuditLogDto> logs = service.list(action, 1).stream()
                .filter(l -> admin.getLoginId().equals(l.getAdminLoginId()))
                .toList();
        assertEquals(1, logs.size(), "이 테스트가 남긴 기록만 1건이어야 한다: " + logs.size());
        return logs.get(0);
    }

    @Test
    void 기록을_남기면_누가_무엇을_언제_했는지_조회된다() throws Exception {
        service.record(admin, AdminAuditService.ARTICLE_HIDE, "TECH_ARTICLE", 1234L, "사유: 광고성 글");

        AdminAuditLogDto log = onlyLog(AdminAuditService.ARTICLE_HIDE);
        assertEquals(admin.getId(), log.getAdminUserId());
        assertEquals(admin.getLoginId(), log.getAdminLoginId());
        assertEquals("TECH_ARTICLE", log.getTargetType());
        assertEquals(1234L, log.getTargetId());
        assertEquals("사유: 광고성 글", log.getDetail());
        assertNotNull(log.getCreatedAt());
        assertEquals("글 내림", log.getActionLabel(), "화면에 쓸 한글 이름을 서비스가 채운다");
        assertTrue(log.getCreatedAtText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"), log.getCreatedAtText());
    }

    @Test
    void 행동으로_걸러서_볼_수_있다() throws Exception {
        service.record(admin, AdminAuditService.ARTICLE_HIDE, "TECH_ARTICLE", 1L, null);
        service.record(admin, AdminAuditService.USER_PASSWORD_RESET, "USERS", 2L, null);

        assertEquals(AdminAuditService.ARTICLE_HIDE, onlyLog(AdminAuditService.ARTICLE_HIDE).getAction());
        assertEquals(AdminAuditService.USER_PASSWORD_RESET, onlyLog(AdminAuditService.USER_PASSWORD_RESET).getAction());
        assertTrue(service.usedActions().contains(AdminAuditService.ARTICLE_HIDE));
    }

    @Test
    void 작업이_롤백되면_기록도_함께_사라진다() throws Exception {
        int before = service.count(AdminAuditService.ARTICLE_HIDE);
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            service.record(conn, admin, AdminAuditService.ARTICLE_HIDE, "TECH_ARTICLE", 99L, "되돌릴 작업");
            conn.rollback();
            conn.setAutoCommit(true);
        }
        assertEquals(before, service.count(AdminAuditService.ARTICLE_HIDE),
                "하지 않은 일이 기록에 남으면 안 된다");
    }

    @Test
    void 관리자_계정이_지워져도_누구였는지_남는다() throws Exception {
        UserDto temp = new UserDto();
        temp.setUserType("ADMIN");
        temp.setLoginId("test_audit_gone_" + System.nanoTime());
        temp.setPasswordHash("dummy_hash");
        temp.setDesiredJobStatus("UNSET");
        temp.setPrivacyConsentAt(LocalDateTime.now());
        temp.setId(new UserDao().insert(temp));
        service.record(temp, AdminAuditService.USER_SOFT_DELETE, "USERS", 7L, null);

        try (Connection conn = DBUtil.getConnection()) {
            // ON DELETE SET NULL이라 기록이 있어도 계정을 지울 수 있다
            TestFixtures.hardDelete(conn, "USERS", temp.getId());
        }

        List<AdminAuditLogDto> logs = service.list(AdminAuditService.USER_SOFT_DELETE, 1).stream()
                .filter(l -> temp.getLoginId().equals(l.getAdminLoginId()))
                .toList();
        assertEquals(1, logs.size());
        assertNull(logs.get(0).getAdminUserId(), "계정 id는 NULL이 된다");
        assertEquals(temp.getLoginId(), logs.get(0).getAdminLoginId(), "아이디는 기록에 남는다");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement("DELETE FROM ADMIN_AUDIT_LOG WHERE admin_login_id = ?")) {
            p.setString(1, temp.getLoginId());
            p.executeUpdate();
        }
    }

    @Test
    void 사유가_아주_길어도_저장된다() {
        String long500 = "가".repeat(600);
        assertEquals(500, AdminAuditService.trimDetail(long500).length(), "VARCHAR(500)을 넘기지 않는다");
        assertNull(AdminAuditService.trimDetail("   "), "빈 사유는 남기지 않는다");
    }
}
