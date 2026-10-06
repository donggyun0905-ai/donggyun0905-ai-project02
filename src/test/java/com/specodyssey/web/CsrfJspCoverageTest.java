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
    private static final String TOKEN_INPUT = "name=\"_csrf\" value=\"${csrfToken}\"";

    private static List<Path> jsps() throws IOException {
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            List<Path> result = new ArrayList<>();
            files.filter(p -> p.toString().endsWith(".jsp") || p.toString().endsWith(".jspf")).forEach(result::add);
            return result;
        }
    }

    /**
     * 폼 여는 태그의 끝(>)을 따옴표를 따져서 찾는다 — action="<c:url value='/x' />"처럼 속성 값 안에 >가 들어 있어도
     * 거기서 끊지 않는다. (예전에 토큰 입력을 속성 값 안에 끼워 넣어 제출 버튼이 안 먹고 화면에 코드가 보이던 사고가 있었다.)
     */
    static int findTagEnd(String text, int from) {
        char quote = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    @Test
    void 모든_POST_폼은_여는_태그_바로_뒤에_CSRF_토큰을_담고_태그_안은_깨끗하다() throws IOException {
        List<String> problems = new ArrayList<>();
        int forms = 0;
        for (Path jsp : jsps()) {
            String text = Files.readString(jsp);
            Matcher m = Pattern.compile("<form\\b", Pattern.CASE_INSENSITIVE).matcher(text);
            while (m.find()) {
                forms++;
                int end = findTagEnd(text, m.end());
                String tag = end < 0 ? text.substring(m.start()) : text.substring(m.start(), end + 1);
                String oneLine = tag.replaceAll("\\s+", " ");
                if (end < 0) {
                    problems.add(jsp.getFileName() + " : 닫히지 않은 폼 태그 " + oneLine);
                    continue;
                }
                if (tag.contains("_csrf") || tag.contains("<input")) {
                    problems.add(jsp.getFileName() + " : 토큰/입력이 폼 태그 속성 안에 끼어 있다 " + oneLine);
                }
                // GET 폼(검색·필터처럼 부작용 없는 조회)은 CSRF 토큰이 필요 없다 — 다른 사이트가 위조해서
                // 보내도 그냥 같은 조회만 될 뿐 상태가 안 바뀐다. method="post"가 아니면 이 폼으로 간주한다.
                if (!tag.toLowerCase().contains("method=\"post\"")) {
                    continue;
                }
                String after = text.substring(end + 1, Math.min(text.length(), end + 1 + 120)).stripLeading();
                if (!after.startsWith("<input type=\"hidden\" " + TOKEN_INPUT)) {
                    problems.add(jsp.getFileName() + " : 여는 태그 바로 뒤에 토큰 입력이 없다 " + oneLine);
                }
            }
        }
        assertTrue(forms >= 50, "폼을 못 찾았다(경로 확인): " + forms);
        assertEquals(List.of(), problems, "폼 문제");
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
