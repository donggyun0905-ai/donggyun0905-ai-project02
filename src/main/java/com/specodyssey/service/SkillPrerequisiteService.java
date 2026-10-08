package com.specodyssey.service;

import com.specodyssey.dao.SkillPrerequisiteDao;
import com.specodyssey.dto.SkillPrerequisiteDto;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기술 선수관계로 로드맵 순서를 정한다 (2026-10-08).
 * 관련 요구사항: FR-33 로드맵 단계 이유 · FR-39 로드맵 생성
 *
 * 로드맵 순서는 지금까지 "필수냐 우대냐"와 티어로만 정했다. 그래서 Spring이 Java보다 먼저, Docker가
 * Linux보다 먼저 나오는 순서가 만들어질 수 있었다. SKILL_PREREQUISITE의 "A를 하기 전에 B" 관계를
 * 방향 그래프로 보고 <b>위상 정렬(Kahn 알고리즘)</b>로 순서를 정한다.
 *
 * 두 가지를 지킨다.
 *  1. <b>원래 순위를 최대한 유지한다.</b> 선수관계는 "이것보다 먼저"만 말하고 전체 순서를 말하지 않는다.
 *     그래서 큐에서 꺼낼 때 항상 "지금 꺼낼 수 있는 것 중 원래 순위가 가장 앞선 것"을 고른다
 *     (우선순위 큐 대신 정렬된 LinkedHashSet — 후보가 5개 안쪽이라 단순한 쪽이 읽기 쉽다).
 *     격차 분석이 매긴 중요도 순서를 선수관계가 필요한 만큼만 흔든다.
 *  2. <b>순환이 있어도 멈추지 않는다.</b> 관리자 화면이 DFS로 순환을 막지만, 데이터를 직접 고치면
 *     순환이 생길 수 있다. 그때 로드맵 생성이 실패하면 안 되므로, 정렬하지 못한 기술은 원래 순서대로
 *     뒤에 붙인다(FR-111과 같은 태도 — 데이터가 이상해도 화면은 멈추지 않는다).
 */
public class SkillPrerequisiteService {

    private final SkillPrerequisiteDao prerequisiteDao;

    public SkillPrerequisiteService() {
        this(new SkillPrerequisiteDao());
    }

    SkillPrerequisiteService(SkillPrerequisiteDao prerequisiteDao) {
        this.prerequisiteDao = prerequisiteDao;
    }

    /**
     * 이번 라운드 기술을 선수관계에 맞게 다시 줄 세운다. 관계가 없으면 받은 순서 그대로다.
     * @param rankedSkillIds 격차 분석이 매긴 순위 (앞이 더 중요)
     */
    public List<Long> order(List<Long> rankedSkillIds) throws SQLException {
        if (rankedSkillIds == null || rankedSkillIds.size() < 2) {
            return rankedSkillIds == null ? List.of() : new ArrayList<>(rankedSkillIds);
        }
        return topologicalSort(rankedSkillIds, prerequisiteDao.edges());
    }

    /**
     * Kahn 알고리즘. 라운드에 담긴 기술끼리의 관계만 본다 — 라운드 밖 기술을 선수로 끌어오면 이번 라운드가
     * 통째로 바뀌고, 그 기술은 다음 라운드에 어차피 들어온다.
     *
     * @param ranked 원래 순위 (동점 처리의 기준이 된다)
     * @param prereqs key = 뒤에 와야 하는 기술, value = 먼저 와야 하는 기술들 (그래프 전체)
     */
    static List<Long> topologicalSort(List<Long> ranked, Map<Long, Set<Long>> prereqs) {
        // 입력에 중복이 있으면 진입차수가 어긋난다 — 순서를 지키며 중복만 제거한다
        List<Long> nodes = new ArrayList<>(new LinkedHashSet<>(ranked));
        Set<Long> inRound = new HashSet<>(nodes);
        Map<Long, Integer> rank = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            rank.put(nodes.get(i), i);
        }

