package com.specodyssey.service;

import com.specodyssey.service.SkillMatcher.MatchResult;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExactMatcher DB 통합테스트.
 * dao.TestFixtures는 package-private이라 여기서 쓸 수 없어서 같은 방식의 픽스처를 직접 둔다.
 */
class ExactMatcherTest {

    private final SkillMatcher matcher = new ExactMatcher();

    private String skillName;
    private long skillId;

    @BeforeEach
    void setUp() throws SQLException {
        skillName = "MatcherSkill_" + System.nanoTime();
        try (Connection conn = DBUtil.getConnection()) {
            skillId = insertSkill(conn, skillName);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p = conn.prepareStatement("DELETE FROM SKILL WHERE id = ?")) {
            p.setLong(1, skillId);
            p.executeUpdate();
        }
    }

    @Test
    void exactName_returnsScoreOne() throws SQLException {
        assertMatched(matcher.match(skillName));
    }

    @Test
    void differentCase_matches() throws SQLException {
        assertMatched(matcher.match(skillName.toUpperCase()));
        assertMatched(matcher.match(skillName.toLowerCase()));
    }

    @Test
    void surroundingWhitespace_matches() throws SQLException {
        assertMatched(matcher.match("  " + skillName + "  "));
        assertMatched(matcher.match("\t" + skillName + "\n"));
    }

    @Test
    void unknownSkill_returnsNull() throws SQLException {
        assertNull(matcher.match(skillName + "_없음"));
    }

    @Test
    void nullOrBlank_returnsNull() throws SQLException {
        assertNull(matcher.match(null));
        assertNull(matcher.match(""));
        assertNull(matcher.match("   "));
    }

    private void assertMatched(MatchResult result) {
        assertNotNull(result);
        assertEquals(skillId, result.skillId());
        assertEquals(1.0, result.score());
    }

    private static long insertSkill(Connection conn, String name) throws SQLException {
        String sql = "INSERT INTO SKILL (skill_name, category) VALUES (?, 'TEST')";
        try (PreparedStatement p = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, name);
            p.executeUpdate();
            try (ResultSet rs = p.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
