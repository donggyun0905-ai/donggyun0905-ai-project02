package com.specodyssey.service.archive;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스펙 아카이브 입력 규칙 — DB 없이 판단하는 검증만 모았다 (단위 테스트 대상).
 * 사용자 입력과 외부 링크는 전부 이 규칙을 통과해야 저장된다.
 */
public final class SpecArchiveRules {

    public static final int TITLE_MAX = 30;
    public static final int CONTENT_MAX = 2000;
    public static final int COMMENT_MAX = 300;
    // 첨부 개수 제한은 없다 — 용량만 본다. 글 하나에 올리는 사진을 모두 합쳐 10MB (그래서 한 장도 10MB를 넘을 수 없다)
    public static final long MAX_TOTAL_UPLOAD_BYTES = 10L * 1024 * 1024;
    public static final long MAX_IMAGE_BYTES = MAX_TOTAL_UPLOAD_BYTES;
    public static final int MAX_URL_LENGTH = 2048;

    /**
     * 코드 블록 언어 — highlight.js 이름(js/vendor/highlight, 공통 빌드) → 화면 표시 이름. 이 목록 밖의 값은 plaintext로 저장한다.
     * 순서 = 편집기 언어 선택 목록 순서 (많이 쓰는 것부터).
     */
    public static final Map<String, String> CODE_LANGUAGES = codeLanguages();

