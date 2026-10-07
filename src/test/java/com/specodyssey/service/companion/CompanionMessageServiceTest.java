package com.specodyssey.service.companion;

import com.specodyssey.service.companion.CompanionMessageService.Message;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 캐릭터가 말할 것 — 미션·연속 기록 문장, key, 우선순위 */
class CompanionMessageServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    @Test
    void 오늘_미션을_다_했으면_미션_얘기는_안_한다() {
        assertTrue(CompanionMessageService.missionMessages(DAY, 21, 5, true, new int[]{3, 3}).isEmpty());
    }

    @Test
    void 저녁에_연속_기록이_걸려_있으면_경고_하나만() {
        List<Message> list = CompanionMessageService.missionMessages(DAY, 20, 12, false, new int[]{3, 1});
        assertEquals(1, list.size());
        assertEquals(CompanionMessageService.WARN, list.get(0).kind());
        assertTrue(list.get(0).text().contains("12일째"));
        assertEquals("/mission", list.get(0).path());
    }

    @Test
    void 낮에는_남은_개수를_할_일로_말하고_하나_풀면_key가_바뀐다() {
        Message two = CompanionMessageService.missionMessages(DAY, 14, 3, false, new int[]{3, 1}).get(0);
        Message one = CompanionMessageService.missionMessages(DAY, 14, 3, false, new int[]{3, 2}).get(0);
        assertEquals(CompanionMessageService.TODO, two.kind());
        assertTrue(two.text().contains("2개 남았어요"));
        assertNotEquals(two.key(), one.key(), "한 문제를 풀면 key가 달라져 캐릭터가 말풍선을 스스로 끈다");
    }

    @Test
    void 아직_미션을_안_받았으면_받으러_가자고_한다() {
        Message m = CompanionMessageService.missionMessages(DAY, 9, 0, false, null).get(0);
        assertEquals("mission-new:" + DAY, m.key());
    }

    @Test
    void 연속_기록이_없으면_저녁이어도_경고가_아니라_할_일() {
        Message m = CompanionMessageService.missionMessages(DAY, 22, 0, false, new int[]{3, 0}).get(0);
        assertEquals(CompanionMessageService.TODO, m.kind());
    }

    @Test
    void 경고_알림_할_일_순으로_말한다() {
        List<Message> ordered = CompanionMessageService.ordered(List.of(
                new Message("a", CompanionMessageService.TODO, "", "", "", "/", null),
                new Message("b", CompanionMessageService.NOTICE, "", "", "", "/", 1L),
                new Message("c", CompanionMessageService.WARN, "", "", "", "/", null),
                new Message("d", CompanionMessageService.TODO, "", "", "", "/", null)));
        assertEquals(List.of("c", "b", "a", "d"), ordered.stream().map(Message::key).toList());
    }

    @Test
    void 알림_종류를_말풍선_제목으로() {
        assertEquals("새 댓글", CompanionMessageService.noticeLabel("COMMENT"));
        assertEquals("면접관 열람", CompanionMessageService.noticeLabel("SHARE_VIEW"));
        assertEquals("알림", CompanionMessageService.noticeLabel("NEW_TYPE"));
    }
}
