package com.specodyssey.dao;

import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 데스크톱 캐릭터가 말할 거리가 바뀌었는지 한 번에 알아보는 값 (2026-10-08).
 * 캐릭터는 10초마다 묻는데, 할 말을 새로 만들려면 알림·미션·점수·로드맵·D-day를 열 번 넘게 읽어야 한다.
 * 이 값(테이블별 행 수 + 마지막 수정 시각)이 그대로면 직전에 만든 결과를 다시 쓴다 — CompanionSnapshotCache.
 * 행 수를 같이 보는 이유: 지워진 행은 마지막 수정 시각을 바꾸지 않기 때문.
 */
public class CompanionFingerprintDao {

    private static final String SQL = "SELECT CONCAT_WS('|', "
            + "(SELECT CONCAT(COUNT(*), ':', IFNULL(MAX(updated_at), '')) FROM NOTIFICATION WHERE user_id = ?), "
            + "(SELECT CONCAT(COUNT(*), ':', IFNULL(MAX(updated_at), '')) FROM USER_DAILY_MISSION WHERE user_id = ?), "
            + "(SELECT IFNULL(MAX(updated_at), '') FROM USER_SCORE_SUMMARY WHERE user_id = ?), "
            + "(SELECT CONCAT(COUNT(*), ':', IFNULL(MAX(updated_at), '')) FROM DDAY_ALERT WHERE user_id = ?), "
            + "(SELECT CONCAT(COUNT(*), ':', IFNULL(MAX(updated_at), '')) FROM ROADMAP WHERE user_id = ?), "
            + "(SELECT CONCAT(COUNT(*), ':', IFNULL(MAX(s.updated_at), '')) FROM ROADMAP_STEP s "
            + "   JOIN ROADMAP r ON r.id = s.roadmap_id WHERE r.user_id = ?), "
            + "(SELECT IFNULL(MAX(updated_at), '') FROM USERS WHERE id = ?))";

    public String fingerprint(Long userId) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SQL)) {
            for (int i = 1; i <= 7; i++) {
                pstmt.setLong(i, userId);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }
}
