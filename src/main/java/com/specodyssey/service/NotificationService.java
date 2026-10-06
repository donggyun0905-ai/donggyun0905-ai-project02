package com.specodyssey.service;

import com.specodyssey.dao.NotificationDao;
import com.specodyssey.dto.NotificationDto;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 알림 — 헤더의 알림 버튼(빨간 표시·안 읽은 개수)과 알림 목록.
 *
 * 만드는 곳:
 *   댓글·답글     SpecArchiveService.addComment가 댓글을 저장한 뒤 notifyComment 호출
 *   면접관 열람   ShareViewService가 열람 기록을 남긴 뒤 notifyShareView 호출 (열 때마다)
 *   D-day·미션    NotificationScheduler가 매일 09:00(D-day 하루 전·당일)·23:00(미션 마감 1시간 전)에 호출
 * 알림은 부가 기능이라, 만들다 실패해도 댓글 작성·링크 열람 같은 본래 동작은 그대로 진행한다(예외를 삼키고 로그만 남김).
 */
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());
    private static final int TITLE_PREVIEW_LENGTH = 30;
    public static final String FALLBACK_LINK = "/notifications";

    private final NotificationDao notificationDao = new NotificationDao();

    /** 댓글 하나가 만드는 알림 한 건 — 누구에게, 어떤 종류로 */
    record Target(Long userId, String type) {
    }

    /**
     * 댓글 하나로 누구에게 알릴지 정한다.
     * - 답글이면 답글 대상 댓글의 작성자에게 REPLY
     * - 글쓴이에게 COMMENT (답글 대상이 글쓴이 본인이면 REPLY 하나만 — 같은 사람에게 두 번 알리지 않는다)
     * - 자기 글·자기 댓글에 직접 단 것은 알리지 않는다
     */
    static List<Target> commentTargets(Long articleOwnerId, Long commenterId, Long replyToUserId) {
        List<Target> targets = new ArrayList<>();
        if (replyToUserId != null && !replyToUserId.equals(commenterId)) {
            targets.add(new Target(replyToUserId, NotificationDao.TYPE_REPLY));
        }
        boolean ownerAlreadyNotified = articleOwnerId != null && articleOwnerId.equals(replyToUserId)
                && !targets.isEmpty();
        if (articleOwnerId != null && !articleOwnerId.equals(commenterId) && !ownerAlreadyNotified) {
            targets.add(new Target(articleOwnerId, NotificationDao.TYPE_COMMENT));
        }
        return targets;
    }

    /** 스펙 아카이브 댓글·답글 알림 */
    public void notifyComment(Long articleId, Long articleOwnerId, String articleTitle,
                              Long commenterId, Long commentId, Long replyToUserId) {
        String title = preview(articleTitle);
        for (Target target : commentTargets(articleOwnerId, commenterId, replyToUserId)) {
            String message = NotificationDao.TYPE_REPLY.equals(target.type())
                    ? "내 댓글에 새 답글이 달렸습니다 — 「" + title + "」"
                    : "내 글 「" + title + "」에 새 댓글이 달렸습니다.";
            save(target.userId(), target.type(), message,
                    "/spec-archive/post?id=" + articleId + "#comments", "comment:" + commentId);
        }
    }

    /** 면접관이 공유 링크를 열람했을 때 — 열 때마다 하나씩 (열람 기록 id로 구분) */
    public void notifyShareView(Long ownerUserId, String linkLabel, Long viewLogId) {
        String label = linkLabel == null || linkLabel.isBlank() ? "" : " 「" + preview(linkLabel) + "」";
        save(ownerUserId, NotificationDao.TYPE_SHARE_VIEW, "면접관이 내 공유 링크" + label + "를 열람했습니다.",
                "/share-links", "view:" + viewLogId);
    }

    /** D-day 알림 (스케줄러가 매일 09:00에 호출) — 내일이 목표일이면 하루 전 알림, 오늘이 목표일이면 당일 알림. 새로 만든 건수. */
    public int createDdayReminders(LocalDate today) throws SQLException {
        return notificationDao.insertDdayReminders(today.plusDays(1), NotificationDao.DdayTiming.DAY_BEFORE)
                + notificationDao.insertDdayReminders(today, NotificationDao.DdayTiming.TODAY);
    }

    /** 오늘의 미션을 다 안 푼 사람에게 마감 1시간 전 알림 (스케줄러가 23:00에 호출). 새로 만든 건수. */
    public int createMissionReminders(LocalDate today) throws SQLException {
        return notificationDao.insertMissionReminders(today);
    }

    public int countUnread(Long userId) throws SQLException {
        return notificationDao.countUnread(userId);
    }

    /** 전체 알림 화면용 — 읽은 것까지 최근 순 */
    public List<NotificationDto> findRecent(Long userId, int limit) throws SQLException {
        return notificationDao.findRecent(userId, limit);
    }

    /** 헤더 드롭다운용 — 안 읽은 것만 최근 순. 눌러서 읽으면 다음 화면부터 드롭다운에서 빠진다. */
    public List<NotificationDto> findRecentUnread(Long userId, int limit) throws SQLException {
        return notificationDao.findRecentUnread(userId, limit);
    }

    /** 알림을 읽음으로 바꾸고 이동할 앱 안 경로를 돌려준다. 남의 알림이거나 없으면 알림 목록으로. */
    public String open(Long notificationId, Long userId) throws SQLException {
        NotificationDto n = notificationDao.findByIdAndUserId(notificationId, userId);
        if (n == null) {
            return FALLBACK_LINK;
        }
        notificationDao.markRead(notificationId, userId);
        return safeLink(n.getLinkUrl());
    }

    public void markAllRead(Long userId) throws SQLException {
        notificationDao.markAllRead(userId);
    }

    /** 앱 안 경로만 허용한다 — '/'로 시작하고 '//'(다른 사이트)나 '\'가 아닌 것. 아니면 알림 목록. */
    public static String safeLink(String link) {
        if (link == null || !link.startsWith("/") || link.startsWith("//") || link.contains("\\")) {
            return FALLBACK_LINK;
        }
        return link;
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() > TITLE_PREVIEW_LENGTH ? t.substring(0, TITLE_PREVIEW_LENGTH) + "…" : t;
    }

    private void save(Long userId, String type, String message, String link, String refKey) {
        NotificationDto n = new NotificationDto();
        n.setUserId(userId);
        n.setNotiType(type);
        n.setMessage(message);
        n.setLinkUrl(link);
        n.setRefKey(refKey);
        try {
            notificationDao.insertIfAbsent(n);
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "알림 저장 실패 — 본래 동작은 그대로 진행합니다 (" + type + ", " + refKey + ")", e);
        }
    }
}
