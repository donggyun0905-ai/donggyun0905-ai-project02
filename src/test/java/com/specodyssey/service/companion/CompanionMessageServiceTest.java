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
        assertTrue(CompanionMessageService.missionMessages(DAY, 21, 20, 5, true, new int[]{3, 3}).isEmpty());
    }

    @Test
    void 저녁에_연속_기록이_걸려_있으면_경고_하나만() {
        List<Message> list = CompanionMessageService.missionMessages(DAY, 20, 20, 12, false, new int[]{3, 1});
        assertEquals(1, list.size());
        assertEquals(CompanionMessageService.WARN, list.get(0).kind());
        assertTrue(list.get(0).text().contains("12일째"));
        assertEquals("/mission", list.get(0).path());
    }

    @Test
    void 낮에는_남은_개수를_할_일로_말하고_하나_풀면_key가_바뀐다() {
        Message two = CompanionMessageService.missionMessages(DAY, 14, 20, 3, false, new int[]{3, 1}).get(0);
        Message one = CompanionMessageService.missionMessages(DAY, 14, 20, 3, false, new int[]{3, 2}).get(0);
        assertEquals(CompanionMessageService.TODO, two.kind());
        assertTrue(two.text().contains("2개 남았어요"));
        assertNotEquals(two.key(), one.key(), "한 문제를 풀면 key가 달라져 캐릭터가 말풍선을 스스로 끈다");
    }

    @Test
    void 아직_미션을_안_받았으면_받으러_가자고_한다() {
        Message m = CompanionMessageService.missionMessages(DAY, 9, 20, 0, false, null).get(0);
        assertEquals("mission-new:" + DAY, m.key());
    }

    @Test
    void 연속_기록이_없으면_저녁이어도_경고가_아니라_할_일() {
        Message m = CompanionMessageService.missionMessages(DAY, 22, 20, 0, false, new int[]{3, 0}).get(0);
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

    @Test
    void 연속_7일_30일을_채운_날만_칭찬한다() {
        assertEquals(CompanionMessageService.PRAISE, CompanionMessageService.streakPraise(DAY, 7, true).kind());
        assertTrue(CompanionMessageService.streakPraise(DAY, 30, true).text().contains("30일"));
        assertEquals(null, CompanionMessageService.streakPraise(DAY, 8, true));
        assertEquals(null, CompanionMessageService.streakPraise(DAY, 7, false), "오늘 끝내야 칭찬");
    }

    @Test
    void 로드맵_티어를_최근에_다_끝냈으면_칭찬하고_오래됐으면_안_한다() {
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 10, 7, 12, 0);
        java.util.List<com.specodyssey.dto.RoadmapStepDto> steps = java.util.List.of(
                step("ENTRY", true, now.minusDays(1)), step("ENTRY", true, now.minusDays(2)), step("CORE", false, null));
        List<Message> praise = CompanionMessageService.tierPraises(9L, steps, now);
        assertEquals(1, praise.size());
        assertEquals("tier-done:9:ENTRY", praise.get(0).key());
        assertTrue(CompanionMessageService.tierPraises(9L, java.util.List.of(step("ENTRY", true, now.minusDays(10))), now).isEmpty());
    }

    @Test
    void 하루_요약_한_줄() {
        assertEquals("오늘 할 일 — 미션 2개 남음", CompanionMessageService.summary(new int[]{3, 1}, null));
        assertEquals("오늘 할 일 — 오늘의 미션 3문제", CompanionMessageService.summary(null, null));
    }

    private static com.specodyssey.dto.RoadmapStepDto step(String tier, boolean done, java.time.LocalDateTime at) {
        com.specodyssey.dto.RoadmapStepDto s = new com.specodyssey.dto.RoadmapStepDto();
        s.setTier(tier);
        s.setStepType("SKILL");
        s.setCompleted(done);
        s.setCompletedAt(at);
        return s;
    }
}
