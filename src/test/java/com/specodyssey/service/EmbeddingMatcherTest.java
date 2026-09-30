package com.specodyssey.service;

import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.util.AppConfig;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.LocalEmbedder;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EmbeddingMatcher 통합테스트 — 실제 모델로 검증한다. 모델 폴더(EMBEDDING_MODEL_DIR)가 없는
 * PC에서는 LocalEmbedderTest와 같은 이유로 건너뛴다.
 */
class EmbeddingMatcherTest {

    private static final Gson GSON = new Gson();

    private final SkillDao skillDao = new SkillDao();
    private final EmbeddingMatcher matcher = new EmbeddingMatcher();
    private Long backendSkillId;
    private Long unrelatedSkillId;

    @BeforeEach
    void setUp() throws Exception {
        String dir = AppConfig.get(LocalEmbedder.MODEL_DIR_KEY);
        assumeTrue(dir != null && Files.isRegularFile(Path.of(dir, "model.onnx")),
                "EMBEDDING_MODEL_DIR에 모델이 없어 건너뜀");

        // skill_name 자체는 DB UNIQUE 제약 때문에 유니크 접미사가 필요하지만, 저장하는 벡터는
        // 깨끗한 의미 문구로 계산한다 — 접미사(숫자)까지 같이 임베딩하면 토큰 노이즈가 커져서
        // 테스트가 임베딩 품질 문제로 흔들릴 수 있다. 편집거리로는 절대 못 잡을 만큼 이름을
        // 다르게 만든다 — 오직 임베딩 유사도로만 잡혀야 한다.
        String backendSkillName = "임베딩테스트_웹서버구축기술_" + System.nanoTime();
        String unrelatedSkillName = "임베딩테스트_요리조리법정리_" + System.nanoTime();
        try (Connection conn = DBUtil.getConnection();
             LocalEmbedder embedder = LocalEmbedder.fromConfig()) {
            backendSkillId = TestFixtures.insertSkill(conn, backendSkillName);
            skillDao.updateEmbedding(conn, backendSkillId, GSON.toJson(embedder.embed("웹 서버 구축 기술")),
                    LocalEmbedder.MODEL_NAME, LocalDateTime.now());

            unrelatedSkillId = TestFixtures.insertSkill(conn, unrelatedSkillName);
            skillDao.updateEmbedding(conn, unrelatedSkillId, GSON.toJson(embedder.embed("요리 조리법 정리")),
                    LocalEmbedder.MODEL_NAME, LocalDateTime.now());
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (backendSkillId == null) {
            return; // setUp이 assumeTrue로 건너뛴 경우
        }
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "SKILL", backendSkillId);
            TestFixtures.hardDelete(conn, "SKILL", unrelatedSkillId);
        }
    }

    @Test
    void 정확히_같은_이름이면_델리게이트의_정확일치가_그대로_쓰인다() throws Exception {
        String exactName = skillDao.findById(backendSkillId).getSkillName();

        SkillMatcher.MatchResult result = matcher.match(exactName);

        assertEquals(backendSkillId, result.skillId());
        assertEquals(1.0, result.score());
    }

    @Test
    void 편집거리로는_못_잡지만_의미가_비슷한_문장은_임베딩으로_잡힌다() throws Exception {
        // "웹서버구축기술"과 의미상 가깝지만 글자는 거의 안 겹치는 표현.
        String similarPhrase = "백엔드 서버 개발 능력";

        SkillMatcher.MatchResult result = matcher.match(similarPhrase);

        assertEquals(backendSkillId, result.skillId());
        assertTrue(result.score() >= 0.75, "임계값(0.75) 이상이어야 한다: " + result.score());
    }

    @Test
    void 의미가_전혀_다른_문장은_매칭되지_않는다() throws Exception {
        String unrelatedPhrase = "된장찌개 맛있게 끓이는 순서";

        SkillMatcher.MatchResult result = matcher.match(unrelatedPhrase);

        assertNull(result.skillId());
    }
}
