package com.specodyssey.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 새 POST 폼을 만들고 CSRF 토큰 hidden 입력을 빠뜨리면 그 폼은 항상 403이 난다 — 사람이 일일이 기억하는 대신
 * 모든 JSP를 훑어서 폼 여는 태그 바로 뒤에 토큰 입력이 있는지, 자바스크립트 POST가 헤더를 보내는지 검사한다.
 */
class CsrfJspCoverageTest {

    private static final Path WEBAPP = Paths.get("src/main/webapp");
    private static final Pattern FORM_OPEN = Pattern.compile("<form\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final String TOKEN_INPUT = "name=\"_csrf\" value=\"${csrfToken}\"";

    private static List<Path> jsps() throws IOException {
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            List<Path> result = new ArrayList<>();
            files.filter(p -> p.toString().endsWith(".jsp") || p.toString().endsWith(".jspf")).forEach(result::add);
            return result;
        }
    }

    @Test
    void 모든_POST_폼은_여는_태그_바로_뒤에_CSRF_토큰을_담는다() throws IOException {
        List<String> missing = new ArrayList<>();
        int forms = 0;
        for (Path jsp : jsps()) {
            String text = Files.readString(jsp);
            Matcher m = FORM_OPEN.matcher(text);
            while (m.find()) {
                forms++;
                String tag = m.group();
                assertTrue(tag.toLowerCase().contains("method=\"post\""),
                        jsp + " — GET 폼이 생겼다면 이 검사를 GET 예외로 바꿔야 한다: " + tag);
                String after = text.substring(m.end(), Math.min(text.length(), m.end() + 120));
                if (!after.contains(TOKEN_INPUT)) {
                    missing.add(jsp.getFileName() + " : " + tag.replaceAll("\\s+", " "));
                }
            }
        }
        assertTrue(forms >= 50, "폼을 못 찾았다(경로 확인): " + forms);
        assertEquals(List.of(), missing, "CSRF 토큰이 없는 폼");
    }

    @Test
    void 자바스크립트로_보내는_POST는_토큰_헤더를_함께_보낸다() throws IOException {
        for (Path jsp : jsps()) {
            String text = Files.readString(jsp);
            if (text.contains("fetch(") || text.contains("XMLHttpRequest")) {
                assertTrue(text.contains("X-CSRF-Token") && text.contains("${csrfToken}"),
                        jsp + " — fetch/XHR POST에 X-CSRF-Token 헤더가 없다");
            }
        }
    }
}