        // 진입차수 = 이 기술이 기다려야 하는 선수 기술 수 (라운드 안에 있는 것만)
        Map<Long, Integer> inDegree = new LinkedHashMap<>();
        Map<Long, List<Long>> unlocks = new LinkedHashMap<>(); // 선수 → 그걸 기다리는 기술들
        for (Long node : nodes) {
            inDegree.put(node, 0);
            unlocks.put(node, new ArrayList<>());
        }
        for (Long node : nodes) {
            for (Long prereq : prereqs.getOrDefault(node, Set.of())) {
                if (!inRound.contains(prereq) || prereq.equals(node)) {
                    continue; // 라운드 밖이거나 자기 자신(잘못된 데이터)
                }
                inDegree.merge(node, 1, Integer::sum);
                unlocks.get(prereq).add(node);
            }
        }

        // 꺼낼 수 있는 것들 — 항상 원래 순위가 가장 앞선 것부터 꺼낸다(안정적인 순서)
        List<Long> ready = new ArrayList<>();
        for (Long node : nodes) {
            if (inDegree.get(node) == 0) {
                ready.add(node);
            }
        }
        List<Long> sorted = new ArrayList<>();
        while (!ready.isEmpty()) {
            ready.sort((a, b) -> Integer.compare(rank.get(a), rank.get(b)));
            Long next = ready.remove(0);
            sorted.add(next);
            for (Long waiting : unlocks.get(next)) {
                if (inDegree.merge(waiting, -1, Integer::sum) == 0) {
                    ready.add(waiting);
                }
            }
        }

        // 순환에 걸려 못 꺼낸 기술은 원래 순서로 뒤에 붙인다 — 로드맵 생성이 멈추면 안 된다
        if (sorted.size() < nodes.size()) {
            Set<Long> placed = new HashSet<>(sorted);
            for (Long node : nodes) {
                if (!placed.contains(node)) {
                    sorted.add(node);
                }
            }
        }
        return sorted;
    }

    // ---------------------------------------------------------------- 관리자 화면

    public List<SkillPrerequisiteDto> list() throws SQLException {
        return prerequisiteDao.findAllWithNames();
    }

    /**
     * 선수관계를 추가한다. 순환이 생기면 넣지 않고 예외를 던진다 — 메시지는 화면에 그대로 보여 준다.
     * @throws IllegalArgumentException 같은 기술이거나, 이 관계가 순환을 만드는 경우
     */
    public void add(Long skillId, Long prereqSkillId) throws SQLException {
        if (skillId == null || prereqSkillId == null) {
            throw new IllegalArgumentException("기술과 선수 기술을 모두 고르세요.");
        }
        if (skillId.equals(prereqSkillId)) {
            throw new IllegalArgumentException("같은 기술을 자기 자신의 선수 기술로 둘 수 없습니다.");
        }
        if (wouldCreateCycle(skillId, prereqSkillId, prerequisiteDao.edges())) {
            throw new IllegalArgumentException(
                    "이 관계를 넣으면 순환이 생깁니다(A 전에 B, B 전에 A). 먼저 반대 방향 관계를 지우세요.");
        }
        prerequisiteDao.upsert(skillId, prereqSkillId);
    }

    public boolean delete(Long id) throws SQLException {
        return id != null && prerequisiteDao.delete(id);
    }

    /**
     * skill → prereq 관계를 넣으면 순환이 생기는지 <b>DFS</b>로 본다.
     *
     * 순환은 "prereq에서 선수관계를 따라가다 skill에 닿는" 경우다. 예를 들어 이미 "Java 전에 Spring"이
     * 있는데 "Spring 전에 Java"를 넣으려 하면, Java의 선수(Spring)를 따라가다 Spring에 닿는다.
     * 간선이 skill → (그 선수들) 방향이므로 prereq에서 출발해 skill을 찾으면 된다.
     *
     * 반복문 + 명시적 스택으로 쓴다 — 재귀는 관계가 길게 이어지면 스택이 넘칠 수 있다.
     */
    static boolean wouldCreateCycle(Long skillId, Long prereqSkillId, Map<Long, Set<Long>> prereqs) {
        if (skillId.equals(prereqSkillId)) {
            return true; // 자기 자신이 가장 짧은 순환
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(prereqSkillId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (!visited.add(current)) {
                continue; // 이미 본 곳 — 기존 데이터에 순환이 있어도 여기서 멈춘다
            }
            if (current.equals(skillId)) {
                return true;
            }
            for (Long next : prereqs.getOrDefault(current, Set.of())) {
                if (!visited.contains(next)) {
                    stack.push(next);
                }
            }
        }
        return false;
    }
}
