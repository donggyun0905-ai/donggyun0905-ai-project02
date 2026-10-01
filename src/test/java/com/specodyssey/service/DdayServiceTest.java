package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.DdayAlertDto;
import com.specodyssey.dto.DdayItemDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DdayService 통합테스트. 실제 DB에 사용자와 일정을 만들고 끝나면 지운다.
 */
class DdayServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private final DdayService service = new DdayService();

    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_ddaysvc_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = new UserDao().insert(user);
    }

    @AfterEach
    void clearAlerts() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "DDAY_ALERT", "user_id", userId);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 직접_추가한_일정은_마감이_가까운_순으로_나오고_지난_일정은_맨_뒤에_온다() throws Exception {
        service.addItem(userId, "B사 서류 마감", TODAY.plusDays(18), "RECRUIT", TODAY);
        service.addItem(userId, "  A사 면접  ", TODAY.plusDays(3), "CUSTOM", TODAY);
        service.addItem(userId, "오늘 마감", TODAY, "RECRUIT", TODAY);

        // 이틀 뒤에 보면 "오늘 마감"은 지난 일정이 된다
        List<DdayItemDto> items = service.listItems(userId, TODAY.plusDays(2));

        assertEquals(3, items.size());
        assertEquals("A사 면접", items.get(0).getTitle());
        assertEquals("D-1", items.get(0).getDdayText());
        assertEquals("URGENT", items.get(0).getUrgency());
        assertEquals("기타", items.get(0).getTypeLabel());
        assertEquals("D-16", items.get(1).getDdayText());
        assertEquals("UPCOMING", items.get(1).getUrgency());
        assertEquals("지남", items.get(2).getDdayText());
        assertEquals("PAST", items.get(2).getUrgency());
        assertEquals("A사 면접", service.findMostUrgent(items).getTitle());
    }

    @Test
    void 마감_임박_일정이_없으면_강조할_일정도_없다() throws Exception {
        service.addItem(userId, "먼 일정", TODAY.plusDays(DdayService.URGENT_DAYS + 1), "CUSTOM", TODAY);

        assertNull(service.findMostUrgent(service.listItems(userId, TODAY)));
    }

    @Test
    void 메뉴_배지는_지나지_않은_일정_중_가장_가까운_것을_가리킨다() throws Exception {
        assertNull(service.findNearest(userId, TODAY));

        service.addItem(userId, "먼 일정", TODAY.plusDays(40), "CUSTOM", TODAY);
        service.addItem(userId, "가까운 일정", TODAY.plusDays(9), "CUSTOM", TODAY);
        service.addItem(userId, "곧 지날 일정", TODAY, "CUSTOM", TODAY);

        assertEquals("D-DAY", service.findNearest(userId, TODAY).getDdayText());
        // 하루 지나면 지난 일정은 건너뛰고 그다음 일정을 가리킨다
        assertEquals("D-8", service.findNearest(userId, TODAY.plusDays(1)).getDdayText());
        // 전부 지나면 배지가 없다
        assertNull(service.findNearest(userId, TODAY.plusDays(41)));
    }

    @Test
    void 당일은_D_DAY로_표시한다() {
        DdayAlertDto alert = new DdayAlertDto();
        alert.setTargetDate(TODAY);
        alert.setAlertType("CERT");

        DdayItemDto item = DdayService.toItem(alert, TODAY);

        assertEquals("D-DAY", item.getDdayText());
        assertEquals("URGENT", item.getUrgency());
        assertEquals("자격증", item.getTypeLabel());
    }

    @Test
    void 잘못된_입력은_저장하지_않고_거부한다() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.addItem(userId, "  ", TODAY.plusDays(1), "CUSTOM", TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.addItem(userId, "가".repeat(101), TODAY.plusDays(1), "CUSTOM", TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.addItem(userId, "제목", null, "CUSTOM", TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.addItem(userId, "제목", TODAY.minusDays(1), "CUSTOM", TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> service.addItem(userId, "제목", TODAY.plusDays(1), "UNKNOWN", TODAY));

        assertTrue(service.listItems(userId, TODAY).isEmpty());
    }

    @Test
    void 직접_추가한_일정은_제목_날짜_종류를_수정할_수_있다() throws Exception {
        Long id = service.addItem(userId, "A사 서류 마감", TODAY.plusDays(5), "CUSTOM", TODAY);

        service.updateItem(userId, id, "  A사 서류 마감(연장)  ", TODAY.plusDays(12), "RECRUIT");

        DdayItemDto item = service.listItems(userId, TODAY).get(0);
        assertEquals("A사 서류 마감(연장)", item.getTitle());
        assertEquals(TODAY.plusDays(12), item.getTargetDate());
        assertEquals("RECRUIT", item.getAlertType());
        assertEquals("D-12", item.getDdayText());
    }

    @Test
    void 남의_일정과_잘못된_입력은_수정되지_않는다() throws Exception {
        Long id = service.addItem(userId, "내 일정", TODAY.plusDays(5), "CUSTOM", TODAY);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateItem(userId + 1_000_000L, id, "바꿈", TODAY.plusDays(9), "CUSTOM"));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateItem(userId, id, " ", TODAY.plusDays(9), "CUSTOM"));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateItem(userId, id, "바꿈", null, "CUSTOM"));

        List<DdayItemDto> items = service.listItems(userId, TODAY);
        assertEquals(1, items.size());
        for (DdayItemDto item : items) {
            assertFalse("바꿈".equals(item.getTitle()));
        }
    }

    @Test
    void 삭제는_본인_일정만_된다() throws Exception {
        Long id = service.addItem(userId, "지울 일정", TODAY.plusDays(5), "CUSTOM", TODAY);

        service.deleteItem(userId + 1_000_000L, id);
        assertEquals(1, service.listItems(userId, TODAY).size());

        service.deleteItem(userId, id);
        assertTrue(service.listItems(userId, TODAY).isEmpty());
    }
}
