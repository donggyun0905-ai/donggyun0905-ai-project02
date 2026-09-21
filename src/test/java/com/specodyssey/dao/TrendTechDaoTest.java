package com.specodyssey.dao;

import com.specodyssey.dto.TrendTechDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TrendTechDaoTest {

    private final TrendTechDao dao = new TrendTechDao();

    @Test
    void insert_findAll() throws Exception {
        TrendTechDto trend = new TrendTechDto();
        trend.setTechName("Rust");
        trend.setSummary("시스템 프로그래밍 언어 채용 언급 증가");
        trend.setSourceUrl("https://example.com/rust");
        trend.setPublishedAt(LocalDateTime.now());

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, trend);
        }
        try {
            assertNotNull(id);
            assertTrue(dao.findAll().stream().anyMatch(t -> t.getId().equals(id)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "TREND_TECH", id);
            }
        }
    }
}
