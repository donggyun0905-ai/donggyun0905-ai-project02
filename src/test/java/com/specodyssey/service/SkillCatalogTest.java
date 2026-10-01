package com.specodyssey.service;

import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SkillCatalog DB 통합테스트 — 데이터가 그대로면 메모리 것을 재사용하고, 바뀌면 바로 다시 읽는지 본다. */
class SkillCatalogTest {

    private final SkillDao skillDao = new SkillDao();

    @Test
    void 데이터가_그대로면_같은_스냅샷을_재사용한다() throws Exception {
        SkillCatalog.Snapshot first = SkillCatalog.current();
        SkillCatalog.Snapshot second = SkillCatalog.current();

        assertSame(first, second);
    }

    @Test
    void 스킬이_추가되면_다시_읽고_삭제되면_다시_빠진다() throws Exception {
        SkillCatalog.Snapshot before = SkillCatalog.current();
        String name = "카탈로그테스트_" + System.nanoTime();
        long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = TestFixtures.insertSkill(conn, name);
        }
        try {
            SkillCatalog.Snapshot after = SkillCatalog.current();
            assertNotSame(before, after);
            assertTrue(after.skills().stream().anyMatch(s -> s.getId().equals(id)));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SKILL", id);
            }
        }
        assertFalse(SkillCatalog.current().skills().stream().anyMatch(s -> s.getId().equals(id)));
    }

    @Test
    void 임베딩이_저장되면_벡터_목록에_float_배열로_들어간다() throws Exception {
        String name = "카탈로그벡터_" + System.nanoTime();
        long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = TestFixtures.insertSkill(conn, name);
            skillDao.updateEmbedding(conn, id, "[0.5,0.25,1.0]", "test-model", LocalDateTime.now());
        }
        try {
            SkillCatalog.SkillVector vector = SkillCatalog.current().vectors().stream()
                    .filter(v -> v.skillId().equals(id)).findFirst().orElseThrow();
            assertEquals(3, vector.vector().length);
            assertEquals(0.25f, vector.vector()[1]);
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SKILL", id);
            }
        }
    }

    @Test
    void refresh하면_다음_호출에서_새로_읽는다() throws Exception {
        SkillCatalog.Snapshot first = SkillCatalog.current();
        SkillCatalog.refresh();

        assertNotSame(first, SkillCatalog.current());
    }
}
