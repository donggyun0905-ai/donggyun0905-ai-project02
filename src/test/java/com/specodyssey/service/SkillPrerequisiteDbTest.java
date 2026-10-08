package com.specodyssey.service;

import com.specodyssey.dao.SkillPrerequisiteDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dto.SkillPrerequisiteDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 기술 선수관계 — DB를 실제로 왕복하는 부분 (2026-10-08). */
class SkillPrerequisiteDbTest {

    private static final SkillPrerequisiteDao dao = new SkillPrerequisiteDao();
    private final SkillPrerequisiteService service = new SkillPrerequisiteService();

    private static long first;
    private static long second;
    private static long third;

    @BeforeAll
    static void setUp() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            long stamp = System.nanoTime();
            first = TestFixtures.insertSkill(conn, "선수테스트A_" + stamp);
            second = TestFixtures.insertSkill(conn, "선수테스트B_" + stamp);
            third = TestFixtures.insertSkill(conn, "선수테스트C_" + stamp);
        }
    }

    /** 테스트끼리 관계를 물려받으면(JUnit은 순서를 보장하지 않는다) 순환이 생겨 서로를 깨뜨린다 */
    @BeforeEach
    void clearRelations() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (long skillId : new long[] {first, second, third}) {
                TestFixtures.hardDeleteByColumn(conn, "SKILL_PREREQUISITE", "skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "SKILL_PREREQUISITE", "prereq_skill_id", skillId);
            }
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (long skillId : new long[] {first, second, third}) {
                // SKILL은 RESTRICT라 관계를 먼저 지워야 한다. 양쪽 컬럼 모두에서 지운다.
                TestFixtures.hardDeleteByColumn(conn, "SKILL_PREREQUISITE", "skill_id", skillId);
                TestFixtures.hardDeleteByColumn(conn, "SKILL_PREREQUISITE", "prereq_skill_id", skillId);
            }
            for (long skillId : new long[] {first, second, third}) {
                TestFixtures.hardDelete(conn, "SKILL", skillId);
            }
        }
    }

    @Test
    void 추가한_관계가_그래프로_읽히고_순서에_반영된다() throws Exception {
        service.add(second, first); // B를 하기 전에 A

        Map<Long, Set<Long>> edges = dao.edges();
        assertTrue(edges.getOrDefault(second, Set.of()).contains(first), "실제: " + edges);
        assertEquals(List.of(first, second), service.order(List.of(second, first)),
                "격차 분석이 B를 더 중요하게 봤어도 A가 먼저 나와야 한다");
    }

    @Test
    void 순환이_되는_관계는_저장하지_않고_이유를_알려준다() throws Exception {
        service.add(third, first); // C 전에 A

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.add(first, third)); // A 전에 C — 반대 방향
        assertTrue(e.getMessage().contains("순환"), "실제 메시지: " + e.getMessage());

        assertTrue(dao.edges().getOrDefault(first, Set.of()).isEmpty(), "막혔으면 저장도 안 돼야 한다");
    }

    @Test
    void 자기_자신을_선수로_두려_하면_막는다() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.add(first, first));
        assertTrue(e.getMessage().contains("자기 자신"), "실제 메시지: " + e.getMessage());
    }

    @Test
    void 같은_관계를_두_번_넣어도_한_줄만_남는다() throws Exception {
        service.add(third, second);
        service.add(third, second); // 중복 — UNIQUE라 INSERT만 하면 깨진다

        long count = dao.findAllWithNames().stream()
                .filter(p -> third == p.getSkillId() && second == p.getPrereqSkillId()).count();
        assertEquals(1, count, "중복 행이 생기면 위상 정렬의 진입차수가 어긋난다");
    }

    @Test
    void 지운_관계는_순서에_더_이상_영향을_주지_않는다() throws Exception {
        service.add(second, third); // B 전에 C
        assertEquals(List.of(third, second), service.order(List.of(second, third)));

        SkillPrerequisiteDto row = dao.findAllWithNames().stream()
                .filter(p -> second == p.getSkillId() && third == p.getPrereqSkillId())
                .findFirst().orElseThrow();
        assertTrue(service.delete(row.getId()));

        assertEquals(List.of(second, third), service.order(List.of(second, third)),
                "관계를 지우면 원래 중요도 순서로 돌아간다");
    }

    // 시드(sql/35)로 넣은 실제 그래프가 순환 없는지 — 순환이 있으면 그 기술들의 로드맵 순서를 정할 수 없다.
    // 관리자 화면이 막아 주지만 DB를 직접 고치거나 시드를 잘못 늘리면 생길 수 있어 여기서 지킨다.
    @Test
    void 지금_DB에_들어_있는_선수관계_전체에_순환이_없다() throws Exception {
        Map<Long, Set<Long>> edges = dao.edges();
        List<Long> everySkill = edges.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.concat(java.util.stream.Stream.of(e.getKey()),
                        e.getValue().stream()))
                .distinct().toList();

        // 순환이 있으면 위상 정렬이 못 꺼낸 것을 뒤에 붙인다. 진입차수가 0으로 떨어지는지 직접 센다.
        List<Long> blocked = new java.util.ArrayList<>();
        java.util.Set<Long> placed = new java.util.HashSet<>();
        List<Long> sorted = SkillPrerequisiteService.topologicalSort(everySkill, edges);
        for (Long skillId : sorted) {
            boolean ready = edges.getOrDefault(skillId, Set.of()).stream()
                    .filter(everySkill::contains)
                    .allMatch(placed::contains);
            if (!ready) {
                blocked.add(skillId);
            }
            placed.add(skillId);
        }
        assertTrue(blocked.isEmpty(),
                "선수 기술보다 먼저 나온 기술이 있다 — 순환을 의심하세요. skill_id=" + blocked);
    }

    @Test
    void 관리자_화면_목록은_기술_이름을_함께_준다() throws Exception {
        service.add(second, first);

        SkillPrerequisiteDto row = dao.findAllWithNames().stream()
                .filter(p -> second == p.getSkillId() && first == p.getPrereqSkillId())
                .findFirst().orElseThrow();
        assertTrue(row.getSkillName().startsWith("선수테스트B_"), "실제: " + row.getSkillName());
        assertTrue(row.getPrereqSkillName().startsWith("선수테스트A_"), "실제: " + row.getPrereqSkillName());
    }
}
