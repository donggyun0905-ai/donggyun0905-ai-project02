package com.specodyssey.service;

import com.specodyssey.dao.NotificationDao;
import com.specodyssey.service.NotificationService.Target;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NotificationService 규칙 단위테스트 — 댓글 알림을 누구에게 보낼지, 알림 링크가 앱 안 경로인지. DB 없음.
 */
class NotificationServiceTest {

    private static final Long OWNER = 1L;
    private static final Long COMMENTER = 2L;
    private static final Long OTHER = 3L;

    @Test
    void commentTargets_남의_글에_댓글이면_글쓴이에게만_댓글_알림() {
        assertEquals(List.of(new Target(OWNER, NotificationDao.TYPE_COMMENT)),
                NotificationService.commentTargets(OWNER, COMMENTER, null));
    }

    @Test
    void commentTargets_내_글에_내가_댓글이면_알림_없음() {
        assertEquals(List.of(), NotificationService.commentTargets(OWNER, OWNER, null));
    }

    @Test
    void commentTargets_다른_사람_댓글에_답글이면_답글_대상과_글쓴이_둘_다() {
        assertEquals(List.of(new Target(OTHER, NotificationDao.TYPE_REPLY), new Target(OWNER, NotificationDao.TYPE_COMMENT)),
                NotificationService.commentTargets(OWNER, COMMENTER, OTHER));
    }

    @Test
    void commentTargets_글쓴이_댓글에_답글이면_글쓴이에게_답글_알림_하나만() {
        assertEquals(List.of(new Target(OWNER, NotificationDao.TYPE_REPLY)),
                NotificationService.commentTargets(OWNER, COMMENTER, OWNER));
    }

    @Test
    void commentTargets_내_댓글에_내가_답글이면_글쓴이에게만() {
        assertEquals(List.of(new Target(OWNER, NotificationDao.TYPE_COMMENT)),
                NotificationService.commentTargets(OWNER, COMMENTER, COMMENTER));
    }

    @Test
    void commentTargets_글쓴이가_남의_댓글에_답글이면_그_댓글_작성자에게만() {
        assertEquals(List.of(new Target(OTHER, NotificationDao.TYPE_REPLY)),
                NotificationService.commentTargets(OWNER, OWNER, OTHER));
    }

    @Test
    void safeLink_앱_안_경로만_허용한다() {
        assertEquals("/spec-archive/post?id=3#comments", NotificationService.safeLink("/spec-archive/post?id=3#comments"));
        assertEquals(NotificationService.FALLBACK_LINK, NotificationService.safeLink(null));
        assertEquals(NotificationService.FALLBACK_LINK, NotificationService.safeLink("https://evil.example"));
        assertEquals(NotificationService.FALLBACK_LINK, NotificationService.safeLink("//evil.example"));
        assertEquals(NotificationService.FALLBACK_LINK, NotificationService.safeLink("/\\evil.example"));
    }
}