    private static Map<String, String> codeLanguages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("plaintext", "텍스트");
        m.put("java", "Java");
        m.put("python", "Python");
        m.put("javascript", "JavaScript");
        m.put("typescript", "TypeScript");
        m.put("c", "C");
        m.put("cpp", "C++");
        m.put("csharp", "C#");
        m.put("go", "Go");
        m.put("kotlin", "Kotlin");
        m.put("swift", "Swift");
        m.put("rust", "Rust");
        m.put("php", "PHP");
        m.put("ruby", "Ruby");
        m.put("sql", "SQL");
        m.put("xml", "HTML / XML");
        m.put("css", "CSS");
        m.put("bash", "Bash / Shell");
        m.put("json", "JSON");
        m.put("yaml", "YAML");
        m.put("r", "R");
        m.put("markdown", "Markdown");
        return Collections.unmodifiableMap(m);
    }

    /** 코드 블록 언어 정리 — 대소문자를 무시하고, 흔한 별칭(js·py·html…)을 받아 주고, 모르는 값은 plaintext */
    public static String normalizeCodeLanguage(String lang) {
        String l = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
        l = switch (l) {
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "py" -> "python";
            case "c++" -> "cpp";
            case "c#", "cs" -> "csharp";
            case "kt" -> "kotlin";
            case "html", "htm" -> "xml";
            case "sh", "shell", "zsh" -> "bash";
            case "yml" -> "yaml";
            case "md" -> "markdown";
            case "text", "txt", "" -> "plaintext";
            default -> l;
        };
        return CODE_LANGUAGES.containsKey(l) ? l : "plaintext";
    }

    // 유튜브 영상 ID는 영문·숫자·-·_ 11자리
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern YOUTUBE_PATH_ID = Pattern.compile("^/(?:embed|shorts|live|v)/([A-Za-z0-9_-]{11})(?:[/?#].*)?$");

    private SpecArchiveRules() {
    }

    /** 앞뒤 공백을 지운 제목. 비었거나 30자를 넘으면 IllegalArgumentException (메시지는 화면에 그대로 보여준다). */
    public static String checkTitle(String title) {
        String t = title == null ? "" : title.strip();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("제목을 입력해 주세요.");
        }
        if (t.codePointCount(0, t.length()) > TITLE_MAX) {
            throw new IllegalArgumentException("제목은 " + TITLE_MAX + "자까지 쓸 수 있습니다.");
        }
        return t;
    }

    /**
     * 저장 형식으로 바꾼 본문 검사 — 글도 첨부도 없으면, 또는 글자 수(링크·첨부 표시 제외)가 2000자를 넘으면
     * IllegalArgumentException. 링크를 아주 많이 붙여 저장 한도를 넘는 경우도 막는다.
     */
    public static void checkContent(ArchiveContentCodec.Encoded encoded) {
        if (encoded.content().isBlank() && encoded.items().isEmpty()) {
            throw new IllegalArgumentException("본문을 입력해 주세요.");
        }
        if (encoded.countedLength() > CONTENT_MAX) {
            throw new IllegalArgumentException("본문은 " + CONTENT_MAX + "자까지 쓸 수 있습니다. (링크·사진은 세지 않음, 지금 "
                    + encoded.countedLength() + "자)");
        }
        if (encoded.content().length() > ArchiveContentCodec.MAX_STORED_LENGTH) {
            throw new IllegalArgumentException("본문에 링크가 너무 많습니다. 일부를 줄여 주세요.");
        }
    }

    /** 댓글 — 비었거나 300자를 넘으면 IllegalArgumentException. */
    public static String checkComment(String content) {
        String c = normalizeNewlines(content);
        if (c.isEmpty()) {
            throw new IllegalArgumentException("댓글 내용을 입력해 주세요.");
        }
        if (c.codePointCount(0, c.length()) > COMMENT_MAX) {
            throw new IllegalArgumentException("댓글은 " + COMMENT_MAX + "자까지 쓸 수 있습니다.");
        }
        return c;
    }

    /**
     * 유튜브 링크에서 영상 ID를 꺼낸다. 유튜브 링크가 아니면 null.
     * 지원: youtube.com/watch?v=ID · youtu.be/ID · youtube.com/shorts/ID · /embed/ID · /live/ID · m.·music.·www. 하위 도메인
     * 임베드 주소는 원본 링크가 아니라 이 ID로만 만든다 — 다른 사이트를 iframe에 끼워 넣을 수 없게.
     */
    public static String parseYoutubeId(String link) {
        URI uri = parseHttpUri(link);
        if (uri == null || uri.getHost() == null) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (host.equals("youtu.be")) {
            String id = path.startsWith("/") ? path.substring(1) : path;
            int slash = id.indexOf('/');
            id = slash >= 0 ? id.substring(0, slash) : id;
            return YOUTUBE_ID.matcher(id).matches() ? id : null;
        }
        if (!(host.equals("youtube.com") || host.endsWith(".youtube.com")
                || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com"))) {
            return null;
        }
        if (path.equals("/watch")) {
            String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
            for (String pair : query.split("&")) {
                if (pair.startsWith("v=")) {
                    String id = pair.substring(2);
                    return YOUTUBE_ID.matcher(id).matches() ? id : null;
                }
            }
            return null;
        }
        Matcher m = YOUTUBE_PATH_ID.matcher(path);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * 이미지 링크 검사 — https만, 아이디·비밀번호가 들어간 링크는 거부, 2048자 이하.
     * 올바르면 앞뒤 공백을 지운 링크를, 아니면 IllegalArgumentException.
     * (http는 https 페이지에서 경고가 뜨고 엿보기가 가능해서 받지 않는다.)
     */
    static String checkImageUrl(String link) {
        String l = link == null ? "" : link.strip();
        if (l.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("이미지 링크가 너무 깁니다 (" + MAX_URL_LENGTH + "자 이하).");
        }
        URI uri = parseHttpUri(l);
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("이미지 링크는 https:// 로 시작하는 주소만 넣을 수 있습니다.");
        }
        return l;
    }

    /** 유튜브 링크 검사 — 영상 ID를 돌려준다. 유튜브 링크가 아니면 IllegalArgumentException. */
    static String checkYoutubeUrl(String link) {
        String l = link == null ? "" : link.strip();
        if (l.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("유튜브 링크가 너무 깁니다.");
        }
        String id = parseYoutubeId(l);
        if (id == null) {
            throw new IllegalArgumentException("유튜브 영상 링크를 알아볼 수 없습니다. (예: https://www.youtube.com/watch?v=… 또는 https://youtu.be/…)");
        }
        return id;
    }

    /**
     * 파일 앞부분(매직 넘버)으로 실제 이미지 형식을 판별한다. PNG · JPEG · GIF · WebP만, 아니면 null.
     * 브라우저가 보내는 Content-Type이나 확장자는 바꿔치기가 쉬워서 믿지 않는다. SVG는 스크립트를 담을 수 있어 받지 않는다.
     */
    public static String detectImageMime(byte[] head) {
        if (head == null) {
            return null;
        }
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(head, 'G', 'I', 'F', '8', '7', 'a') || startsWith(head, 'G', 'I', 'F', '8', '9', 'a')) {
            return "image/gif";
        }
        if (head.length >= 12 && startsWith(head, 'R', 'I', 'F', 'F')
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /** 이미지 보여주기에서 응답 Content-Type으로 써도 되는 값인지 (저장값을 그대로 믿지 않는다) */
    public static boolean isServableImageMime(String mime) {
        return "image/png".equals(mime) || "image/jpeg".equals(mime) || "image/gif".equals(mime) || "image/webp".equals(mime);
    }

    public static String normalizeNewlines(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static URI parseHttpUri(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(link.strip());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            return uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
