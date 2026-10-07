package com.specodyssey.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기능 설계서·테스트 목록 (2026-10-07 사용자 요청) — 요구사항(FR)과 실제 코드·테스트의 연결을 보여 준다.
 *
 * 내용은 손으로 쓰지 않는다. 빌드(FeatureCoverageTest)가 소스를 훑어
 * src/main/resources/feature-coverage.json을 만들고 여기서 그 리소스만 읽는다 —
 * 배포된 WAR에는 src·docs가 없어 실행 중에 소스를 읽을 수 없고, 손으로 쓴 문서는 코드보다 금방 낡기 때문이다.
 *
 * 연결 근거는 코드 주석의 "관련 요구사항: FR-xx"다. 테스트는 그 주석과, 연결된 클래스 이름을 딴
 * 테스트(ProfileService → ProfileServiceTest) 둘을 합쳐 찾는다.
 */
public class FeatureCoverageService {

    private static final String RESOURCE = "/feature-coverage.json";

    /** FR 한 줄. 구현 산출물이 하나도 없으면 미구현으로 본다. */
    public static class Feature {
        private String id;
        private String title;
        private String priority;
        private String area;
        private List<String> controllers = List.of();
        private List<String> services = List.of();
        private List<String> daos = List.of();
        private List<String> views = List.of();
        private List<String> tests = List.of();

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getPriority() {
            return priority;
        }

        public String getArea() {
            return area;
        }

        public List<String> getControllers() {
            return controllers;
        }

        public List<String> getServices() {
            return services;
        }

        public List<String> getDaos() {
            return daos;
        }

        public List<String> getViews() {
            return views;
        }

        public List<String> getTests() {
            return tests;
        }

        public boolean isImplemented() {
            return !controllers.isEmpty() || !services.isEmpty() || !daos.isEmpty() || !views.isEmpty();
        }

        public boolean isTested() {
            return !tests.isEmpty();
        }

        public int getTestCount() {
            return tests.size();
        }
    }

    /** 요약 숫자 — 화면 위쪽에 보여 준다. */
    public record Summary(int total, int implemented, int tested) {
        public int getTotal() {
            return total;
        }

        public int getImplemented() {
            return implemented;
        }

        public int getTested() {
            return tested;
        }

        public int getMissing() {
            return total - implemented;
        }

        public int getUntested() {
            return total - tested;
        }
    }

    // 리소스는 빌드 산출물이라 요청마다 다시 읽을 필요가 없다
    private static volatile List<Feature> cached;

    public List<Feature> all() {
        List<Feature> existing = cached;
        if (existing != null) {
            return existing;
        }
        synchronized (FeatureCoverageService.class) {
            if (cached == null) {
                cached = load();
            }
            return cached;
        }
    }

    private static List<Feature> load() {
        try (InputStream in = FeatureCoverageService.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                // 리소스를 못 찾아도 화면이 깨지지 않게 — 안내만 보여 준다
                return List.of();
            }
            List<Feature> features = new Gson().fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<List<Feature>>() { }.getType());
            return features == null ? List.of() : List.copyOf(features);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 요구사항 명세서의 영역(2-1 회원/인증 …) 순서를 그대로 지킨 묶음 */
    public Map<String, List<Feature>> byArea() {
        Map<String, List<Feature>> grouped = new LinkedHashMap<>();
        for (Feature feature : all()) {
            String area = feature.getArea() == null || feature.getArea().isBlank() ? "기타" : feature.getArea();
            grouped.computeIfAbsent(area, k -> new ArrayList<>()).add(feature);
        }
        return grouped;
    }

    public Summary summary() {
        List<Feature> features = all();
        return new Summary(features.size(),
                (int) features.stream().filter(Feature::isImplemented).count(),
                (int) features.stream().filter(Feature::isTested).count());
    }
}
