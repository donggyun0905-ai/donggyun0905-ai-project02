package com.specodyssey.service.archive;

import com.specodyssey.dto.TechArticleAttachmentDto;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스펙 아카이브 본문 ↔ 첨부 위치 변환. DB 없이 동작하는 순수 규칙이라 단위 테스트 대상이다.
 *
 * 저장 형식 (TECH_ARTICLE.content):
 *   - [[att:N]] — "N번 첨부(sort_order = N)가 이 자리".
 *     편집기에서 넣은 사진은 [[upload:K]](K = 업로드 파일 번호)로 넘어오고 저장할 때 [[att:N]]이 된다.
 *     본문에 적은 유튜브 링크·이미지 링크(https, .png/.jpg/.gif/.webp)는 자동으로 첨부로 옮기고 [[att:N]]만 남긴다.
 *     그 밖의 링크는 본문에 그대로 두고 화면에서 하이퍼링크로 보여준다.
 *   - 코드 블록 — 마크다운처럼 ```언어 … ``` (펜스는 코드 안 백틱보다 길게). 코드 안의 링크·표시는 건드리지 않는다.
 *   - 글자 수(2000자)에는 글만 센다 — 링크·사진·영상·코드 블록은 세지 않는다.
 *
 * 화면 표시(decode): 한 줄에 첨부 표시만 있으면 그 자리에 이미지·영상을, 코드 블록은 그 자리에 코드를 넣는다.
 *   글 중간에 링크가 있었다면 글에는 링크를 그대로 보여주고, 그 줄 바로 아래에 이미지·영상을 붙인다.
 */
public final class ArchiveContentCodec {

    /** 저장된 본문을 넣을 수 있는 최대 길이 — TEXT(65,535바이트)에 한글로 꽉 채워도 들어가는 크기 (코드 포함) */
    public static final int MAX_STORED_LENGTH = 20_000;

    private static final Pattern ATT_TOKEN = Pattern.compile("\\[\\[att:(\\d{1,4})]]");
    private static final Pattern UPLOAD_TOKEN = Pattern.compile("\\[\\[upload:(\\d{1,4})]]");
    private static final Pattern ANY_TOKEN = Pattern.compile("\\[\\[(?:att|upload):\\d{1,4}]]");
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+");
    private static final Pattern TRAILING = Pattern.compile("[.,!?;:'\")\\]}>…]+$");
    private static final Pattern IMAGE_PATH = Pattern.compile("(?i).+\\.(png|jpe?g|gif|webp)$");
    private static final Pattern FENCE_OPEN = Pattern.compile("^(`{3,})([A-Za-z0-9+#.-]{0,20})[ \\t]*$");
    // 첨부 표시만 있는 줄 — 그 줄바꿈까지 지워야 편집기(글 칸끼리만 줄바꿈으로 잇는다)와 글자 수가 같다
    private static final Pattern TOKEN_ONLY_LINE = Pattern.compile("(?m)\\n?^[ \\t]*\\[\\[(?:att|upload):\\d{1,4}]][ \\t]*$");

    private ArchiveContentCodec() {
    }

    // ================================================================ 글 / 코드 나누기

    /** 본문 조각 — 글이거나 코드 블록 */
    record Chunk(boolean code, String language, String text) {
    }

