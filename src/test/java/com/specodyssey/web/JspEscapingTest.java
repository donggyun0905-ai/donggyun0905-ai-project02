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

/**
 * 사용자가 쓴 값이나 외부(LLM·수집)에서 온 글을 JSP에 그냥 `${…}`로 찍으면 `<script>`가 그대로 실행된다(XSS).
 * 글처럼 보이는 속성(제목·설명·내용·이유·파일명·이메일 등)은 반드시 `<c:out>`이나 `fn:escapeXml`로 찍어야 한다 —
 * 새 화면에서 빠뜨리지 않게 모든 JSP를 훑어 확인한다. 태그 속성(test=, items=, value= 의 c:/fmt: 태그)과 주석·스크립트·스타일은 출력이 아니므로 제외한다.
 */
class JspEscapingTest {

    private static final Path VIEWS = Paths.get("src/main/webapp/WEB-INF");

    // 글(문자열)일 가능성이 있는 속성 이름 — 이 이름으로 끝나는 값을 이스케이프 없이 찍으면 실패
    private static final Pattern TEXTY = Pattern.compile(
            "(title|description|content|reason|note|issuer|techStack|rawInput|email|major|interestField|originalName|"
                    + "message|aliasName|jobName|skillName|label|text|url|name|specType|careerStatus|jobCategory)$",
            Pattern.CASE_INSENSITIVE);

    // 우리가 정한 값(마스터 데이터·열거형·숫자 서식)이라 이스케이프가 필요 없는 것
    private static final List<String> ALLOWED = List.of(
            "currentTier.tierName", "currentTier.titleName", "celebrateTierName", "celebrateTierImage",
            "lang.code", "type.name", "type.label", "tierLabel", "typeLabel", "sizeLabel", "scopeText", "ddayText", "navDdayText",
            "markerClass", "markerIcon", "cardClass", "tierLogoPath", "pageContext.request.contextPath", "passwordAction", "pagerPath");

    private static List<Path> jsps() throws IOException {
        try (Stream<Path> files = Files.walk(VIEWS)) {
            List<Path> list = new ArrayList<>();
            files.filter(p -> p.toString().endsWith(".jsp") || p.toString().endsWith(".jspf")).forEach(list::add);
            return list;
        }
    }

    @Test
    void 글처럼_보이는_값은_이스케이프_없이_화면에_찍지_않는다() throws IOException {
        List<String> raw = new ArrayList<>();
        for (Path jsp : jsps()) {
            String text = Files.readString(jsp)
                    .replaceAll("(?s)<%--.*?--%>", "")
                    .replaceAll("(?s)<script.*?</script>", "")
                    .replaceAll("(?s)<style.*?</style>", "");
            Matcher m = Pattern.compile("\\$\\{([^}]*)}").matcher(text);
            while (m.find()) {
                String expr = m.group(1).trim();
                int tagStart = text.lastIndexOf('<', m.start());
                String tag = tagStart < 0 ? "" : text.substring(tagStart, m.start());
                if (tag.matches("(?s)<(c|fmt|fn|jsp):.*")) {
                    continue; // 서버가 해석하는 태그 속성
                }
                if (expr.contains("escapeXml") || ALLOWED.contains(expr) || expr.contains("?") || expr.contains("==")
                        || expr.contains(" empty ") || expr.startsWith("empty ") || expr.startsWith("not ")) {
                    continue;
                }
                String last = expr.substring(expr.lastIndexOf('.') + 1);
                if (ALLOWED.contains(last)) {
                    continue; // 서버가 만든 표시용 문구(날짜 라벨·크기 등)
                }
                if (TEXTY.matcher(last).find()) {
                    raw.add(VIEWS.relativize(jsp) + " : ${" + expr + "}");
                }
            }
        }
        assertEquals(List.of(), raw, "이스케이프 없이 찍는 값 — <c:out value='${…}' /> 로 바꾸세요");
    }
}
