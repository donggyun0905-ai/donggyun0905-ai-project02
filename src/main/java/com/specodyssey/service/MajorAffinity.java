package com.specodyssey.service;

import com.specodyssey.util.LocalEmbedder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 전공 ↔ 직무 계열 가까움 (FR-38 ② "보유 스펙·전공을 임베딩으로 역산", 2026-10-06).
 *
 * 전공(USERS.major, 자유 입력)과 6개 계열 설명 문장의 임베딩 유사도를 구해, 가장 가까운 계열 1 ~ 가장 먼 계열 0으로
 * 다시 매긴다. 직무 테이블엔 설명이 없어 계열 단위로 비교한다.
 * 가장 높은 유사도가 MIN_TOP_SIMILARITY보다 낮으면(국어국문·전자공학처럼 어느 계열과도 뚜렷하지 않은 전공) 빈 결과를
 * 돌려줘 반영하지 않는다 — 약한 신호로 순위를 흔들지 않게. 모델이 없거나 오류가 나도 빈 결과다.
 *
 * 2026-10-06 실험(전공 18개): 통계·응용통계·수학 → DATA, 정보보호·사이버보안 → SECURITY, 디자인 → FRONTEND,
 * 소프트웨어·컴퓨터공학 → BACKEND, 경영 → PM. 국어국문·전자공학·경제·심리는 모든 계열 0.36 이하라 반영 안 함.
 */
public class MajorAffinity {

    private static final Logger LOG = Logger.getLogger(MajorAffinity.class.getName());

    static final double MIN_TOP_SIMILARITY = 0.40;

    // 계열을 대표하는 설명 문장 — 계열 이름만으로는 임베딩이 뜻을 잘 못 잡아 하는 일을 풀어 쓴다
    static final Map<String, String> CATEGORY_ANCHORS;
    static {
        Map<String, String> anchors = new LinkedHashMap<>();
        anchors.put("BACKEND", "서버 개발, 백엔드, 데이터베이스와 API를 만드는 소프트웨어 개발");
        anchors.put("FRONTEND", "웹 화면 개발, 프론트엔드, 사용자 인터페이스와 디자인을 구현");
        anchors.put("DATA", "데이터 분석, 통계, 머신러닝과 데이터 엔지니어링");
        anchors.put("DEVOPS", "인프라 운영, 클라우드, 네트워크와 시스템 자동화");
        anchors.put("SECURITY", "정보보안, 해킹 방어, 보안 취약점 분석");
        anchors.put("PM", "서비스 기획, 프로젝트 관리, 비즈니스와 사용자 조사");
        CATEGORY_ANCHORS = Collections.unmodifiableMap(anchors);
    }

    private final Function<String, float[]> embed;
    private volatile Map<String, float[]> anchorVectors;

    /** 직무 발굴이 쓰는 기본값 — EmbeddingMatcher와 같은 로컬 모델 */
    public MajorAffinity() {
        this(text -> {
            LocalEmbedder embedder = EmbeddingMatcher.embedder();
            if (embedder == null) {
                return null;
            }
            try {
                return embedder.embed(text);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // 테스트에서 가짜 임베딩을 넣는다. null을 돌려주면 "모델 없음"으로 본다
    MajorAffinity(Function<String, float[]> embed) {
        this.embed = embed;
    }

    /** @return 계열 → 0~1 (가장 가까운 계열이 1). 반영하지 않을 때는 빈 Map */
    public Map<String, Double> scores(String major) {
        if (major == null || major.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, float[]> anchors = anchors();
            float[] vector = anchors == null ? null : embed.apply(major.trim());
            if (vector == null) {
                return Map.of();
            }
            Map<String, Double> similarities = new LinkedHashMap<>();
            anchors.forEach((category, anchor) -> similarities.put(category, LocalEmbedder.cosine(vector, anchor)));
            return normalize(similarities);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "전공 임베딩 계산 실패 — 전공 없이 추천합니다", e);
            return Map.of();
        }
    }

    // 가장 높은 유사도가 기준 미만이면 반영하지 않고, 아니면 최소 0 ~ 최대 1로 다시 매긴다
    static Map<String, Double> normalize(Map<String, Double> similarities) {
        if (similarities.isEmpty()) {
            return Map.of();
        }
        double max = similarities.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = similarities.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
        if (max < MIN_TOP_SIMILARITY || max - min <= 0) {
            return Map.of();
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        similarities.forEach((category, s) -> scores.put(category, (s - min) / (max - min)));
        return scores;
    }

    private Map<String, float[]> anchors() {
        Map<String, float[]> cached = anchorVectors;
        if (cached != null) {
            return cached;
        }
        Map<String, float[]> built = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : CATEGORY_ANCHORS.entrySet()) {
            float[] vector = embed.apply(entry.getValue());
            if (vector == null) {
                return null; // 모델 없음 — 다음에 다시 시도한다
            }
            built.put(entry.getKey(), vector);
        }
        anchorVectors = built;
        return built;
    }
}
