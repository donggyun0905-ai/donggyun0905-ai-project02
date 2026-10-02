package com.specodyssey.dao;

import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * SCORING_RULE 테이블 DAO — 점수·복습 주기 규칙(rule_key → rule_value).
 * 값은 운영자가 DB에서 바꾸는 설정이라 읽기 + 값 갱신(upsert)만 지원한다.
 */
public class ScoringRuleDao {

    public Map<String, Integer> findAll() throws SQLException {
        String sql = "SELECT rule_key, rule_value FROM SCORING_RULE WHERE is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            Map<String, Integer> rules = new HashMap<>();
            while (rs.next()) {
                rules.put(rs.getString("rule_key"), rs.getInt("rule_value"));
            }
            return rules;
        }
    }

    public void upsert(String key, int value) throws SQLException {
        String sql = "INSERT INTO SCORING_RULE (rule_key, rule_value) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value), is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setInt(2, value);
            pstmt.executeUpdate();
        }
    }
}
