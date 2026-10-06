package com.specodyssey.service.discovery;

import com.specodyssey.service.discovery.JobDiscoveryScorer.MajorFit;
import com.specodyssey.util.AppConfig;
import com.specodyssey.util.LocalEmbedder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FR-38 ② 전공 역산. 정규화는 모델 없이 돌고, 실제 전공 → 계열 결과는 모델이 있는 PC에서만 돈다
 * (EMBEDDING_MODEL_DIR이 없으면 건너뜀 — LocalEmbedderTest와 같은 방식).
 */
class MajorAffinityTest {

    private final MajorAffinity affinity = new MajorAffinity();

    private static Map<String, Double> sims(double backend, double frontend, double data,
                                            double devops, double security, double pm) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("BACKEND", backend);
        m.put("FRONTEND", frontend);
        m.put("DATA", data);
        m.put("DEVOPS", devops);
        m.put("SECURITY", security);
        m.put("PM", pm);
        return m;
    }

    // ---- 정규화 (모델 없이) ----

    @Test
    void 가장_가까운_계열이_1_가장_먼_계열이_0() {
        MajorFit fit = MajorAffinity.normalize("통계학과", sims(0.32, 0.25, 0.60, 0.30, 0.30, 0.34));

        assertEquals(1.0, fit.scoreByCategory().get("DATA"), 1e-9);
        assertEquals(0.0, fit.scoreByCategory().get("FRONTEND"), 1e-9);
        assertEquals("통계학과", fit.major());
    }

    @Test
    void 한_계열이_뚜렷하면_신뢰도_최대() {
        // 실측 통계학과와 비슷한 분포 — 최고 - 평균 ≈ 0.25
        MajorFit fit = MajorAffinity.normalize("통계학과", sims(0.32, 0.25, 0.60, 0.30, 0.30, 0.34));

        assertEquals(1.0, fit.confidence(), 1e-9);
    }

    @Test
    void 계열_사이_차이가_작으면_신뢰도_0() {
        // 실측 컴퓨터공학과와 비슷한 분포 — 다 비슷하면 전공으로 방향을 정하지 않는다
        MajorFit fit = MajorAffinity.normalize("컴퓨터공학과", sims(0.44, 0.43, 0.38, 0.47, 0.45, 0.42));

        assertEquals(0.0, fit.confidence(), 1e-9);
    }

    @Test
    void 유사도가_전체적으로_낮으면_차이가_커도_약하게() {
        // 실측 국어국문학과와 비슷한 분포 — 상대 차이만 보면 FRONTEND로 쏠리지만 IT와 거리가 먼 전공
        MajorFit fit = MajorAffinity.normalize("국어국문학과", sims(0.25, 0.33, 0.22, 0.23, 0.24, 0.10));

        assertTrue(fit.confidence() < 0.2, "절대 유사도가 낮으면 신뢰도도 낮아야 한다: " + fit.confidence());
    }

    @Test
    void 전부_같은_값이면_반영하지_않는다() {
        MajorFit fit = MajorAffinity.normalize("무엇", sims(0.3, 0.3, 0.3, 0.3, 0.3, 0.3));

        assertEquals(0.0, fit.confidence());
        assertTrue(fit.scoreByCategory().isEmpty());
    }

    @Test
    void 전공이_비어있으면_none() {
        assertEquals(0.0, affinity.score(null).confidence());
        assertEquals(0.0, affinity.score("   ").confidence());
    }

    // ---- 실제 모델 ----

    private static void assumeModel() {
        String dir = AppConfig.get(LocalEmbedder.MODEL_DIR_KEY);
        assumeTrue(dir != null && Files.isRegularFile(Path.of(dir, "model.onnx")),
                "EMBEDDING_MODEL_DIR에 모델이 없어 건너뜀");
    }

    private static String top(MajorFit fit) {
        return fit.scoreByCategory().entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue)).orElseThrow().getKey();
    }

    @Test
    void 통계학과는_데이터() {
        assumeModel();
        MajorFit fit = affinity.score("통계학과");

        assertEquals("DATA", top(fit));
        assertTrue(fit.confidence() > 0.5, "통계학과는 데이터 쪽이 뚜렷해야 한다: " + fit);
    }

    @Test
    void 정보보호학과는_보안() {
        assumeModel();
        MajorFit fit = affinity.score("정보보호학과");

        assertEquals("SECURITY", top(fit));
        assertTrue(fit.confidence() > 0.5, "정보보호학과는 보안 쪽이 뚜렷해야 한다: " + fit);
    }

    @Test
    void 컴퓨터공학과는_한쪽으로_치우치지_않는다() {
        assumeModel();
        MajorFit fit = affinity.score("컴퓨터공학과");

        assertTrue(fit.confidence() < 0.5, "컴공은 전 계열에 두루 맞으므로 전공 비중이 작아야 한다: " + fit);
    }

    @Test
    void IT와_먼_전공은_거의_반영하지_않는다() {
        assumeModel();

        assertTrue(affinity.score("국어국문학과").confidence() < 0.2);
        assertTrue(affinity.score("체육학과").confidence() < 0.2);
    }

    @Test
    void 디자인_전공은_프론트엔드() {
        assumeModel();

        assertEquals("FRONTEND", top(affinity.score("시각디자인학과")));
    }
}
