package com.specodyssey.dao;

import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

class SkillDaoTest {

    private final SkillDao skillDao = new SkillDao();

    @Test
    void findAll_and_findByName() throws Exception {
        String skillName = "TestSkill_" + System.nanoTime();
        long skillId;
        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, skillName);
        }
        try {
            SkillDto found = skillDao.findByName(skillName);
            assertNotNull(found);
            assertEquals(skillName, found.getSkillName());

            assertTrue(skillDao.findAll().stream().anyMatch(s -> s.getId().equals(skillId)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SKILL", skillId);
            }
        }
    }
}
