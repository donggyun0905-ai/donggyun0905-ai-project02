package com.specodyssey.service;

import com.specodyssey.util.AppConfig;
import com.specodyssey.util.LocalEmbedder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 전공 ↔ 직무 계열 (FR-38 ②, 2026-10-06). 규칙은 가짜 값으로, 실제 판단은 로컬 모델이 있을 때만 확인한다. DB 불필요. */
class MajorAffinityTest {

    @Test
    void 가장_가까운_계열이_1_가장_먼_계열이_0이_된다() {
        Map<String, Double> sims = new LinkedHashMap<>();
        sims.put("DATA", 0.62);
        sims.put("PM", 0.44);
        sims.put("SECURITY", 0.30);

        Map<String, Double> scores = MajorAffinity.normalize(sims);

        assertEquals(1.0, scores.get("DATA"), 1e-9);
        assertEquals(0.0, scores.get("SECURITY"), 1e-9);
        assertEquals((0.44 - 0.30) / (0.62 - 0.30), scores.get("PM"), 1e-9);
    }

    @Test
    void 어느_계열과도_뚜렷하지_않으면_반영하지_않는다() {
        assertTrue(MajorAffinity.normalize(Map.of("DATA", 0.33, "PM", 0.20)).isEmpty(), "최고 유사도가 0.40 미만");
        assertTrue(MajorAffinity.normalize(Map.of("DATA", 0.5, "PM", 0.5)).isEmpty(), "모든 계열이 같으면 구분할 수 없다");
    }

    @Test
    void 전공이_비었거나_모델이_없으면_빈_결과() {
        MajorAffinity noModel = new MajorAffinity(text -> null);

        assertTrue(noModel.scores("통계학과").isEmpty());
        assertTrue(noModel.scores("  ").isEmpty());
        assertTrue(noModel.scores(null).isEmpty());
    }

    @Test
    void 임베딩_계산이_실패해도_예외_없이_빈_결과() {
        MajorAffinity broken = new MajorAffinity(text -> {
            throw new IllegalStateException("모델 오류");
        });

        assertTrue(broken.scores("통계학과").isEmpty());
    }

    @Test
    void 실제_모델로_전공이_맞는_계열에_가장_가깝게_나온다() {
        String dir = AppConfig.get(LocalEmbedder.MODEL_DIR_KEY);
        assumeTrue(dir != null && Files.isRegularFile(Path.of(dir, "model.onnx")), "EMBEDDING_MODEL_DIR에 모델이 없어 건너뜀");
        MajorAffinity affinity = new MajorAffinity();

        assertEquals("DATA", top(affinity.scores("통계학과")));
        assertEquals("SECURITY", top(affinity.scores("정보보호학과")));
        assertEquals("FRONTEND", top(affinity.scores("시각디자인학과")));
        assertEquals("BACKEND", top(affinity.scores("소프트웨어학과")));
        assertTrue(affinity.scores("국어국문학과").isEmpty(), "IT 계열과 뚜렷하지 않은 전공은 반영하지 않는다");
    }

    private static String top(Map<String, Double> scores) {
        return scores.entrySet().stream().filter(e -> e.getValue() == 1.0).map(Map.Entry::getKey).findFirst().orElse("없음");
    }
}
