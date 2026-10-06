package com.specodyssey.service.discovery;

import com.specodyssey.service.discovery.JobDiscoveryScorer.MajorFit;
import com.specodyssey.util.LocalEmbedder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 전공 → 직무 계열 역산. 관련 요구사항: FR-38 ②(보유 스펙·전공을 임베딩으로 역산), TD-1
 *
 * 전공명과 계열별 기준 문장을 로컬 임베딩(ko-sroberta-multitask)으로 바꿔 코사인 유사도를 잰다.
 * 결과는 JobDiscoveryScorer가 설문·스펙 점수와 섞는다.
 *
 * ── 왜 유사도를 그대로 쓰지 않나 (2026-10-06 실측) ──────────────
 *   통계학과 → 데이터 0.60, 정보보호학과 → 보안 0.67 처럼 잘 맞는 전공도 있지만
 *   컴퓨터공학과는 전 계열이 0.37~0.48로 비슷하고, 국어국문학과는 전부 0.1~0.3이다.
 *   전공마다 값의 범위가 달라서 절대값 대신 같은 사람 안에서 계열끼리 상대 비교한다.
 *     전공 점수(계열) = (sim - 최저) / (최고 - 최저)                       → 0~1
 *     신뢰도         = 차이 신뢰도 × 절대 신뢰도
 *       차이 신뢰도   = (최고 - 평균 - SPREAD_FLOOR) / (SPREAD_FULL - SPREAD_FLOOR)     → 0~1로 자름
 *       절대 신뢰도   = (최고 - TOP_SIM_FLOOR) / (TOP_SIM_FULL - TOP_SIM_FLOOR)         → 0~1로 자름
 *   계열 사이 차이가 작은 전공(컴공·산업공학 등)은 차이 신뢰도가, IT와 거리가 먼 전공(국문·체육 등)은
 *   절대 신뢰도가 0에 가까워져 추천에 거의 영향을 주지 않는다. 국문(최고 0.33, FRONTEND)이 상대값만으로는
 *   신뢰도 0.49가 나와서 절대 신뢰도를 곱하기로 했다. 국문 0.33과 수학 0.36(DATA, 맞는 신호)이 가까워
 *   딱 자르는 문턱 대신 곱하는 방식으로 약한 신호를 서서히 줄인다.
 *
 * ── 기준 문장 ──────────────────────────────────────────────
 *   직무명만 쓰면 경영학과 → 기획처럼 약한 연결을 못 잡아서, 계열마다 직무명 + 하는 일 문장을 두고
 *   벡터 평균을 계열 벡터로 쓴다. 계열 벡터는 처음 한 번만 계산해 메모리에 둔다.
 *
 * 모델은 EmbeddingMatcher와 따로 로드한다(약 440MB 추가) — 남의 파일을 건드리지 않기 위한 선택.
 * EmbeddingMatcher가 모델을 공유하도록 바뀌면 그쪽 인스턴스를 쓰도록 합치면 된다.
 * 모델이 없거나 전공이 비었거나 계산이 실패하면 {@link MajorFit#none()} — 전공 없이 기존대로 추천한다(FR-111).
 */
public class MajorAffinity {

    private static final Logger LOG = Logger.getLogger(MajorAffinity.class.getName());

    // ---- 튜닝 값 ----
    /** 최고 유사도가 평균보다 이만큼은 높아야 전공을 반영하기 시작한다 */
    static final double SPREAD_FLOOR = 0.05;
    /** 최고 - 평균이 이 이상이면 전공을 최대 비중으로 반영한다 */
    static final double SPREAD_FULL = 0.15;
    /** 최고 유사도가 이 이하면 IT 계열 어디와도 거리가 먼 전공으로 보고 반영하지 않는다 */
    static final double TOP_SIM_FLOOR = 0.30;
    /** 최고 유사도가 이 이상이면 절대 신뢰도 최대 */
    static final double TOP_SIM_FULL = 0.45;
    /** 전공별 벡터 캐시 크기 상한 — 전공 종류는 많지 않지만 자유 입력이라 끝없이 쌓이지 않게 */
    private static final int MAJOR_CACHE_MAX = 500;

    static final Map<String, List<String>> CATEGORY_ANCHORS = anchors();

    private static Map<String, List<String>> anchors() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("BACKEND", List.of("백엔드 개발자", "서버와 API를 개발하고 데이터베이스를 설계하는 백엔드 개발"));
        m.put("FRONTEND", List.of("프론트엔드 개발자", "웹 화면과 사용자 인터페이스를 디자인하고 구현하는 프론트엔드 개발"));
        m.put("DATA", List.of("데이터 분석가", "통계와 데이터 분석, 머신러닝으로 인사이트를 찾는 데이터 직무"));
        m.put("DEVOPS", List.of("클라우드 엔지니어", "서버 인프라, 클라우드, 네트워크를 운영하고 배포를 자동화하는 직무"));
        m.put("SECURITY", List.of("보안 엔지니어", "해킹 방어, 암호와 정보보호로 시스템을 지키는 보안 직무"));
        m.put("PM", List.of("서비스 기획자", "경영과 사업 전략을 바탕으로 서비스를 기획하고 일정과 사람을 관리하는 직무"));
        return m;
    }

    // 모델 로딩이 수 초 걸려서 프로세스당 한 번만 시도한다. 실패하면 다시 시도하지 않는다.
    private static volatile LocalEmbedder sharedEmbedder;
    private static volatile boolean embedderUnavailable;
    private static volatile Map<String, float[]> categoryVectors;
    private static final Map<String, float[]> MAJOR_VECTORS = new ConcurrentHashMap<>();

    /** 전공명으로 계열별 적합도를 구한다. 어떤 경우에도 예외를 던지지 않는다. */
    public MajorFit score(String major) {
        if (major == null || major.isBlank()) {
            return MajorFit.none();
        }
        String key = major.trim();
        try {
            LocalEmbedder embedder = embedder();
            if (embedder == null) {
                return MajorFit.none();
            }
            Map<String, float[]> categories = categoryVectors(embedder);
            float[] majorVector = MAJOR_VECTORS.get(key);
            if (majorVector == null) {
                majorVector = embedder.embed(key);
                if (MAJOR_VECTORS.size() < MAJOR_CACHE_MAX) {
                    MAJOR_VECTORS.put(key, majorVector);
                }
            }
            Map<String, Double> similarity = new LinkedHashMap<>();
            for (Map.Entry<String, float[]> c : categories.entrySet()) {
                similarity.put(c.getKey(), LocalEmbedder.cosine(majorVector, c.getValue()));
            }
            return normalize(key, similarity);
        } catch (Exception | LinkageError e) {
            // LinkageError: 네이티브 라이브러리(ONNX·토크나이저) 로딩 실패는 Exception이 아니라 Error로 온다.
            // 예) Tomcat을 끄지 않고 WAR만 바꾸면 "already loaded in another classloader" — 이때도 화면은 멈추면 안 된다(FR-111)
            LOG.log(Level.WARNING, "전공 임베딩 계산 실패 — 전공 없이 추천합니다: " + key, e);
            return MajorFit.none();
        }
    }

    /** 계열별 원 유사도 → 상대 점수 + 신뢰도. 모델 없이 테스트할 수 있게 분리한 순수 계산. */
    static MajorFit normalize(String major, Map<String, Double> similarity) {
        if (similarity.isEmpty()) {
            return MajorFit.none();
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        for (double s : similarity.values()) {
            min = Math.min(min, s);
            max = Math.max(max, s);
            sum += s;
        }
        double mean = sum / similarity.size();
        double range = max - min;
        if (range <= 0) {
            return MajorFit.none();
        }
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, Double> e : similarity.entrySet()) {
            scores.put(e.getKey(), (e.getValue() - min) / range);
        }
        double spreadConfidence = clamp01((max - mean - SPREAD_FLOOR) / (SPREAD_FULL - SPREAD_FLOOR));
        double absoluteConfidence = clamp01((max - TOP_SIM_FLOOR) / (TOP_SIM_FULL - TOP_SIM_FLOOR));
        double confidence = spreadConfidence * absoluteConfidence;
        return new MajorFit(major, scores, confidence);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static Map<String, float[]> categoryVectors(LocalEmbedder embedder) throws Exception {
        Map<String, float[]> existing = categoryVectors;
        if (existing != null) {
            return existing;
        }
        synchronized (MajorAffinity.class) {
            if (categoryVectors == null) {
                Map<String, float[]> built = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> e : CATEGORY_ANCHORS.entrySet()) {
                    float[] mean = new float[LocalEmbedder.DIMENSION];
                    for (String sentence : e.getValue()) {
                        float[] v = embedder.embed(sentence);
                        for (int d = 0; d < mean.length; d++) {
                            mean[d] += v[d] / e.getValue().size();
                        }
                    }
                    built.put(e.getKey(), mean);
                }
                categoryVectors = built;
            }
            return categoryVectors;
        }
    }

    private static LocalEmbedder embedder() {
        if (embedderUnavailable) {
            return null;
        }
        LocalEmbedder existing = sharedEmbedder;
        if (existing != null) {
            return existing;
        }
        synchronized (MajorAffinity.class) {
            if (embedderUnavailable) {
                return null;
            }
            if (sharedEmbedder == null) {
                try {
                    sharedEmbedder = LocalEmbedder.fromConfig();
                } catch (Exception | LinkageError e) {
                    LOG.log(Level.INFO, "임베딩 모델을 불러오지 못해 전공 역산을 건너뜁니다 — "
                            + "설문·보유 기술만으로 추천합니다.", e);
                    embedderUnavailable = true;
                    return null;
                }
            }
            return sharedEmbedder;
        }
    }
}
