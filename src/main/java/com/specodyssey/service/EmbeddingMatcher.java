package com.specodyssey.service;

import com.google.gson.Gson;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.SkillDto;
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
 * 모델 파일(EMBEDDING_MODEL_DIR)이 없는 PC에서는 첫 호출에서 조용히 포기하고 그 뒤로는
 * FuzzyNameMatcher 결과만 쓴다 — 팀원 전원이 440MB 모델을 받아둘 필요는 없다.
 */
public class EmbeddingMatcher implements SkillMatcher {

    private static final Logger LOG = Logger.getLogger(EmbeddingMatcher.class.getName());
    private static final Gson GSON = new Gson();
    private static final double SIMILARITY_THRESHOLD = 0.75;

    // 모델 로딩이 수 초 걸려서 프로세스당 한 번만 만들고 끝까지 재사용한다(웹앱 수명 = JVM 수명).
    // volatile 두 개로 "이미 시도했는데 실패했다"와 "성공한 인스턴스"를 구분한다.
    private static volatile LocalEmbedder sharedEmbedder;
    private static volatile boolean embedderUnavailable;

    private final SkillMatcher delegate;
    private final SkillDao skillDao;

    public EmbeddingMatcher() {
        this(new FuzzyNameMatcher());
    }

    public EmbeddingMatcher(SkillMatcher delegate) {
        this.delegate = delegate;
        this.skillDao = new SkillDao();
    }

    @Override
    public MatchResult match(String raw) throws SQLException {
        MatchResult delegateResult = delegate.match(raw);
        if (delegateResult.skillId() != null) {
            return delegateResult;
        }
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

        List<SkillDto> skills = skillDao.findAll();
        Long bestSkillId = null;
        double bestScore = 0.0;
        for (SkillDto skill : skills) {
            if (skill.getEmbeddingVector() == null) {
                continue;
            }
            float[] candidateVector = GSON.fromJson(skill.getEmbeddingVector(), float[].class);
            double score = LocalEmbedder.cosine(queryVector, candidateVector);
            if (score > bestScore) {
                bestScore = score;
                bestSkillId = skill.getId();
            }
        }

        if (bestSkillId == null || bestScore < SIMILARITY_THRESHOLD) {
            return MatchResult.none();
        }
        return new MatchResult(bestSkillId, bestScore);
    }

    private static LocalEmbedder embedder() {
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
