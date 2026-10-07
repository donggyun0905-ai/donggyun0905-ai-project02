package com.specodyssey.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 요구사항(FR) ↔ 코드·테스트 연결표를 소스에서 긁어모은다 (2026-10-07).
 *
 * 배포된 WAR에는 src·docs가 없어서 화면이 실행 중에 소스를 읽을 수 없다 — 그래서 빌드(테스트)에서
 * 이 스캐너가 만든 결과를 src/main/resources/feature-coverage.json에 넣어 두고, 관리자 화면은
 * 그 리소스만 읽는다. 소스가 바뀌면 FeatureCoverageTest가 달라진 것을 알려 주므로 낡지 않는다.
 *
 * FR 참조는 코드 주석에 여러 형태로 적혀 있어 모두 받아들인다:
 *   "관련 요구사항: FR-13"  ·  "FR-41~44"(범위)  ·  "FR-71 · 72"(앞의 FR- 생략)  ·  "FR-23·25"
 */
final class FeatureCoverageScanner {

    private static final Path REQUIREMENTS = Paths.get("docs/requirements.md");
    private static final Path MAIN = Paths.get("src/main/java");
    private static final Path TEST = Paths.get("src/test/java");
    private static final Path WEBAPP = Paths.get("src/main/webapp/WEB-INF/views");

    /** `| FR-11 | 제목 | [필수] |` 형태의 표 줄 */
    private static final Pattern FR_ROW = Pattern.compile("^\\|\\s*(FR-\\d+)\\s*\\|([^|]*)\\|([^|]*)\\|");
    /** `### 2-1. 회원 / 인증` */
    private static final Pattern AREA = Pattern.compile("^###\\s+(2-\\d+\\..*)$");
    /** 주석의 FR 참조 — FR-12, FR-41~44, FR-71 · 72, FR-23·25 */
    private static final Pattern FR_REF = Pattern.compile("FR-\\d+(\\s*[~·,]\\s*\\d+)*");

    private FeatureCoverageScanner() {
    }

    record Feature(String id, String title, String priority, String area,
                   List<String> controllers, List<String> services, List<String> daos,
                   List<String> views, List<String> tests) {
        boolean implemented() {
            return !controllers.isEmpty() || !services.isEmpty() || !daos.isEmpty() || !views.isEmpty();
        }
    }

    /** 요구사항 표의 순서를 그대로 지킨 FR 목록 */
    static List<Feature> scan() throws IOException {
        Map<String, String[]> declared = readRequirements(); // FR-id → {제목, 중요도, 영역}
        Map<String, Set<String>> byFile = new LinkedHashMap<>();
        collect(MAIN, ".java", byFile);
        collect(WEBAPP, ".jsp", byFile);
        collect(WEBAPP, ".jspf", byFile);
        Map<String, Set<String>> byTest = new LinkedHashMap<>();
        collect(TEST, ".java", byTest);

        List<Feature> features = new ArrayList<>();
        for (Map.Entry<String, String[]> e : declared.entrySet()) {
            String id = e.getKey();
            features.add(new Feature(id, e.getValue()[0], e.getValue()[1], e.getValue()[2],
                    filesFor(byFile, id, "controller"),
                    filesFor(byFile, id, "service"),
                    filesFor(byFile, id, "dao"),
                    viewsFor(byFile, id),
                    tests(byTest, id, byFile)));
        }
        return features;
    }

    private static Map<String, String[]> readRequirements() throws IOException {
        Map<String, String[]> declared = new LinkedHashMap<>();
        String area = "";
        for (String line : Files.readAllLines(REQUIREMENTS)) {
            Matcher areaMatcher = AREA.matcher(line.trim());
            if (areaMatcher.find()) {
                area = areaMatcher.group(1).trim();
                continue;
            }
            Matcher row = FR_ROW.matcher(line.trim());
            if (row.find()) {
                declared.put(row.group(1), new String[]{clean(row.group(2)), clean(row.group(3)), area});
            }
        }
        return declared;
    }

    // 표의 **강조**와 각주는 화면에서 읽기 어려워 떼어 낸다
    private static String clean(String text) {
        return text.replace("**", "").replaceAll("\\s+", " ").trim();
    }

