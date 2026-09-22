package com.specodyssey.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DAO 통합테스트 공용 픽스처 헬퍼.
 * SKILL처럼 1주차 범위상 DAO에 insert가 없는 마스터 테이블은 여기서 직접 raw SQL로 만든다.
 * 테이블명은 전부 코드에 고정된 상수이므로(사용자 입력 아님) 문자열 결합을 써도 SQL 인젝션 위험이 없다.
 * com.specodyssey.service 쪽 테스트(RoadmapServiceTest 등)에서도 재사용하기 위해 public으로 둔다.
 */
public final class TestFixtures {
    private TestFixtures() {
    }

    public static long insertSkill(Connection conn, String skillName) throws SQLException {
        String sql = "INSERT INTO SKILL (skill_name, category) VALUES (?, 'TEST')";
        try (PreparedStatement p = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, skillName);
            p.executeUpdate();
            try (ResultSet rs = p.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** FK가 걸린 자식 행부터 지워야 하므로 호출 순서(자식→부모)는 호출부 책임. */
    public static void hardDelete(Connection conn, String table, long id) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
            p.setLong(1, id);
            p.executeUpdate();
        }
    }

    public static void hardDeleteByColumn(Connection conn, String table, String column, long value) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("DELETE FROM " + table + " WHERE " + column + " = ?")) {
            p.setLong(1, value);
            p.executeUpdate();
        }
    }
}
