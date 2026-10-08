package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 기술 선수관계 — 위상 정렬(Kahn)과 순환 검사(DFS). DB 없이 규칙만 본다 (2026-10-08).
 *
 * 로드맵 순서는 "필수냐 우대냐"와 티어로만 정해서 Spring이 Java보다 먼저 나올 수 있었다.
 * 여기서 고정하는 것: 선수관계를 지키는지, 지키면서도 원래 중요도 순서를 최대한 유지하는지,
 * 데이터에 순환이 있어도 로드맵 생성이 멈추지 않는지.
 */
class SkillPrerequisiteServiceTest {

    // 읽기 쉽게 이름을 숫자에 붙인다
    private static final long JAVA = 1L;
    private static final long SPRING = 2L;
    private static final long LINUX = 3L;
    private static final long DOCKER = 4L;
    private static final long SQL = 5L;

    /** "key를 하기 전에 value들" */
    private static Map<Long, Set<Long>> graph(Object... pairs) {
        Map<Long, Set<Long>> prereqs = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            prereqs.put((Long) pairs[i], (Set<Long>) pairs[i + 1]);
        }
        return prereqs;
    }

    @Test
    void 선수관계가_없으면_받은_순서를_그대로_둔다() {
        List<Long> ranked = List.of(SPRING, JAVA, DOCKER);

        assertEquals(ranked, SkillPrerequisiteService.topologicalSort(ranked, Map.of()));
    }

    @Test
    void 선수_기술이_뒤에_있으면_앞으로_끌어올린다() {
        // 격차 분석은 Spring을 더 중요하게 봤지만, Spring 전에 Java를 해야 한다
        List<Long> ranked = List.of(SPRING, JAVA);

        List<Long> sorted = SkillPrerequisiteService.topologicalSort(
                ranked, graph(SPRING, Set.of(JAVA)));

        assertEquals(List.of(JAVA, SPRING), sorted);
    }

    @Test
    void 선수관계가_필요한_만큼만_순서를_흔든다() {
        // SQL은 아무 관계가 없다 — Spring 때문에 Java가 올라가더라도 SQL은 원래 자리를 지켜야 한다
        List<Long> ranked = List.of(SPRING, SQL, JAVA);

        List<Long> sorted = SkillPrerequisiteService.topologicalSort(
                ranked, graph(SPRING, Set.of(JAVA)));

        assertEquals(List.of(SQL, JAVA, SPRING), sorted,
                "꺼낼 수 있는 것 중 원래 순위가 앞선 것부터 — SQL이 Java보다 앞이었다");
    }

    @Test
    void 선수_기술이_여러_단계로_이어져도_순서를_지킨다() {
        // Docker 전에 Linux, Spring 전에 Java — 사슬 두 개가 섞여 있다
        List<Long> ranked = List.of(DOCKER, SPRING, LINUX, JAVA);

        List<Long> sorted = SkillPrerequisiteService.topologicalSort(
                ranked, graph(DOCKER, Set.of(LINUX), SPRING, Set.of(JAVA)));

        assertTrue(sorted.indexOf(LINUX) < sorted.indexOf(DOCKER), "실제: " + sorted);
        assertTrue(sorted.indexOf(JAVA) < sorted.indexOf(SPRING), "실제: " + sorted);
        assertEquals(4, sorted.size());
    }

    @Test
    void 사슬이_셋_이상_길어도_순서를_지킨다() {
        // SQL 전에 Java, Spring 전에 SQL → Java → SQL → Spring
        List<Long> ranked = List.of(SPRING, SQL, JAVA);

        List<Long> sorted = SkillPrerequisiteService.topologicalSort(
                ranked, graph(SPRING, Set.of(SQL), SQL, Set.of(JAVA)));

        assertEquals(List.of(JAVA, SQL, SPRING), sorted);
    }

    @Test
    void 라운드_밖의_선수_기술은_이번_순서에_끌어오지_않는다() {
        // Spring 전에 Java인데 이번 라운드에 Java가 없다 — Java는 다음 라운드에 어차피 들어온다
        List<Long> ranked = List.of(SPRING, SQL);

        List<Long> sorted = SkillPrerequisiteService.topologicalSort(
                ranked, graph(SPRING, Set.of(JAVA)));

        assertEquals(List.of(SPRING, SQL), sorted, "라운드 구성을 선수관계가 바꾸면 안 된다");
    }

    @Test
    void 데이터에_순환이_있어도_로드맵_생성이_멈추지_않는다() {
        // 관리자 화면은 순환을 막지만 DB를 직접 고치면 생길 수 있다
        List<Long> ranked = List.of(JAVA, SPRING, SQL);

        List<Long> sorted = SkillPrerequisiteService.topologicalSort(
                ranked, graph(JAVA, Set.of(SPRING), SPRING, Set.of(JAVA)));

        assertEquals(3, sorted.size(), "하나도 빠뜨리지 않아야 한다: " + sorted);
        assertTrue(sorted.containsAll(ranked));
        assertEquals(SQL, sorted.get(0), "순환에 안 걸린 것은 정상적으로 먼저 나온다");
    }

    @Test
    void 같은_기술이_두_번_들어와도_한_번만_나온다() {
        List<Long> sorted = SkillPrerequisiteService.topologicalSort(
                List.of(SPRING, JAVA, SPRING), graph(SPRING, Set.of(JAVA)));

        assertEquals(List.of(JAVA, SPRING), sorted, "중복이 있으면 진입차수가 어긋난다");
    }

    @Test
    void 자기_자신을_선수로_둔_잘못된_데이터는_무시한다() {
        List<Long> sorted = SkillPrerequisiteService.topologicalSort(
                List.of(JAVA, SQL), graph(JAVA, Set.of(JAVA)));

        assertEquals(List.of(JAVA, SQL), sorted, "자기 자신 간선에 막혀 영원히 못 꺼내면 안 된다");
    }

    // ---------------------------------------------------------------- 순환 검사 (DFS)

    @Test
    void 반대_방향_관계가_이미_있으면_순환으로_막는다() {
        // 이미 "Java 전에 Spring"이 있는데 "Spring 전에 Java"를 넣으려 한다
        Map<Long, Set<Long>> prereqs = graph(JAVA, Set.of(SPRING));

        assertTrue(SkillPrerequisiteService.wouldCreateCycle(SPRING, JAVA, prereqs));
    }

    @Test
    void 사슬을_돌아_제자리로_오는_관계도_막는다() {
        // Java 전에 Spring, Spring 전에 SQL → 여기에 "SQL 전에 Java"를 넣으면 세 개가 한 바퀴 돈다
        Map<Long, Set<Long>> prereqs = graph(JAVA, Set.of(SPRING), SPRING, Set.of(SQL));

        assertTrue(SkillPrerequisiteService.wouldCreateCycle(SQL, JAVA, prereqs));
    }

    @Test
    void 자기_자신은_가장_짧은_순환이다() {
        assertTrue(SkillPrerequisiteService.wouldCreateCycle(JAVA, JAVA, Map.of()));
    }

    @Test
    void 순환이_되지_않는_관계는_통과시킨다() {
        Map<Long, Set<Long>> prereqs = graph(SPRING, Set.of(JAVA));

        assertFalse(SkillPrerequisiteService.wouldCreateCycle(DOCKER, LINUX, prereqs), "관계없는 두 기술");
        assertFalse(SkillPrerequisiteService.wouldCreateCycle(SPRING, SQL, prereqs), "선수를 하나 더 두는 것");
        assertFalse(SkillPrerequisiteService.wouldCreateCycle(SQL, SPRING, prereqs),
                "SQL 전에 Spring — Spring의 선수(Java)를 따라가도 SQL에 닿지 않는다");
    }

    @Test
    void 기존_데이터에_이미_순환이_있어도_검사가_멈추지_않는다() {
        // DB를 직접 고쳐 Java↔Spring 순환이 생긴 상태에서 관리자가 다른 관계를 넣으려 한다
        Map<Long, Set<Long>> broken = graph(JAVA, Set.of(SPRING), SPRING, Set.of(JAVA));

        assertFalse(SkillPrerequisiteService.wouldCreateCycle(DOCKER, LINUX, broken),
                "무한 루프에 빠지지 않고 답을 내야 한다");
    }
}
