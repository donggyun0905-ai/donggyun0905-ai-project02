package com.specodyssey.companion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.DoubleConsumer;

/**
 * 업데이트 — 우리 사이트(/api/companion/latest)에 올라간 최신 설치 파일이 지금보다 새 버전이면 알려 준다.
 * 관리자가 관리자 화면(데스크톱 캐릭터)에서 Setup.exe를 올리면 DB에 보관되고, 여기서 그것을 받는다.
 * [업데이트]: 설치 파일을 받아 SHA-256을 대조 → 설치 프로그램을 실행하고 캐릭터는 끈다 → 설치 프로그램이 새 버전으로 덮어쓰고
 * 끝나면 캐릭터를 다시 켠다 (installer/main.wxs). 설정·토큰·기록은 데이터 폴더에 있어서 그대로 남는다.
 */
public class Updater {

    public record Release(String version, String notes, String sha256, long size) {
    }

    private final ApiClient api;

    public Updater(ApiClient api) {
        this.api = api;
    }

    /** 지금보다 새 버전이 올라가 있으면 그 정보, 없으면 null */
    public Release findNewer(String server, String token) throws IOException, InterruptedException {
        ApiClient.Latest latest = api.latest(server, token);
        if (!isNewer(latest.version(), Version.current()) || latest.sha256() == null) {
            return null;
        }
        return new Release(latest.version(), firstLine(latest.notes()), latest.sha256(), latest.size());
    }

    static boolean isNewer(String candidate, String current) {
        return candidate != null && Version.compare(candidate, current) > 0;
    }

    /**
     * 설치 파일을 받아 확인하고 실행한다. 돌아오면 호출부가 캐릭터를 끝내야 한다 (설치 프로그램이 파일을 바꿀 수 있게).
     * @param progress 0.0 ~ 1.0
     */
    public void downloadAndRunInstaller(String server, String token, Release release, DoubleConsumer progress)
            throws IOException, InterruptedException {
        Path work = Files.createTempDirectory("so-companion-update");
        Path setup = work.resolve(AppPaths.APP_NAME + "-Setup-" + release.version() + ".exe");
        api.download(server, token, setup, progress, release.size());
        if (!sha256(setup).equalsIgnoreCase(release.sha256())) {
            Files.deleteIfExists(setup);
            throw new IOException("내려받은 파일이 확인값과 달라요. 잠시 후 다시 시도해 주세요.");
        }
        new ProcessBuilder(setup.toString()).start();
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static String firstLine(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String line = s.strip().split("\\R", 2)[0].replaceFirst("^[#*\\-\\s]+", "");
        return line.length() > 60 ? line.substring(0, 60) + "…" : line;
    }
}
