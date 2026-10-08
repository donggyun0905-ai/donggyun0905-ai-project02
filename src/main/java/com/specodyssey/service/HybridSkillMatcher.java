package com.specodyssey.service;

import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.EditDistanceUtil;
import com.specodyssey.util.LocalEmbedder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 하이브리드 기술명 매칭 — 글자 유사도 순위와 임베딩 순위를 RRF로 합친다 (2026-10-08).
 * 관련 요구사항: FR-23 · 25 기술 입력 매칭 (TD-1 시맨틱 매칭)
 *
 * 기존 매칭은 "정확 일치 → 접두·부분 → 편집거리(0.75) → 임베딩(0.75)"을 차례로 시도하고, 각 단계가
 * 자기 임계값을 못 넘으면 거기서 끝이다. 그래서 <b>두 신호가 모두 "조금 비슷하다"고 말하는 입력</b>을
 * 놓친다 — 글자로도 0.6, 의미로도 0.7이면 둘 다 떨어뜨리지만 사람 눈에는 같은 기술인 경우가 있다.
 *
 * <b>기존 동작은 전혀 바꾸지 않는다.</b> 종속(EmbeddingMatcher → FuzzyNameMatcher)이 뭐라도 찾으면
 * 그대로 돌려준다. 못 찾았을 때만 이 단계가 돈다 — 즉 <b>없던 결과만 더하고 있던 결과는 안 건드린다</b>.
 * 사전(SKILL_ALIAS) 일치가 먼저 잡던 "자바 백엔드 → Java" 같은 동작도 그대로다.
 *
 * <b>받아들이는 조건</b>: RRF 1등이 두 순위에서 <b>모두 상위 AGREEMENT_RANK 안</b>에 있어야 한다.
 * 한쪽만 좋아하는 후보가 바로 알려진 오탐이다(EmbeddingMatcher 주석의 Java↔JavaScript = 0.805 —
 * 임베딩만 보면 1등이지만 글자로는 전혀 아니다). 두 신호가 같이 가리킬 때만 받으면 그 종류를 막는다.
 * 그래서 각 신호의 개별 임계값은 종속보다 낮춰도 된다 — "합의"가 확신을 대신한다.
 *
 * 한계: 표기 체계가 아예 다른 입력("리액트" vs "React.js")은 글자 순위가 전혀 못 잡아서 이 단계로도
 * 안 된다. 그건 SKILL_ALIAS 사전이 맡는 영역이다 — 여기서 억지로 받으면 오탐이 늘어난다.
 */
public class HybridSkillMatcher implements SkillMatcher {

    private static final Logger LOG = Logger.getLogger(HybridSkillMatcher.class.getName());

    /** 각 순위에서 이 등수 안에 둘 다 있어야 받아들인다 */
    static final int AGREEMENT_RANK = 3;
    /** 순위에 올릴 후보 수 — 너무 길면 "합의"가 의미를 잃는다 */
    static final int CANDIDATES = 10;
    /** 글자 유사도 바닥 — 이보다 낮으면 순위에 올리지 않는다(종속의 0.75보다 낮다) */
    static final double MIN_LEXICAL = 0.45;
    /** 코사인 바닥 — 종속의 0.75보다 낮다 */
    static final double MIN_SEMANTIC = 0.60;

    private final SkillMatcher delegate;

    public HybridSkillMatcher() {
        this(new EmbeddingMatcher());
    }

    public HybridSkillMatcher(SkillMatcher delegate) {
        this.delegate = delegate;
    }

    @Override
    public MatchResult match(String raw) throws SQLException {
        List<MatchResult> all = matchAll(raw);
        return all.isEmpty() ? MatchResult.none() : all.get(0);
    }

    @Override
    public List<MatchResult> matchAll(String raw) throws SQLException {
        List<MatchResult> found = delegate.matchAll(raw);
        if (!found.isEmpty()) {
            return found;
        }
        MatchResult fused = fusedMatch(raw);
        return fused.skillId() == null ? List.of() : List.of(fused);
    }

    private MatchResult fusedMatch(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return MatchResult.none();
        }
        SkillCatalog.Snapshot catalog = SkillCatalog.current();
        Map<Long, Double> lexicalScores = lexicalScores(raw, catalog);
        Map<Long, Double> semanticScores = semanticScores(raw, catalog);
        if (lexicalScores.isEmpty() || semanticScores.isEmpty()) {
            return MatchResult.none(); // 합의할 상대가 없다
        }

        List<Long> lexicalRanking = topRanking(lexicalScores);
        List<Long> semanticRanking = topRanking(semanticScores);
        List<RankFusion.Fused<Long>> fused = RankFusion.fuse(lexicalRanking, semanticRanking);
        if (fused.isEmpty()) {
            return MatchResult.none();
        }

        RankFusion.Fused<Long> winner = fused.get(0);
        int lexicalRank = RankFusion.rankOf(lexicalRanking, winner.key());
        int semanticRank = RankFusion.rankOf(semanticRanking, winner.key());
        boolean agreed = lexicalRank > 0 && lexicalRank <= AGREEMENT_RANK
                && semanticRank > 0 && semanticRank <= AGREEMENT_RANK;
        if (!agreed) {
            return MatchResult.none();
        }

