package com.specodyssey.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * DOCUMENTS(서류 보관함) 실제 파일 저장/조회 유틸.
 * 관련 요구사항: FR-61 · 62, NFR-7(용량·확장자 제한, 저장 경로/무결성 관리)
 *
 * webapp 디렉터리 밖에 저장한다 — webapp 안(css/js/image처럼 공개 정적 경로)에 두면 URL만 알면
 * 누구나 다른 사용자의 프로젝트 산출물을 내려받을 수 있다. 다운로드는 항상 DocumentDownloadServlet이
 * 소유자 확인 후 스트리밍하는 경로로만 이뤄진다.
 *
 * 저장 위치는 DBUtil과 같은 방식(.env → 환경변수 → 기본값)으로 정한다.
 */
public final class FileStorageUtil {

    private static final String ENV_FILE = "/.env";
    private static final Path UPLOAD_DIR;

    // 업로드할 수 있는 파일 형식은 이 한 곳(허용 목록)에서만 정한다 — 서류 보관함·로드맵 제출·공유 문서가 모두 쓴다.
    // 예전에는 차단 목록(exe·bat 등)이라 html·svg 같은 파일도 올라가고, 그걸 면접관이 브라우저에서 열 수 있었다.
    // 확장자별 MIME도 여기서 고정한다 — 브라우저가 보낸 Content-Type은 믿지 않는다.
    private static final Map<String, String> MIME_BY_EXTENSION = mimeTable();

    private static Map<String, String> mimeTable() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("pdf", "application/pdf");
        m.put("doc", "application/msword");
        m.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        m.put("hwp", "application/x-hwp");
        m.put("hwpx", "application/haansofthwpx");
        m.put("ppt", "application/vnd.ms-powerpoint");
        m.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        m.put("txt", "text/plain; charset=UTF-8");
        m.put("md", "text/plain; charset=UTF-8");
        m.put("png", "image/png");
        m.put("jpg", "image/jpeg");
        m.put("jpeg", "image/jpeg");
        m.put("gif", "image/gif");
        m.put("zip", "application/zip");
        return Collections.unmodifiableMap(m);
    }

    // 브라우저 안에서 바로 열어도 스크립트가 돌 수 없는 형식(공유 문서 미리보기용). 나머지는 내려받기만 된다.
    private static final Set<String> INLINE_SAFE_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "gif");

    static {
        Properties env = loadEnvFile();
        String configured = firstNonNull(env.getProperty("UPLOAD_DIR"), System.getenv("UPLOAD_DIR"));
        String dir = configured != null ? configured : System.getProperty("user.home") + "/spec-odyssey-uploads";
        UPLOAD_DIR = Paths.get(dir);
        try {
            Files.createDirectories(UPLOAD_DIR);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("업로드 저장 폴더를 만들 수 없습니다: " + UPLOAD_DIR + " - " + e);
        }
    }

    private FileStorageUtil() {
    }

    /** 올릴 수 있는 파일 형식인지(허용 목록). 파일명이 없거나 확장자가 없으면 false. */
    public static boolean isAllowedFile(String originalFilename) {
        return originalFilename != null && MIME_BY_EXTENSION.containsKey(extensionOf(originalFilename).toLowerCase(Locale.ROOT));
    }

    /** 확장자로 정한 MIME. 허용 목록 밖이면 application/octet-stream. */
    public static String mimeTypeFor(String originalFilename) {
        if (originalFilename == null) {
            return "application/octet-stream";
        }
        return MIME_BY_EXTENSION.getOrDefault(extensionOf(originalFilename).toLowerCase(Locale.ROOT), "application/octet-stream");
    }

    /** 브라우저에서 바로 열어도 안전한 형식(PDF·이미지)인지. */
    public static boolean isInlineSafe(String originalFilename) {
        return originalFilename != null && INLINE_SAFE_EXTENSIONS.contains(extensionOf(originalFilename).toLowerCase(Locale.ROOT));
    }

    /** PDF인지 — 화면 안에 원본 그대로 끼워 보여줄 수 있는 형식(면접관 뷰 이력서·자소서 미리보기). */
    public static boolean isPdf(String originalFilename) {
        return originalFilename != null && "pdf".equals(extensionOf(originalFilename).toLowerCase(Locale.ROOT));
    }

    // 원본 파일명은 저장 경로 생성에 전혀 쓰지 않는다(UUID로만 생성) — 경로 조작 공격 자체가 성립하지 않는다.
    public static SavedFile save(InputStream in, String originalFilename) throws IOException {
        String ext = extensionOf(originalFilename);
        String storedName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path target = UPLOAD_DIR.resolve(storedName);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new UncheckedIOException(new IOException("SHA-256 알고리즘을 사용할 수 없습니다", e));
        }
        try (DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            Files.copy(digestIn, target);
        }
        long fileSize = Files.size(target);
        String checksum = HexFormat.of().formatHex(digest.digest());
        return new SavedFile(storedName, target.toString(), fileSize, checksum);
    }

    // 저장에 실패한 요청에서 이미 디스크에 쓴 파일들을 되돌릴 때 쓴다(부분 업로드 정리).
    public static void deleteQuietly(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException ignored) {
            // 정리 실패는 서비스 흐름을 막을 이유가 안 된다 — 고아 파일은 운영에서 별도로 치운다.
        }
    }

    // 다운로드 서블릿이 소유자 확인 후에만 호출한다.
    public static void writeTo(String filePath, OutputStream out) throws IOException {
        Files.copy(Paths.get(filePath), out);
    }

    // 텍스트 파일(연습장 노트 등)을 UTF-8 문자열로 읽는다. 호출부가 소유자 확인을 끝낸 경로만 넘긴다.
    public static String readText(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }

    private static Properties loadEnvFile() {
        Properties props = new Properties();
        try (InputStream in = FileStorageUtil.class.getResourceAsStream(ENV_FILE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(".env 파일을 읽는 중 오류가 발생했습니다: " + e);
        }
        return props;
    }

    private static String firstNonNull(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback;
    }

    public static final class SavedFile {
        private final String storedName;
        private final String filePath;
        private final long fileSize;
        private final String checksum;

        public SavedFile(String storedName, String filePath, long fileSize, String checksum) {
            this.storedName = storedName;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.checksum = checksum;
        }

        public String getStoredName() {
            return storedName;
        }

        public String getFilePath() {
            return filePath;
        }

        public long getFileSize() {
            return fileSize;
        }

        public String getChecksum() {
            return checksum;
        }
    }
}
