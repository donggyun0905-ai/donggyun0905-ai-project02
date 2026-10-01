package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiUsageLogService 통합테스트. 관련 요구사항: FR-101(선택) · 102(선택), NFR-4
 */
class AiUsageLogServiceTest {

    private final UserDao userDao = new UserDao();
    private final AiUsageLogService service = new AiUsageLogService();

    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("ai_usage_test_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "AI_USAGE_LOG", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void submit_하면_목록에서_그대로_조회된다() throws Exception {
        service.submit(userId, "ChatGPT로 버그 디버깅", "에러 메시지를 분석해달라고 물어봤다", true);

        List<AiUsageLogService.UsageEntry> entries = service.listMine(userId);

        assertEquals(1, entries.size());
        assertEquals("ChatGPT로 버그 디버깅", entries.get(0).title());
        assertEquals("에러 메시지를 분석해달라고 물어봤다", entries.get(0).description());
        assertTrue(entries.get(0).shared());
    }

    @Test
    void 제목이_없으면_예외() {
        assertThrows(IllegalArgumentException.class, () -> service.submit(userId, "  ", "설명", false));
        assertThrows(IllegalArgumentException.class, () -> service.submit(userId, null, "설명", false));
    }

    @Test
    void setShared로_공유_여부를_바꿀_수_있다() throws Exception {
        service.submit(userId, "제목", "설명", false);
        Long logId = service.listMine(userId).get(0).id();

        service.setShared(userId, logId, true);

        assertTrue(service.listMine(userId).get(0).shared());
    }

    @Test
    void 다른_사용자_id로_setShared를_호출해도_아무_영향이_없다() throws Exception {
        service.submit(userId, "제목", "설명", false);
        Long logId = service.listMine(userId).get(0).id();

        service.setShared(userId + 999_999L, logId, true);

        assertFalse(service.listMine(userId).get(0).shared());
    }

    @Test
    void delete_하면_목록에서_사라진다() throws Exception {
        service.submit(userId, "제목", "설명", false);
        Long logId = service.listMine(userId).get(0).id();

        service.delete(userId, logId);

        assertTrue(service.listMine(userId).isEmpty());
    }
}