    /**
     * 줄 단위로 코드 블록(```언어 … ```)과 글을 나눈다. 닫히지 않은 펜스는 글로 취급한다.
     */
    static List<Chunk> chunks(String content) {
        List<Chunk> out = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            Matcher open = FENCE_OPEN.matcher(lines[i]);
            int close = -1;
            if (open.matches()) {
                for (int j = i + 1; j < lines.length; j++) {
                    if (lines[j].strip().equals(open.group(1))) {
                        close = j;
                        break;
                    }
                }
            }
            if (close < 0) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(lines[i]);
                i++;
                continue;
            }
            if (text.length() > 0) {
                out.add(new Chunk(false, null, text.toString()));
                text.setLength(0);
            }
            StringBuilder code = new StringBuilder();
            for (int j = i + 1; j < close; j++) {
                if (j > i + 1) {
                    code.append('\n');
                }
                code.append(lines[j]);
            }
            out.add(new Chunk(true, SpecArchiveRules.normalizeCodeLanguage(open.group(2)), code.toString()));
            i = close + 1;
        }
        if (text.length() > 0) {
            out.add(new Chunk(false, null, text.toString()));
        }
        return out;
    }

    /** 코드 블록을 저장 형식으로 — 펜스는 코드 안에서 가장 긴 백틱 묶음보다 길게 (코드가 펜스를 닫지 못하게) */
    static String fence(String language, String code) {
        int longest = 0;
        Matcher m = Pattern.compile("`+").matcher(code);
        while (m.find()) {
            longest = Math.max(longest, m.group().length());
        }
        String f = "`".repeat(Math.max(3, longest + 1));
        return f + language + "\n" + code + "\n" + f;
    }

    // ================================================================ 저장 (encode)

    /** 저장할 첨부 하나 — 업로드(파일 번호) · 유튜브 · 이미지 링크 */
    public sealed interface Item permits Upload, Youtube, ImageLink {
    }

    public record Upload(int key) implements Item {
    }

    public record Youtube(String url, String videoId) implements Item {
    }

    public record ImageLink(String url) implements Item {
    }

    /** content = 저장할 본문([[att:N]] · 코드 블록 포함), items = N 순서대로의 첨부, countedLength = 글자 수에 세는 길이 */
    public record Encoded(String content, List<Item> items, int countedLength) {
    }

    /**
     * 편집기에서 온 본문을 저장 형식으로 바꾼다.
     * @param raw        [[upload:K]] · 코드 블록이 들어 있을 수 있는 본문
     * @param uploadKeys 실제로 올라온 업로드 파일 번호 — 본문에 없는 번호의 [[upload:K]]는 지운다
     */
    public static Encoded encode(String raw, Set<Integer> uploadKeys) {
        List<Item> items = new ArrayList<>();
        Set<Integer> usedUploads = new HashSet<>();
        List<String> parts = new ArrayList<>();
        for (Chunk chunk : chunks(SpecArchiveRules.normalizeNewlines(raw))) {
            parts.add(chunk.code() ? fence(chunk.language(), chunk.text())
                    : encodeText(chunk.text(), uploadKeys, usedUploads, items));
        }
        String content = String.join("\n", parts).strip();
        return new Encoded(content, items, countedLength(content));
    }

    private static String encodeText(String text, Set<Integer> uploadKeys, Set<Integer> usedUploads, List<Item> items) {
        // 사용자가 직접 [[att:3]]을 쳐서 남의 첨부를 끌어오지 못하게, 저장 형식의 표시는 먼저 무력화한다
        String t = ATT_TOKEN.matcher(text).replaceAll("[ [att:$1]]");
        StringBuilder out = new StringBuilder();
        int pos = 0;
        Matcher m = Pattern.compile(UPLOAD_TOKEN.pattern() + "|" + URL.pattern()).matcher(t);
        while (m.find()) {
            String match = m.group();
            if (match.startsWith("[[upload:")) {
                out.append(t, pos, m.start());
                int key = Integer.parseInt(m.group(1));
                // 올라오지 않은 파일이거나 같은 파일을 두 번 가리키면 표시를 그냥 지운다
                if (uploadKeys.contains(key) && usedUploads.add(key)) {
                    out.append(token(items.size()));
                    items.add(new Upload(key));
                }
                pos = m.end();
                continue;
            }
            String url = stripTrailing(match);
            Item embed = embedOf(url);
            if (embed == null) {
                continue; // 일반 링크 — 본문에 그대로 둔다
            }
            out.append(t, pos, m.start()).append(token(items.size()));
            items.add(embed);
            pos = m.start() + url.length();
        }
        out.append(t.substring(pos));
        return out.toString();
    }

    /** 유튜브 → Youtube, https 이미지 링크 → ImageLink, 그 밖에는 null(일반 링크) */
    static Item embedOf(String url) {
        if (url.length() > SpecArchiveRules.MAX_URL_LENGTH) {
            return null;
        }
        String videoId = SpecArchiveRules.parseYoutubeId(url);
        if (videoId != null) {
            return new Youtube(url, videoId);
        }
        try {
            URI uri = new URI(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getPath() != null && IMAGE_PATH.matcher(uri.getPath()).matches()) {
                return new ImageLink(url);
            }
        } catch (URISyntaxException ignored) {
            // 주소 형식이 아니면 일반 글자로 둔다
        }
        return null;
    }

    /**
     * 글자 수 — 글만 센다. 코드 블록·첨부 표시·링크는 빼고 유니코드 글자(코드포인트) 수로 센다.
     * (js/spec-archive-editor.js countedLength와 같은 규칙 — 편집기는 글 칸끼리만 줄바꿈으로 잇는다)
     */
    public static int countedLength(String content) {
        List<String> texts = new ArrayList<>();
        for (Chunk chunk : chunks(content)) {
            if (!chunk.code()) {
                String t = TOKEN_ONLY_LINE.matcher(chunk.text()).replaceAll("").strip();
                if (!t.isEmpty()) {
                    texts.add(t);
                }
            }
        }
        String visible = URL.matcher(ANY_TOKEN.matcher(String.join("\n", texts)).replaceAll("")).replaceAll("");
        return visible.codePointCount(0, visible.length());
    }

    private static String token(int n) {
        return "[[att:" + n + "]]";
    }

    private static String stripTrailing(String url) {
        return TRAILING.matcher(url).replaceAll("");
    }

    // ================================================================ 화면 표시 (decode)

    /** 화면 조각 — 글(text) · 첨부(attachment) · 코드(code) 중 하나만 채워진다 */
    public record Segment(String text, TechArticleAttachmentDto attachment, String codeLanguage, String code) {
        static Segment ofText(String text) { return new Segment(text, null, null, null); }
        static Segment ofMedia(TechArticleAttachmentDto a) { return new Segment(null, a, null, null); }
        static Segment ofCode(String language, String code) { return new Segment(null, null, language, code); }

        public String getText() { return text; }
        public TechArticleAttachmentDto getAttachment() { return attachment; }
        public String getCodeLanguage() { return codeLanguage; }
        public String getCode() { return code; }
        public String getCodeLanguageLabel() { return SpecArchiveRules.CODE_LANGUAGES.getOrDefault(codeLanguage, "텍스트"); }
        // getAttachment()와 이름이 겹치면 JSP(EL)가 어느 쪽을 읽을지 헷갈리므로 media로 둔다
        public boolean isMedia() { return attachment != null; }
        public boolean isCodeBlock() { return code != null; }

        // Tomcat 11(EL 6)은 record에서 getX()가 아니라 x()만 찾는다 — 계산해서 만든 값은 x()도 함께 둔다(Tomcat 10 계열은 위의 getX/isX를 쓴다)
        public String codeLanguageLabel() { return getCodeLanguageLabel(); }
        public boolean media() { return isMedia(); }
        public boolean codeBlock() { return isCodeBlock(); }
    }

    /**
     * 저장된 본문을 화면 조각으로 나눈다. 본문에서 가리키지 않은 첨부는 맨 뒤에 붙인다(빠뜨리지 않게).
     * @param attachments 이 글의 첨부 (sort_order = 표시 번호)
     */
    public static List<Segment> decode(String content, List<TechArticleAttachmentDto> attachments) {
        Map<Integer, TechArticleAttachmentDto> byOrder = new HashMap<>();
        for (TechArticleAttachmentDto a : attachments) {
            byOrder.put(a.getSortOrder(), a);
        }
        Set<Integer> shown = new HashSet<>();
        List<Segment> segments = new ArrayList<>();
        for (Chunk chunk : chunks(content == null ? "" : content)) {
            if (chunk.code()) {
                segments.add(Segment.ofCode(chunk.language(), chunk.text()));
            } else {
                decodeText(chunk.text(), byOrder, shown, segments);
            }
        }
        for (TechArticleAttachmentDto a : attachments) {
            if (shown.add(a.getSortOrder())) {
                segments.add(Segment.ofMedia(a));
            }
        }
        return segments;
    }

    private static void decodeText(String content, Map<Integer, TechArticleAttachmentDto> byOrder,
                                   Set<Integer> shown, List<Segment> segments) {
        StringBuilder text = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            Matcher only = ATT_TOKEN.matcher(line.strip());
            if (only.matches()) {
                TechArticleAttachmentDto a = byOrder.get(Integer.parseInt(only.group(1)));
                if (a != null && shown.add(a.getSortOrder())) {
                    flush(segments, text);
                    segments.add(Segment.ofMedia(a));
                }
                continue;
            }
            // 글 중간의 첨부 표시 — 링크는 글자로 보여주고, 이미지·영상은 이 줄 아래에 붙인다
            List<TechArticleAttachmentDto> after = new ArrayList<>();
            Matcher m = ATT_TOKEN.matcher(line);
            StringBuilder rendered = new StringBuilder();
            int pos = 0;
            while (m.find()) {
                rendered.append(line, pos, m.start());
                TechArticleAttachmentDto a = byOrder.get(Integer.parseInt(m.group(1)));
                if (a != null) {
                    if (a.getUrl() != null) {
                        rendered.append(a.getUrl());
                    }
                    if (shown.add(a.getSortOrder())) {
                        after.add(a);
                    }
                }
                pos = m.end();
            }
            rendered.append(line.substring(pos));
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(rendered);
            if (!after.isEmpty()) {
                flush(segments, text);
                after.forEach(a -> segments.add(Segment.ofMedia(a)));
            }
        }
        flush(segments, text);
    }

    /** 목록 미리보기용 — 첨부 표시를 지우고, 코드 블록은 [코드]로, 줄바꿈은 공백으로 */
    public static String plainPreview(String content, int maxChars) {
        List<String> parts = new ArrayList<>();
        for (Chunk chunk : chunks(content == null ? "" : content)) {
            parts.add(chunk.code() ? "[코드]" : ANY_TOKEN.matcher(chunk.text()).replaceAll(" "));
        }
        String plain = String.join(" ", parts).replaceAll("\\s+", " ").strip();
        if (plain.codePointCount(0, plain.length()) <= maxChars) {
            return plain;
        }
        return plain.substring(0, plain.offsetByCodePoints(0, maxChars)) + "…";
    }

    /** 다시 쓰기 화면용 — 저장되지 않은 업로드 표시는 지운다 (파일은 다시 골라야 한다). 코드 블록 안은 건드리지 않는다. */
    public static String withoutUploadTokens(String raw) {
        List<String> parts = new ArrayList<>();
        for (Chunk chunk : chunks(SpecArchiveRules.normalizeNewlines(raw))) {
            parts.add(chunk.code() ? fence(chunk.language(), chunk.text())
                    : UPLOAD_TOKEN.matcher(chunk.text()).replaceAll("").replaceAll("\n{3,}", "\n\n"));
        }
        return String.join("\n", parts).strip();
    }

    private static void flush(List<Segment> segments, StringBuilder text) {
        String t = text.toString();
        if (!t.isBlank()) {
            segments.add(Segment.ofText(t.replaceAll("^\n+|\n+$", "")));
        }
        text.setLength(0);
    }
}
