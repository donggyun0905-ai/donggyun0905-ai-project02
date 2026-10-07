package com.specodyssey.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSP가 읽는 record의 "파생 값"에 x() 형태가 있는지 전수 검사한다 (2026-10-07).
 *
 * Tomcat 11의 RecordELResolver는 record에서 isX()/getX()를 찾지 않고 x()만 찾는다. 구성요소는 x()가
 * 자동으로 생기지만, 구성요소가 아닌 계산 값(isComparable·getMissing 같은 것)은 x()를 직접 둬야 한다.
 * 없으면 그 화면이 Tomcat 11에서만 500이 난다 — 10.1을 쓰는 팀원 환경에서는 멀쩡해 보인다.
 *
 * RecordElAccessTest는 검사할 record를 손으로 적어야 해서 새 record가 계속 빠져나갔다(2026-10-06
 * 인사이트 또래 비교, 2026-10-07 기능 설계서 요약 — 둘 다 실제로 화면이 죽었다). 그래서 목록 없이
 * 소스 전체를 훑는다.
 */
class RecordElCoverageTest {

    private static final Path MAIN = Paths.get("src/main/java");
    private static final Path WEBAPP = Paths.get("src/main/webapp");

    /** `record Name(...) {` — 중첩 record도 걸린다 */
    private static final Pattern RECORD = Pattern.compile("record\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{", Pattern.DOTALL);
    /** `public boolean isX()` · `public String getX()` — 인자 없는 공개 메서드만 */
    private static final Pattern NO_ARG_PUBLIC =
            Pattern.compile("public\\s+[\\w<>\\[\\],.\\s]+?\\s+(\\w+)\\s*\\(\\s*\\)");
    /** JSP의 `${...}` 한 덩이 */
    private static final Pattern EL_EXPRESSION = Pattern.compile("\\$\\{[^}]*\\}");
    /**
     * 그 안의 `.prop` 전부. 처음에는 표현식에서 첫 조각만 뽑았는데, 그러면 `${view.education.gpaText}`에서
     * education만 걸려 중첩 속성이 통째로 검사에서 빠졌다 — 그래서 모든 조각을 본다 (2026-10-07).
     */
    private static final Pattern EL_PROPERTY = Pattern.compile("\\.\\s*([A-Za-z_]\\w*)");

    /**
     * 확인된 오탐 — 이 검사는 타입을 모른 채 "속성 이름"만 보고 짝지으므로, 같은 이름의 속성을
     * 다른 객체에서 쓰는 화면이 있으면 걸린다. 여기 적은 것은 JSP가 그 record를 읽지 않음을 확인한 것이다.
     *   RoadmapRefresher.Result.changed — 서버 코드(RoadmapServlet.noticeRefresh)만 읽는다.
     *     화면의 ${rule.changed}는 record가 아닌 ScoringRuleAdminService.RuleView(class)다.
     */
    private static final Set<String> VERIFIED_NOT_READ_BY_JSP = Set.of("Result.changed");

    private record Finding(String recordName, String property, String only, String file) {
        @Override
        public String toString() {
            return recordName + "." + property + " (" + only + "()만 있음) — " + file;
        }
    }

    @Test
    void JSP가_읽는_record_파생값은_x_형태도_있다() throws IOException {
        Set<String> jspProperties = jspProperties();
        assertTrue(jspProperties.size() > 50, "JSP를 못 읽었다(경로 확인): " + jspProperties.size());

        List<Finding> findings = new ArrayList<>();
        for (Path file : javaFiles()) {
            String source = Files.readString(file);
            Matcher record = RECORD.matcher(source);
            while (record.find()) {
                String name = record.group(1);
                Set<String> components = components(record.group(2));
                String body = bodyOf(source, record.end() - 1);
                Set<String> methods = noArgMethods(body);
                for (String method : methods) {
                    String property = propertyOf(method);
                    if (property == null || components.contains(property) || methods.contains(property)) {
                        continue; // 구성요소이거나 x() 형태가 이미 있다
                    }
                    if (jspProperties.contains(property)
                            && !VERIFIED_NOT_READ_BY_JSP.contains(name + "." + property)) {
                        findings.add(new Finding(name, property, method, MAIN.relativize(file).toString()));
                    }
                }
            }
        }
        assertEquals(List.of(), findings,
                "Tomcat 11에서 500이 난다 — record에 x() 형태를 추가하세요 (예: isEmpty()면 empty()도)");
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static Set<String> jspProperties() throws IOException {
        Set<String> properties = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".jsp") || p.toString().endsWith(".jspf"))
                    .toList()) {
                Matcher expression = EL_EXPRESSION.matcher(Files.readString(file));
                while (expression.find()) {
                    Matcher property = EL_PROPERTY.matcher(expression.group());
                    while (property.find()) {
                        properties.add(property.group(1));
                    }
                }
            }
        }
        return properties;
    }

    private static Set<String> components(String params) {
        Set<String> names = new HashSet<>();
        for (String part : params.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                String[] words = trimmed.split("\\s+");
                names.add(words[words.length - 1]);
            }
        }
        return names;
    }

    /** 여는 중괄호 위치부터 균형을 맞춰 record 본문만 떼어 낸다 */
    private static String bodyOf(String source, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }
        return "";
    }

    private static Set<String> noArgMethods(String body) {
        Set<String> methods = new LinkedHashSet<>();
        Matcher m = NO_ARG_PUBLIC.matcher(body);
        while (m.find()) {
            methods.add(m.group(1));
        }
        return methods;
    }

    /** isComparable → comparable, getMissing → missing. 접두사가 없으면 null */
    private static String propertyOf(String method) {
        if (method.startsWith("is") && method.length() > 2 && Character.isUpperCase(method.charAt(2))) {
            return Character.toLowerCase(method.charAt(2)) + method.substring(3);
        }
        if (method.startsWith("get") && method.length() > 3 && Character.isUpperCase(method.charAt(3))) {
            return Character.toLowerCase(method.charAt(3)) + method.substring(4);
        }
        return null;
    }
}
