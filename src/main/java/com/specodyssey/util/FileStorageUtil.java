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
import java.util.HexFormat;
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

    // 저장 폴더에 실행 파일류가 섞여 들어가지 않게 하는 최소한의 방어(NFR-7 확장자 제한).
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "msi", "jar", "com", "scr", "ps1", "vbs"
    );

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

    public static boolean isExtensionBlocked(String originalFilename) {
        return BLOCKED_EXTENSIONS.contains(extensionOf(originalFilename).toLowerCase());
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
