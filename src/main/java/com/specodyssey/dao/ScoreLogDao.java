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
