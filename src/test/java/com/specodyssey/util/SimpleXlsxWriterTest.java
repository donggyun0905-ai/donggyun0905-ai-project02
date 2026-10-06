package com.specodyssey.util;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SimpleXlsxWriter 단위테스트 — xlsx 구성 파일이 다 들어가고 XML이 올바른지, 열 너비가 글 길이에 맞는지.
 */
class SimpleXlsxWriterTest {

    @Test
    void write_xlsx_구성_파일이_모두_있고_XML이_올바르다() throws Exception {
        List<List<Object>> rows = List.of(
                List.of("순번", "지원자", "자격증"),
                List.of(1, "김철수 <&>", "정보처리기사, SQLD"));
        Map<String, String> parts = unzip(SimpleXlsxWriter.write("지원자 비교", rows));

        assertEquals(List.of("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
                "xl/styles.xml", "xl/worksheets/sheet1.xml"), new ArrayList<>(parts.keySet()));
        for (Map.Entry<String, String> part : parts.entrySet()) {
            parse(part.getValue()); // 깨진 XML이면 예외 → 엑셀이 "파일이 손상됨"으로 거부한다
        }
        String sheet = parts.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("<c r=\"A2\"><v>1</v></c>"), "숫자는 숫자 칸");
        assertTrue(sheet.contains("김철수 &lt;&amp;&gt;"), "특수문자 이스케이프");
        assertTrue(sheet.contains("state=\"frozen\""), "머리 행 고정");
        assertTrue(sheet.contains("<autoFilter ref=\"A1:C2\"/>"), "필터");
        assertTrue(sheet.contains("<c r=\"A1\" s=\"1\" t=\"inlineStr\">"), "머리 행 굵게");
    }

    @Test
    void columnWidth_가장_긴_글에_맞추고_한글은_두_칸으로_센다() {
        List<List<Object>> rows = List.of(
                List.of("이름"),
                List.of("김철수"),
                List.of("ab"));
        // "김철수" = 6칸 + 여유 2 = 8 (머리 "이름"은 4 + 필터 2 = 6)
        assertEquals(8.0, SimpleXlsxWriter.columnWidth(rows, 0));
        assertEquals(6.0, SimpleXlsxWriter.displayWidth("김철수"));
        assertEquals(3.0, SimpleXlsxWriter.displayWidth("abc"));
    }

    @Test
    void columnWidth_너무_짧거나_길면_최소_최대로() {
        assertEquals(SimpleXlsxWriter.MIN_WIDTH, SimpleXlsxWriter.columnWidth(List.of(List.<Object>of("a")), 0));
        assertEquals(SimpleXlsxWriter.MAX_WIDTH,
                SimpleXlsxWriter.columnWidth(List.of(List.<Object>of("가".repeat(100))), 0));
    }

    @Test
    void columnName_엑셀_열_이름() {
        assertEquals("A", SimpleXlsxWriter.columnName(0));
        assertEquals("Z", SimpleXlsxWriter.columnName(25));
        assertEquals("AA", SimpleXlsxWriter.columnName(26));
        assertEquals("AZ", SimpleXlsxWriter.columnName(51));
    }

    @Test
    void xml_넣을_수_없는_제어문자는_뺀다() {
        assertEquals("ab\ncd", SimpleXlsxWriter.xml("a\u0001b\ncd"));
        assertEquals("&quot;x&quot;", SimpleXlsxWriter.xml("\"x\""));
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                parts.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return parts;
    }

    private static Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
