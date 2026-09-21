package com.specodyssey.dao;

import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

class LevelTierDaoTest {

    private final LevelTierDao dao = new LevelTierDao();

    @Test
    void insert_findAll() throws Exception {
        LevelTierDto tier = new LevelTierDto();
        // 시드 등급 구간(0~499, 500~1499...)과 겹치지 않는 테스트 전용 구간
        tier.setMinScore(1_000_000);
        tier.setMaxScore(1_000_999);
        tier.setTierName("테스트등급");
        tier.setTitleName("테스트 항해사");
        tier.setProblemLevelMin(1);
        tier.setProblemLevelMax(1);

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, tier);
        }
        try {
            assertNotNull(id);
            assertTrue(dao.findAll().stream().anyMatch(t -> t.getId().equals(id)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "LEVEL_TIER", id);
            }
        }
    }
}
