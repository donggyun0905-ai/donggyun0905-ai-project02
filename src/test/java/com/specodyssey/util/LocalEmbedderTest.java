package com.specodyssey.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 모델로 도는 통합테스트. 모델 폴더(EMBEDDING_MODEL_DIR)가 없는 PC에서는 건너뛴다 —
 * 모델 파일은 커밋하지 않으므로 팀원 PC에서 빌드가 깨지지 않게 하기 위함.
 */
class LocalEmbedderTest {

    private static LocalEmbedder embedder;

    @BeforeAll
    static void setUp() throws Exception {
        String dir = AppConfig.get(LocalEmbedder.MODEL_DIR_KEY);
        assumeTrue(dir != null && Files.isRegularFile(Path.of(dir, "model.onnx")),
                "EMBEDDING_MODEL_DIR에 모델이 없어 건너뜀");
        embedder = LocalEmbedder.fromConfig();
    }

    @AfterAll
    static void tearDown() {
        if (embedder != null) {
            embedder.close();
        }
    }

    @Test
    void 문장_하나를_768차원_벡터로_바꾼다() throws Exception {
        float[] v = embedder.embed("자바 스프링 백엔드 개발");

        assertEquals(LocalEmbedder.DIMENSION, v.length);
        for (float x : v) {
            assertTrue(Float.isFinite(x), "벡터에 NaN/Infinity가 있으면 안 된다");
        }
    }

    @Test
    void 같은_입력은_같은_벡터를_돌려준다() throws Exception {
        assertArrayEquals(embedder.embed("Docker"), embedder.embed("  Docker  "));
    }

    @Test
    void 뜻이_비슷한_문장이_더_가깝다() throws Exception {
        float[] backend = embedder.embed("자바 백엔드 개발");
        float[] similar = embedder.embed("Java 서버 개발");
        float[] different = embedder.embed("포토샵 이미지 편집");

        double near = LocalEmbedder.cosine(backend, similar);
        double far = LocalEmbedder.cosine(backend, different);
        System.out.printf("[임베딩] 자바 백엔드 개발 ↔ Java 서버 개발 = %.4f, ↔ 포토샵 이미지 편집 = %.4f%n", near, far);

        assertTrue(near > far, "비슷한 문장의 유사도(" + near + ")가 다른 문장(" + far + ")보다 커야 한다");
    }

    @Test
    void 빈_입력은_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> embedder.embed(null));
        assertThrows(IllegalArgumentException.class, () -> embedder.embed("   "));
    }

    @Test
    void cosine_기본_성질() {
        float[] a = {1, 0};
        float[] b = {0, 1};
        assertEquals(1.0, LocalEmbedder.cosine(a, a), 1e-9);
        assertEquals(0.0, LocalEmbedder.cosine(a, b), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> LocalEmbedder.cosine(a, new float[3]));
    }
}
