package com.specodyssey.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 시트 한 장짜리 엑셀(.xlsx) 파일을 라이브러리 없이 만든다.
 * .xlsx는 정해진 이름의 XML 파일 몇 개를 zip으로 묶은 형식(Office Open XML)이라 java.util.zip만으로 충분하다.
 *
 * - 첫 행은 머리 행: 굵게, 스크롤해도 고정, 필터 버튼
 * - 열 너비는 열마다 가장 긴 글에 맞춘다 (한글은 영문 2글자 폭으로 센다) — CSV는 너비를 못 담아 엑셀에서 글이 잘려 보였다
 * - 값이 Number면 숫자 칸(엑셀에서 정렬·계산 가능), 나머지는 글 칸. 글은 수식으로 실행되지 않는다(수식 칸을 만들지 않음)
 */
public final class SimpleXlsxWriter {

    static final double MIN_WIDTH = 8;
    static final double MAX_WIDTH = 60;
    private static final double PADDING = 2;

    private SimpleXlsxWriter() {
    }

    /** rows의 첫 행을 머리 행으로 쓴다. 칸 값은 String 또는 Number (null이면 빈 칸). */
    public static byte[] write(String sheetName, List<List<Object>> rows) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "xl/workbook.xml", workbook(sheetName));
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", sheet(rows));
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 파일 생성 실패", e); // 메모리 스트림이라 실제로는 나지 않는다
        }
        return bytes.toByteArray();
    }

    // ---------------------------------------------------------------- 시트

    private static String sheet(List<List<Object>> rows) {
        int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
        StringBuilder sb = new StringBuilder(XML_HEAD)
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        // 머리 행 고정
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
                .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews>");
        if (columnCount > 0) {
            sb.append("<cols>");
            for (int c = 0; c < columnCount; c++) {
                sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                        .append("\" width=\"").append(columnWidth(rows, c)).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<row r=\"").append(r + 1).append("\">");
            List<Object> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                appendCell(sb, columnName(c) + (r + 1), row.get(c), r == 0);
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        if (columnCount > 0 && !rows.isEmpty()) {
            sb.append("<autoFilter ref=\"A1:").append(columnName(columnCount - 1)).append(rows.size()).append("\"/>");
        }
        return sb.append("</worksheet>").toString();
    }

    private static void appendCell(StringBuilder sb, String ref, Object value, boolean header) {
        String style = header ? " s=\"1\"" : "";
        if (value == null) {
            sb.append("<c r=\"").append(ref).append('"').append(style).append("/>");
        } else if (value instanceof Number n) {
            sb.append("<c r=\"").append(ref).append('"').append(style).append("><v>").append(n).append("</v></c>");
        } else {
            sb.append("<c r=\"").append(ref).append('"').append(style).append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xml(value.toString())).append("</t></is></c>");
        }
    }

    /** 열에서 가장 긴 글의 표시 폭 + 여유. 머리 행은 필터 버튼 자리만큼 더 준다. */
    static double columnWidth(List<List<Object>> rows, int column) {
        double max = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            if (column < row.size() && row.get(column) != null) {
                double w = displayWidth(row.get(column).toString()) + (r == 0 ? 2 : 0);
                max = Math.max(max, w);
            }
        }
        return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, max + PADDING));
    }

    // 한글·한자 등 넓은 글자는 2칸, 나머지는 1칸. 여러 줄이면 가장 긴 줄.
    static double displayWidth(String text) {
        double longest = 0;
        for (String line : text.split("\n", -1)) {
            double w = 0;
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                w += cp >= 0x1100 ? 2 : 1;
                i += Character.charCount(cp);
            }
            longest = Math.max(longest, w);
        }
        return longest;
    }

    /** 0 → A, 25 → Z, 26 → AA */
    static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            sb.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return sb.toString();
    }

    // XML 특수문자 이스케이프, XML에 넣을 수 없는 제어문자는 뺀다
    static String xml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 고정 파일

    private static String workbook(String sheetName) {
        return XML_HEAD + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + xml(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private static final String CONTENT_TYPES = XML_HEAD
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = XML_HEAD
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK_RELS = XML_HEAD
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    // 스타일 0 = 기본, 1 = 머리 행(굵게 + 연한 배경 + 아래 테두리)
    private static final String STYLES = XML_HEAD
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"맑은 고딕\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"맑은 고딕\"/></font></fonts>"
            + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF1E4C8\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
            + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
            + "<border><left/><right/><top/><bottom style=\"thin\"><color rgb=\"FFC9A24B\"/></bottom><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/></cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}
