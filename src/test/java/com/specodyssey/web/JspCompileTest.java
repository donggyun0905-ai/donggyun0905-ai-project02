package com.specodyssey.web;

import org.apache.jasper.JspC;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 모든 JSP를 Tomcat 11의 Jasper로 미리 컴파일해 본다 (2026-10-07).
 *
 * JSP는 톰캣이 그 화면을 처음 요청받는 순간 컴파일한다. 그래서 EL 문법이 틀려도 빌드와 테스트는 멀쩡히
 * 통과하고, 그 화면을 실제로 열어 본 사람만 500을 본다. 팀원 대부분이 Tomcat 10.1(EL 5.0)을 쓰고
 * 한 명이 11(EL 6.0)을 써서, 10.1에서 잘 되던 표현식이 11에서만 화면을 죽인 일이 있었다:
 *   ${view.activity.empty} — empty는 EL 예약어라 11에서는 속성 접근이 아니라 파싱 오류가 된다.
 *   (면접관 "이력 보기" 화면 전체가 500. 10.1 쓰는 팀원 눈에는 아무 문제가 없었다.)
 *
 * tomcat-jasper는 pom에서 11.0.25로 고정해 뒀다 — 10.1을 쓰는 사람이 돌려도 11 기준으로 검사된다.
 *
 * 검사하는 것: EL 파싱, 커스텀 태그 속성(필수 속성 누락·rtexprvalue), taglib URI, JSP 문법.
 * 검사하지 못하는 것: 런타임 값. ${x.noSuchProp}는 여기서 안 걸린다(RecordElCoverageTest가 본다).
 */
class JspCompileTest {

    private static final Path WEBAPP = Paths.get("src/main/webapp");

    @Test
    void 모든_JSP가_톰캣11_Jasper로_컴파일된다() throws IOException {
        int screens = countJsps();
        assertTrue(screens > 40, "JSP를 못 찾았다(경로 확인): " + screens); // 2026-10-07 기준 45개

        Path outDir = Files.createTempDirectory("jspc-");
        JspC jspc = new JspC();
        jspc.setUriroot(WEBAPP.toAbsolutePath().toString()); // 지정하지 않으면 webapp 전체를 훑는다
        jspc.setOutputDir(outDir.toString());
        // 자바 소스 생성까지만 — EL·태그 검증은 생성 단계에서 끝나고 javac까지 돌릴 이유는 없다
        jspc.setCompile(false);
        jspc.setFailOnError(true);
        // JSTL 같은 TLD는 jar 안에 있다. src/main/webapp에는 WEB-INF/lib가 없으므로(빌드 때 채워진다)
        // 테스트 클래스패스를 넘겨 TldScanner가 거기서 TLD를 찾게 한다.
        jspc.setClassPath(System.getProperty("java.class.path"));

        try {
            jspc.execute();
        } catch (Exception e) {
            fail("JSP가 Tomcat 11에서 컴파일되지 않는다 — 그 화면은 열면 500이다:\n" + messageChain(e));
        } finally {
            deleteRecursively(outDir);
        }
    }

    /** JasperException은 원인이 중첩돼 있어 끝까지 따라가야 어느 줄이 문제인지 보인다 */
    private static String messageChain(Throwable e) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = e; t != null && t != t.getCause(); t = t.getCause()) {
            text.append(t.getMessage() == null ? t.toString() : t.getMessage()).append('\n');
        }
        return text.toString();
    }

    private static int countJsps() throws IOException {
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            return (int) files.filter(p -> p.toString().endsWith(".jsp")).count();
        }
    }

    private static void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // 임시 폴더 정리 실패는 테스트 결과와 무관하다
        }
    }
}
