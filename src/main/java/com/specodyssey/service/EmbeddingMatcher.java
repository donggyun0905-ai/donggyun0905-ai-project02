package com.specodyssey.service;

import com.specodyssey.util.LocalEmbedder;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TD-1 임베딩 시맨틱 매칭 — 로컬 임베딩(ko-sroberta-multitask)이 실제로 붙는 자리
 * (2026-09-30, 임베딩 마무리 담당자 작업). SkillMatcher.java의 교체 지점 그대로.
 *
 * 정확 일치(SKILL·SKILL_ALIAS)는 FuzzyNameMatcher에 그대로 위임한다 — 여기서 중복 구현하지
 * 않는다. 그게 실패했을 때만 임베딩으로 넘어간다.
 *
 * ⚠️ 임계값 실측 결과(직접 측정, 2026-09-30) — 이 모델은 짧은 기술명 사이의 "같다/다르다"를
 * 깔끔하게 못 가른다:
 *   - Java↔JavaScript(서로 다른 기술) = 0.805 — 오탐 위험
 *   - 자바↔Java(같은 기술, 표기만 다름) = 0.680 — 오히려 더 낮음
 *   - "웹 서버 구축 기술"↔"백엔드 서버 개발 능력"(의미상 진짜 비슷한 문장) = 0.783
 * 즉 진짜 유사 문장(0.783)이 오탐 위험 쌍(0.805)보다 점수가 낮다 — 어떤 임계값을 잡아도 짧은
 * 기술명끼리는 완벽히 가를 수 없다는 뜻이다. 그래서 이 매처가 실제로 잘 맞는 자리는 "짧은 기술명
 * vs 기술명" 비교가 아니라 "긴 문장·설명형 입력"이라고 보고, 임계값(0.75)은 진짜 유사 문장을
 * 놓치지 않는 쪽에 맞췄다. 짧은 이름끼리의 오탐(Java↔JavaScript류)은 실무에서는 대부분
 * SKILL_ALIAS 사전이 먼저 정확 일치로 잡아줘서 애초에 이 단계까지 안 온다 — 사전에 없는
 * 새로운 짧은 이름 조합에서는 여전히 오탐 가능성이 남아 있다는 걸 인지하고 채택한다.
 *
 * 2026-10-02: "자바 백엔드"가 임베딩에서 JavaScript(0.769)로 가던 문제는 FuzzyNameMatcher의 단어 단위
 * 매칭("자바"→Java)이 먼저 잡도록 해서 막았다. 실제 스킬 이름 벡터와 비교하면 0.75를 넘는 경우가 드물고
 * 그마저 Java/JavaScript 계열이라, 이 단계는 이름·별칭으로 못 찾은 입력의 마지막 수단으로만 둔다.
 *
 * 모델 파일(EMBEDDING_MODEL_DIR)이 없는 PC에서는 첫 호출에서 조용히 포기하고 그 뒤로는
 * FuzzyNameMatcher 결과만 쓴다 — 팀원 전원이 440MB 모델을 받아둘 필요는 없다.
 */
public class EmbeddingMatcher implements SkillMatcher {

    private static final Logger LOG = Logger.getLogger(EmbeddingMatcher.class.getName());
    private static final double SIMILARITY_THRESHOLD = 0.75;

    // 모델 로딩이 수 초 걸려서 프로세스당 한 번만 만들고 끝까지 재사용한다(웹앱 수명 = JVM 수명).
    // volatile 두 개로 "이미 시도했는데 실패했다"와 "성공한 인스턴스"를 구분한다.
    private static volatile LocalEmbedder sharedEmbedder;
    private static volatile boolean embedderUnavailable;

    private final SkillMatcher delegate;

    public EmbeddingMatcher() {
        this(new FuzzyNameMatcher());
    }

    public EmbeddingMatcher(SkillMatcher delegate) {
        this.delegate = delegate;
    }

    @Override
    public MatchResult match(String raw) throws SQLException {
        List<MatchResult> all = matchAll(raw);
        return all.isEmpty() ? MatchResult.none() : all.get(0);
    }

    // 이름·별칭 단계에서 하나라도 찾으면(단어 단위로 여러 개일 수 있음) 그대로 쓰고, 못 찾았을 때만 임베딩
    @Override
    public List<MatchResult> matchAll(String raw) throws SQLException {
        List<MatchResult> found = delegate.matchAll(raw);
        if (!found.isEmpty()) {
            return found;
        }
        MatchResult embedded = embeddingMatch(raw);
        return embedded.skillId() == null ? List.of() : List.of(embedded);
    }

    private MatchResult embeddingMatch(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return MatchResult.none();
        }

        LocalEmbedder embedder = embedder();
        if (embedder == null) {
            return MatchResult.none();
        }

        float[] queryVector;
        try {
            queryVector = embedder.embed(raw);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "임베딩 계산 실패 — 이번 매칭은 실패로 처리합니다: " + raw, e);
            return MatchResult.none();
        }

        // 벡터는 매번 DB에서 읽어 파싱하지 않고 SkillCatalog에 미리 풀어 둔 것을 쓴다 (2026-10-01, 매칭 속도 개선)
        Long bestSkillId = null;
        double bestScore = 0.0;
        for (SkillCatalog.SkillVector candidate : SkillCatalog.current().vectors()) {
            double score = LocalEmbedder.cosine(queryVector, candidate.vector());
            if (score > bestScore) {
                bestScore = score;
                bestSkillId = candidate.skillId();
            }
        }

        if (bestSkillId == null || bestScore < SIMILARITY_THRESHOLD) {
            return MatchResult.none();
        }
        return new MatchResult(bestSkillId, bestScore);
    }

    // MajorAffinity(전공 ↔ 직무 계열)도 같은 모델을 쓴다 — 같은 패키지에 연다 (2026-10-06)
    static LocalEmbedder embedder() {
        if (embedderUnavailable) {
            return null;
        }
        LocalEmbedder existing = sharedEmbedder;
        if (existing != null) {
            return existing;
        }
        synchronized (EmbeddingMatcher.class) {
            if (embedderUnavailable) {
                return null;
            }
            if (sharedEmbedder == null) {
                try {
                    sharedEmbedder = LocalEmbedder.fromConfig();
                } catch (Exception e) {
                    LOG.log(Level.INFO, "EMBEDDING_MODEL_DIR 모델을 못 찾아 임베딩 매칭을 건너뜁니다 — "
                            + "FuzzyNameMatcher(정확 일치·편집거리)만 사용합니다.", e);
                    embedderUnavailable = true;
                    return null;
                }
            }
            return sharedEmbedder;
        }
    }
}
