package com.specodyssey.dao;

import com.specodyssey.dto.ScoreLogDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCORE_LOG 테이블 DAO.
 * 관련 요구사항: TD-5 스코어링
 * 점수 적립 이력은 감사로그 성격이라 append-only — 수정·삭제는 없다.
 * 복합 UNIQUE(user_id, signal_type, ref_id) 위반(중복 적립 시도)은 호출하는 서비스가 판단한다.
 */
public class ScoreLogDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, user_id, signal_type, ref_id, points, earned_at, created_at, updated_at, " +
            "is_deleted";

    public Long insert(ScoreLogDto log) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, log);
        }
    }

    public Long insert(Connection conn, ScoreLogDto log) throws SQLException {
        String sql = "INSERT INTO SCORE_LOG (user_id, signal_type, ref_id, points, earned_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, log.getUserId());
            pstmt.setString(2, log.getSignalType());
            pstmt.setLong(3, log.getRefId());
            pstmt.setInt(4, log.getPoints());
            pstmt.setTimestamp(5, toTimestamp(log.getEarnedAt()));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    // 미션 완료 취소(2026-10-12)로 논리 삭제된 같은 이벤트 기록이 있으면 되살린다 — (user_id, signal_type, ref_id) UNIQUE가
    // 삭제된 행에도 걸려 있어 새로 INSERT하면 중복 오류가 난다. 되살렸으면 true, 삭제된 행이 없으면 false.
    // 삭제된 행이 없을 때 UPDATE를 바로 날리면 UNIQUE 인덱스에 간격 잠금이 걸려 동시 적립의 INSERT와 교착이 난다 —
    // 잠금 없는 조회로 먼저 확인하고, 있을 때만 그 행(id)을 고친다.
    public boolean reviveDeleted(Connection conn, ScoreLogDto log) throws SQLException {
        String findSql = "SELECT id FROM SCORE_LOG WHERE user_id = ? AND signal_type = ? AND ref_id = ? AND is_deleted = TRUE";
        Long deletedId = null;
        try (PreparedStatement pstmt = conn.prepareStatement(findSql)) {
            pstmt.setLong(1, log.getUserId());
            pstmt.setString(2, log.getSignalType());
            pstmt.setLong(3, log.getRefId());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    deletedId = rs.getLong(1);
                }
            }
        }
        if (deletedId == null) {
            return false;
        }
        String sql = "UPDATE SCORE_LOG SET is_deleted = FALSE, points = ?, earned_at = ? WHERE id = ? AND is_deleted = TRUE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, log.getPoints());
            pstmt.setTimestamp(2, toTimestamp(log.getEarnedAt()));
            pstmt.setLong(3, deletedId);
            return pstmt.executeUpdate() == 1;
        }
    }

    // 같은 이벤트(user_id, signal_type, ref_id)로 이미 적립했는지 확인 — 완료 체크를 껐다 켜도
    // 중복 적립되지 않게 ScoreService가 insert 전에 이걸로 먼저 가드한다.
    public boolean existsByUserSignalRef(Connection conn, Long userId, String signalType, Long refId)
            throws SQLException {
        String sql = "SELECT 1 FROM SCORE_LOG WHERE user_id = ? AND signal_type = ? AND ref_id = ? " +
                "AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, signalType);
            pstmt.setLong(3, refId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 미션 완료 취소(2026-10-12) — 그 적립 기록을 논리 삭제하고 빠진 점수를 돌려준다(없으면 0).
    // 논리 삭제하면 existsByUserSignalRef가 못 찾으므로, 다시 풀면 한 번 더 적립된다.
    public int softDeleteByUserSignalRef(Connection conn, Long userId, String signalType, Long refId)
            throws SQLException {
        String sumSql = "SELECT COALESCE(SUM(points), 0) FROM SCORE_LOG WHERE user_id = ? AND signal_type = ? " +
                "AND ref_id = ? AND is_deleted = FALSE";
        int points;
        try (PreparedStatement pstmt = conn.prepareStatement(sumSql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, signalType);
            pstmt.setLong(3, refId);
            try (ResultSet rs = pstmt.executeQuery()) {
                points = rs.next() ? rs.getInt(1) : 0;
            }
        }
        String sql = "UPDATE SCORE_LOG SET is_deleted = TRUE WHERE user_id = ? AND signal_type = ? AND ref_id = ? " +
                "AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, signalType);
            pstmt.setLong(3, refId);
            pstmt.executeUpdate();
        }
        return points;
    }

    public List<ScoreLogDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SCORE_LOG WHERE user_id = ? AND is_deleted = FALSE ORDER BY earned_at";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ScoreLogDto> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
                return logs;
            }
        }
    }

    private ScoreLogDto mapRow(ResultSet rs) throws SQLException {
        ScoreLogDto log = new ScoreLogDto();
        log.setId(rs.getLong("id"));
        log.setUserId(rs.getLong("user_id"));
        log.setSignalType(rs.getString("signal_type"));
        log.setRefId(rs.getLong("ref_id"));
        log.setPoints(rs.getObject("points", Integer.class));
        log.setEarnedAt(toLocalDateTime(rs.getTimestamp("earned_at")));
        log.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        log.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        log.setDeleted(rs.getBoolean("is_deleted"));
        return log;
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