    /** 파일을 훑어 "FR-id → 그 FR을 적어 둔 파일 경로"로 뒤집는다 */
    private static void collect(Path root, String suffix, Map<String, Set<String>> target) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(suffix))
                    // 이 스캐너와 생성 테스트는 FR 번호를 예시로 담고 있어 모든 FR에 붙는다 — 제외한다
                    .filter(p -> !p.getFileName().toString().startsWith("FeatureCoverage"))
                    .toList()) {
                String text = Files.readString(file);
                String rel = root.relativize(file).toString().replace('\\', '/');
                for (String id : extractFrIds(text)) {
                    target.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(rel);
                }
            }
        }
    }

    /** 주석에 적힌 FR 참조를 모두 FR-번호로 펼친다. FR-41~44 → 41,42,43,44 */
    static Set<String> extractFrIds(String text) {
        Set<String> ids = new TreeSet<>();
        Matcher m = FR_REF.matcher(text);
        while (m.find()) {
            String ref = m.group().replaceAll("\\s+", "");
            String[] parts = ref.substring("FR-".length()).split("[·,]");
            for (String part : parts) {
                if (part.contains("~")) {
                    String[] range = part.split("~");
                    int from = Integer.parseInt(range[0]);
                    int to = Integer.parseInt(range[1]);
                    // "FR-45~48"처럼 끝 번호가 시작보다 작게 적힌 경우는 범위로 보지 않는다
                    if (to >= from && to - from <= 20) {
                        for (int n = from; n <= to; n++) {
                            ids.add("FR-" + n);
                        }
                    } else {
                        ids.add("FR-" + from);
                    }
                } else if (!part.isBlank()) {
                    ids.add("FR-" + part);
                }
            }
        }
        return ids;
    }

    private static List<String> filesFor(Map<String, Set<String>> byFile, String id, String packageName) {
        return byFile.getOrDefault(id, Set.of()).stream()
                .filter(p -> p.contains("/" + packageName + "/"))
                .map(FeatureCoverageScanner::simpleName)
                .sorted()
                .toList();
    }

    private static List<String> viewsFor(Map<String, Set<String>> byFile, String id) {
        return byFile.getOrDefault(id, Set.of()).stream()
                .filter(p -> p.endsWith(".jsp") || p.endsWith(".jspf"))
                .map(FeatureCoverageScanner::simpleName)
                .sorted()
                .toList();
    }

    /**
     * 그 FR의 테스트 — 두 가지를 합친다.
     *  ① 테스트 주석에 FR 번호를 직접 적어 둔 것
     *  ② 그 FR에 연결된 클래스의 이름을 딴 테스트 (ProfileService.java → ProfileServiceTest)
     * 테스트는 보통 FR 번호 대신 대상 클래스 이름을 쓰기 때문에 ②가 없으면 대부분 빈칸이 된다.
     */
    private static List<String> tests(Map<String, Set<String>> byTest, String id,
                                      Map<String, Set<String>> byFile) {
        Set<String> names = new TreeSet<>(byTest.getOrDefault(id, Set.of()).stream()
                .map(FeatureCoverageScanner::simpleName).toList());
        for (String path : byFile.getOrDefault(id, Set.of())) {
            if (!path.endsWith(".java")) {
                continue;
            }
            String base = simpleName(path).replace(".java", "");
            for (String candidate : ALL_TEST_NAMES) {
                // ProfileService → ProfileServiceTest, ProfileServiceRetryTest 등 그 클래스를 다루는 테스트
                if (candidate.startsWith(base) && candidate.endsWith("Test.java")) {
                    names.add(candidate);
                }
            }
        }
        return List.copyOf(names);
    }

    /** 테스트 파일 이름 전체 — 이름으로 짝을 찾을 때 쓴다 */
    private static final Set<String> ALL_TEST_NAMES = allTestNames();

    private static Set<String> allTestNames() {
        if (!Files.isDirectory(TEST)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.walk(TEST)) {
            return files.filter(p -> p.toString().endsWith("Test.java"))
                    .map(p -> p.getFileName().toString())
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new IllegalStateException("테스트 목록을 읽지 못했습니다", e);
        }
    }

    private static String simpleName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