        // 화면·DB에 남기는 점수는 두 신호의 평균 — 사용자가 보는 "유사도"가 RRF 내부 값(0.03 같은)이
        // 되면 의미를 알 수 없다. SKILL.similarity_score는 DECIMAL(5,4)라 0~1이어야 한다.
        double score = (lexicalScores.get(winner.key()) + semanticScores.get(winner.key())) / 2.0;
        LOG.log(Level.FINE, () -> "하이브리드 매칭: " + raw + " → skillId=" + winner.key()
                + " (글자 " + lexicalRank + "등, 의미 " + semanticRank + "등)");
        return new MatchResult(winner.key(), score);
    }

    /** 이름·별칭 중 가장 비슷한 쪽의 글자 유사도. 바닥을 못 넘으면 넣지 않는다. */
    private Map<Long, Double> lexicalScores(String raw, SkillCatalog.Snapshot catalog) {
        String query = normalize(raw);
        Map<Long, Double> scores = new HashMap<>();
        if (query.isEmpty()) {
            return scores;
        }
        for (SkillDto skill : catalog.skills()) {
            if (skill.getSkillName() != null) {
                consider(scores, skill.getId(), similarity(query, normalize(skill.getSkillName())));
            }
        }
        for (SkillAliasDto alias : catalog.aliases()) {
            if (alias.getAliasName() != null) {
                consider(scores, alias.getSkillId(), similarity(query, normalize(alias.getAliasName())));
            }
        }
        return scores;
    }

    private Map<Long, Double> semanticScores(String raw, SkillCatalog.Snapshot catalog) {
        Map<Long, Double> scores = new HashMap<>();
        LocalEmbedder embedder = EmbeddingMatcher.embedder();
        if (embedder == null) {
            return scores; // 모델이 없는 PC — 합의할 두 번째 신호가 없으니 이 단계는 건너뛴다
        }
        float[] queryVector;
        try {
            queryVector = embedder.embed(raw);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "임베딩 계산 실패 — 하이브리드 매칭을 건너뜁니다: " + raw, e);
            return scores;
        }
        for (SkillCatalog.SkillVector candidate : catalog.vectors()) {
            consider(scores, candidate.skillId(),
                    LocalEmbedder.cosine(queryVector, candidate.vector()), MIN_SEMANTIC);
        }
        return scores;
    }

    private static void consider(Map<Long, Double> scores, Long skillId, double score) {
        consider(scores, skillId, score, MIN_LEXICAL);
    }

    /** 같은 기술에 여러 이름·별칭이 걸리면 가장 높은 점수만 남긴다 */
    private static void consider(Map<Long, Double> scores, Long skillId, double score, double floor) {
        if (skillId == null || score < floor) {
            return;
        }
        scores.merge(skillId, score, Math::max);
    }

    private static List<Long> topRanking(Map<Long, Double> scores) {
        List<Long> ranking = new ArrayList<>(scores.keySet());
        // 동점이면 skillId 순 — 결과가 호출마다 달라지지 않게
        ranking.sort(Comparator.comparingDouble((Long id) -> -scores.get(id)).thenComparing(id -> id));
        return ranking.size() > CANDIDATES ? new ArrayList<>(ranking.subList(0, CANDIDATES)) : ranking;
    }

    /**
     * 글자 유사도 — 편집거리와 문자 2-gram 겹침 중 높은 쪽.
     * 편집거리만 쓰면 "springboot"와 "spring boot"처럼 순서가 같고 길이만 다른 쌍에 약하고,
     * 2-gram만 쓰면 짧은 이름의 오타에 약하다.
     */
    static double similarity(String query, String name) {
        if (query.isEmpty() || name.isEmpty()) {
            return 0.0;
        }
        if (query.equals(name)) {
            return 1.0;
        }
        int distance = EditDistanceUtil.transpositionAwareDistance(query, name);
        double edit = 1.0 - (double) distance / Math.max(query.length(), name.length());
        return Math.max(edit, bigramDice(query, name));
    }

    /** 문자 2-gram Dice 계수 — 2 × 공통 / (전체 + 전체). 한 글자 입력은 그 글자 자체를 토큰으로 본다. */
    static double bigramDice(String a, String b) {
        List<String> left = bigrams(a);
        List<String> right = bigrams(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        List<String> remaining = new ArrayList<>(right);
        int common = 0;
        for (String gram : left) {
            if (remaining.remove(gram)) {
                common++;
            }
        }
        return 2.0 * common / (left.size() + right.size());
    }

    private static List<String> bigrams(String s) {
        List<String> grams = new ArrayList<>();
        if (s.length() == 1) {
            grams.add(s);
            return grams;
        }
        for (int i = 0; i + 1 < s.length(); i++) {
            grams.add(s.substring(i, i + 2));
        }
        return grams;
    }

    /** FuzzyNameMatcher와 같은 기준으로 맞춘다 — 공백·점·밑줄·하이픈을 지우고 소문자 */
    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s._-]", "").toLowerCase(java.util.Locale.ROOT);
    }
}
