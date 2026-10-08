package com.specodyssey.service;

import com.specodyssey.dao.SkillCooccurrenceDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserDto;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 함께 익힌 기술 추천 — 자카드 유사도 + 동시 출현 빈도 (2026-10-08).
 * 관련 요구사항: FR-114 다음에 할 일
 *
 * "같은 직무를 목표로 한 사람들이 내가 가진 기술 다음에 많이 익힌 기술"을 보여 준다. 회원 데이터로 하는
 * 가장 단순한 협업 필터링이다 — 직무 요구 기술(JOB_REQUIRED_SKILL)은 "직무가 요구하는 것"이고,
 * 이쪽은 "비슷한 사람들이 실제로 고른 것"이라 서로 다른 정보다.
 *
 * <b>왜 단순 빈도가 아니라 자카드로 가중하나</b>: 인기 기술은 아무 상관 없는 사람도 갖고 있다.
 * 나와 기술 구성이 비슷한 사람(자카드가 높은 사람)이 가진 기술에 더 무게를 주면 "나와 비슷한 길을
 * 걷는 사람들의 다음 걸음"에 가까워진다. 자카드 = |교집합| / |합집합| 로 집합 크기 차이도 보정된다.
 *
 * <b>개인정보</b>: 집계만 내보낸다. 누가 가졌는지는 돌려주지 않고, 표본이 모자라면 아무것도 보여 주지
 * 않는다(MIN_PEERS·MIN_HOLDERS) — 2~3명으로 "많이 익혔다"고 말하면 틀린 말인 데다, 사람이 적을 때는
 * 추천 자체가 특정 개인의 프로필을 드러낼 수 있다. 데이터 인사이트의 또래 비교와 같은 태도다.
 *
 * 계산은 DB 없이 테스트할 수 있게 static으로 분리했다(InsightService와 같은 방식).
 */
public class SkillCooccurrenceService {

    /** 이보다 적으면 추천하지 않는다 — 표본이 적으면 "많이 익혔다"가 거짓말이 된다 */
    static final int MIN_PEERS = 3;
    /** 그 기술을 가진 또래가 이보다 적으면 넣지 않는다 — 한 사람의 선택은 추천 근거가 못 된다 */
    static final int MIN_HOLDERS = 2;
    static final int TOP_N = 5;

    /** 추천 한 줄. holders/peers로 "N명 중 M명" 문구를 만든다. */
    public record Suggestion(Long skillId, String skillName, int holders, int peers, double score) {

        public Long getSkillId() {
            return skillId;
        }

        public String getSkillName() {
            return skillName;
        }

        public int getHolders() {
            return holders;
        }

        public int getPeers() {
            return peers;
        }

        public double getScore() {
            return score;
        }

        /** "같은 직무를 목표로 한 12명 중 7명" */
        public String getShareText() {
            return peers + "명 중 " + holders + "명";
        }

        public String shareText() {
            return getShareText();
        }
    }

    private final SkillCooccurrenceDao cooccurrenceDao = new SkillCooccurrenceDao();
    private final SkillDao skillDao = new SkillDao();
    private final UserDao userDao = new UserDao();

    /**
     * @return 추천 목록. 목표 직무가 없거나 표본이 모자라면 빈 목록 — 화면은 그때 아무것도 그리지 않는다
     */
    public List<Suggestion> suggest(Long userId) throws SQLException {
        UserDto user = userDao.findById(userId);
        if (user == null || user.getDesiredJobId() == null) {
            return List.of(); // 목표 직무가 없으면 "같은 직무를 목표로 한 사람"을 정의할 수 없다
        }
        Set<Long> mine = cooccurrenceDao.mySkillIds(userId);
        if (mine.isEmpty()) {
            return List.of(); // 내 기술이 없으면 자카드가 전부 0 — 비교할 게 없다
        }
        Map<Long, Set<Long>> peers = cooccurrenceDao.peerSkillsByJob(user.getDesiredJobId(), userId);
        List<Ranked> ranked = rank(mine, peers);
        if (ranked.isEmpty()) {
            return List.of();
        }

        List<Suggestion> suggestions = new ArrayList<>();
        for (Ranked row : ranked) {
            SkillDto skill = skillDao.findById(row.skillId());
            if (skill != null && skill.getSkillName() != null) {
                suggestions.add(new Suggestion(row.skillId(), skill.getSkillName(),
                        row.holders(), peers.size(), row.score()));
            }
        }
        return suggestions;
    }

    /** 이름을 붙이기 전의 순위 — 계산만 분리해 DB 없이 테스트한다 */
    record Ranked(Long skillId, int holders, double score) {
    }

    /**
     * 내가 안 가진 기술을 "나와 비슷한 또래가 얼마나 가졌는지"로 줄 세운다.
     *
     * @param mine  내 기술 집합
     * @param peers 또래별 기술 집합 (key는 익명 user_id — 누가 가졌는지는 결과에 나가지 않는다)
     */
    static List<Ranked> rank(Set<Long> mine, Map<Long, Set<Long>> peers) {
        if (mine == null || mine.isEmpty() || peers == null || peers.size() < MIN_PEERS) {
            return List.of();
        }
        Map<Long, Integer> holders = new HashMap<>();
        Map<Long, Double> weighted = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> peer : peers.entrySet()) {
            Set<Long> peerSkills = peer.getValue();
            if (peerSkills == null || peerSkills.isEmpty()) {
                continue;
            }
            double similarity = jaccard(mine, peerSkills);
            if (similarity <= 0) {
                continue; // 겹치는 기술이 하나도 없는 사람은 "비슷한 길"이 아니다
            }
            for (Long skillId : peerSkills) {
                if (mine.contains(skillId)) {
                    continue; // 이미 가진 기술은 추천이 아니다
                }
                holders.merge(skillId, 1, Integer::sum);
                weighted.merge(skillId, similarity, Double::sum);
            }
        }

        List<Ranked> ranked = new ArrayList<>();
        holders.forEach((skillId, count) -> {
            if (count >= MIN_HOLDERS) {
                ranked.add(new Ranked(skillId, count, weighted.getOrDefault(skillId, 0.0)));
            }
        });
        // 가중 점수 → 가진 사람 수 → skillId 순. 마지막 기준은 결과가 호출마다 달라지지 않게 하려는 것.
        ranked.sort(Comparator.comparingDouble((Ranked r) -> -r.score())
                .thenComparing(Comparator.comparingInt((Ranked r) -> -r.holders()))
                .thenComparing(Ranked::skillId));
        return ranked.size() > TOP_N ? List.copyOf(ranked.subList(0, TOP_N)) : List.copyOf(ranked);
    }

    /** 자카드 유사도 = |교집합| / |합집합|. 둘 다 비면 0. */
    static double jaccard(Set<Long> left, Set<Long> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<Long> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<Long> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }
}
