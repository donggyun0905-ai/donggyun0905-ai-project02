package com.specodyssey.dao;

import com.specodyssey.dto.NotificationDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * NOTIFICATION 테이블 DAO. 항상 받는 사람(user_id) 본인 것만 읽고 고친다.
 * 같은 (user_id, noti_type, ref_key)는 UNIQUE라, 넣을 때 이미 있으면 조용히 건너뛴다(ON DUPLICATE KEY UPDATE id = id)
 * — 스케줄러가 재기동으로 두 번 돌거나 같은 이벤트가 다시 들어와도 알림이 겹치지 않는다.
 */
public class NotificationDao {

    public static final String TYPE_COMMENT = "COMMENT";
    public static final String TYPE_REPLY = "REPLY";
    public static final String TYPE_SHARE_VIEW = "SHARE_VIEW";
    public static final String TYPE_DDAY = "DDAY";
    public static final String TYPE_MISSION = "MISSION";

    private static final String COLUMNS =
            "id, user_id, noti_type, message, link_url, ref_key, is_read, created_at, updated_at, is_deleted";

    /** 알림 하나. 같은 ref_key가 이미 있으면 아무것도 하지 않는다. */
    public void insertIfAbsent(NotificationDto notification) throws SQLException {
        String sql = "INSERT INTO NOTIFICATION (user_id, noti_type, message, link_url, ref_key) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE id = id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, notification.getUserId());
            pstmt.setString(2, notification.getNotiType());
            pstmt.setString(3, notification.getMessage());
            pstmt.setString(4, notification.getLinkUrl());
            pstmt.setString(5, notification.getRefKey());
            pstmt.executeUpdate();
        }
    }

    /** D-day 알림 종류 — 하루 전과 당일은 같은 일정에서 나오므로 ref_key 접두어로 구분한다 */
    public enum DdayTiming {
        DAY_BEFORE("dday:", "내일은 「", "」 D-day입니다."),
        TODAY("dday-today:", "오늘은 「", "」 D-day입니다!");

        final String refPrefix;
        final String messageHead;
        final String messageTail;

        DdayTiming(String refPrefix, String messageHead, String messageTail) {
            this.refPrefix = refPrefix;
            this.messageHead = messageHead;
            this.messageTail = messageTail;
        }
    }

    /**
     * D-day 알림 — 목표일이 targetDate인 D-day 일정 주인에게 한 번에 넣는다 (하루 전이면 내일, 당일이면 오늘 날짜).
     * 새로 넣은 건수를 돌려준다 (이미 있어 건너뛴 행은 값이 바뀌지 않아 0으로 센다).
     */
    public int insertDdayReminders(LocalDate targetDate, DdayTiming timing) throws SQLException {
        String sql = "INSERT INTO NOTIFICATION (user_id, noti_type, message, link_url, ref_key) " +
                "SELECT d.user_id, '" + TYPE_DDAY + "', LEFT(CONCAT(?, d.title, ?), 200), '/dday', " +
                "CONCAT(?, d.id, ':', d.target_date) " +
                "FROM DDAY_ALERT d JOIN USERS u ON u.id = d.user_id AND u.is_deleted = FALSE " +
                "WHERE d.target_date = ? AND d.is_deleted = FALSE " +
                "ON DUPLICATE KEY UPDATE id = NOTIFICATION.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, timing.messageHead);
            pstmt.setString(2, timing.messageTail);
            pstmt.setString(3, timing.refPrefix);
            pstmt.setDate(4, Date.valueOf(targetDate));
            return pstmt.executeUpdate();
        }
    }

    /**
     * 오늘의 미션 마감 1시간 전 알림 — 지원자 중 오늘 미션을 다 풀지 않은 사람에게 넣는다.
     * 미션은 접속할 때 배정되므로, 오늘 배정된 미션이 없는 사람(아직 안 들어온 사람)도 "안 푼 사람"으로 본다.
     */
    public int insertMissionReminders(LocalDate today) throws SQLException {
        String sql = "INSERT INTO NOTIFICATION (user_id, noti_type, message, link_url, ref_key) " +
                "SELECT u.id, '" + TYPE_MISSION + "', '오늘의 미션이 1시간 뒤 마감됩니다. 아직 풀지 않은 미션이 있어요.', '/mission', ? " +
                "FROM USERS u " +
                "WHERE u.is_deleted = FALSE AND u.user_type = 'APPLICANT' " +
                "AND (NOT EXISTS (SELECT 1 FROM USER_DAILY_MISSION m " +
                "                 WHERE m.user_id = u.id AND m.assigned_date = ? AND m.is_deleted = FALSE) " +
                "     OR EXISTS (SELECT 1 FROM USER_DAILY_MISSION m " +
                "                WHERE m.user_id = u.id AND m.assigned_date = ? AND m.is_deleted = FALSE AND m.is_completed = FALSE)) " +
                "ON DUPLICATE KEY UPDATE id = NOTIFICATION.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "mission:" + today);
            pstmt.setDate(2, Date.valueOf(today));
            pstmt.setDate(3, Date.valueOf(today));
            return pstmt.executeUpdate();
        }
    }

    public int countUnread(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM NOTIFICATION WHERE user_id = ? AND is_read = FALSE AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** 최근 알림부터 limit개 (읽은 것 포함) */
    public List<NotificationDto> findRecent(Long userId, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM NOTIFICATION WHERE user_id = ? AND is_deleted = FALSE " +
                "ORDER BY created_at DESC, id DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<NotificationDto> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    /** 안 읽은 알림만 최근 것부터 limit개 — 헤더 드롭다운용 (읽으면 드롭다운에서 빠진다) */
    public List<NotificationDto> findRecentUnread(Long userId, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM NOTIFICATION WHERE user_id = ? AND is_read = FALSE AND is_deleted = FALSE " +
                "ORDER BY created_at DESC, id DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<NotificationDto> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    /** 본인 알림 하나. 없거나 남의 것이면 null. */
    public NotificationDto findByIdAndUserId(Long id, Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM NOTIFICATION WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public void markRead(Long id, Long userId) throws SQLException {
        String sql = "UPDATE NOTIFICATION SET is_read = TRUE WHERE id = ? AND user_id = ? AND is_read = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    public void markAllRead(Long userId) throws SQLException {
        String sql = "UPDATE NOTIFICATION SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        }
    }

    private NotificationDto mapRow(ResultSet rs) throws SQLException {
        NotificationDto n = new NotificationDto();
        n.setId(rs.getLong("id"));
        n.setUserId(rs.getLong("user_id"));
        n.setNotiType(rs.getString("noti_type"));
        n.setMessage(rs.getString("message"));
        n.setLinkUrl(rs.getString("link_url"));
        n.setRefKey(rs.getString("ref_key"));
        n.setRead(rs.getBoolean("is_read"));
        Timestamp created = rs.getTimestamp("created_at");
        n.setCreatedAt(created == null ? null : created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        n.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        n.setDeleted(rs.getBoolean("is_deleted"));
        return n;
    }
}
