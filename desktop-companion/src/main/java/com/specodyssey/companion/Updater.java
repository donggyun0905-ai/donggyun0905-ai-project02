package com.specodyssey.companion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.DoubleConsumer;

/**
 * 업데이트 — GitHub Releases에서 "companion-vX.Y.Z" 태그 중 가장 새 버전을 찾아, 지금보다 새것이면 알려 준다.
 * [업데이트]를 누르면: 압축 파일을 받아 SHA-256 확인값과 대조 → 캐릭터를 끄고 작은 스크립트가 설치 폴더를 새 것으로 바꾼 뒤 다시 켠다.
 * 바꾸다 실패하면 이전 폴더로 되돌린다. 설정·토큰·기록은 데이터 폴더에 있어서 그대로 남는다.
 */
public class Updater {

    public static final String REPO = "donggyun0905-ai/donggyun0905-ai-project02";
    public static final String TAG_PREFIX = "companion-v";
    public static final String ZIP_NAME = AppPaths.APP_NAME + ".zip";

    public record Release(String version, String notes, String zipUrl, String shaUrl) {
    }

    private static final Gson GSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 지금보다 새 버전이 있으면 그 정보, 없으면 null */
    public Release findNewer() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases?per_page=30"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", AppPaths.APP_NAME + "/" + Version.current())
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("업데이트 확인 실패 (HTTP " + resp.statusCode() + ")");
        }
        return pickNewer(GSON.fromJson(resp.body(), JsonArray.class), Version.current());
    }

    /** 릴리스 목록에서 정식(초안·미리보기 아님) 캐릭터 릴리스 중 current보다 새 가장 높은 버전 */
    static Release pickNewer(JsonArray releases, String current) {
        Release best = null;
        if (releases == null) {
            return null;
        }
        for (JsonElement e : releases) {
            JsonObject r = e.getAsJsonObject();
            String tag = r.has("tag_name") ? r.get("tag_name").getAsString() : "";
            if (!tag.startsWith(TAG_PREFIX) || bool(r, "draft") || bool(r, "prerelease")) {
                continue;
            }
            String version = tag.substring(TAG_PREFIX.length());
            String zip = null;
            String sha = null;
            for (JsonElement a : r.getAsJsonArray("assets")) {
                JsonObject asset = a.getAsJsonObject();
                String name = asset.get("name").getAsString();
                if (ZIP_NAME.equals(name)) {
                    zip = asset.get("browser_download_url").getAsString();
                } else if ((ZIP_NAME + ".sha256").equals(name)) {
                    sha = asset.get("browser_download_url").getAsString();
                }
            }
            if (zip == null || sha == null || Version.compare(version, current) <= 0) {
                continue;
            }
            if (best == null || Version.compare(version, best.version()) > 0) {
                String notes = r.has("body") && !r.get("body").isJsonNull() ? firstLine(r.get("body").getAsString()) : "";
                best = new Release(version, notes, zip, sha);
            }
        }
        return best;
    }

    /**
     * 내려받고 확인한 뒤 교체 스크립트를 띄운다. 돌아오면 호출부가 프로그램을 끝내야 한다 (스크립트가 끝나길 기다렸다 바꾼다).
     * @param progress 0.0 ~ 1.0
     */
    public void downloadAndLaunchSwap(Release release, DoubleConsumer progress) throws IOException, InterruptedException {
        if (!AppPaths.isPackaged() || !AppPaths.isRunningFromInstallDir()) {
            throw new IOException("설치된 캐릭터에서만 업데이트할 수 있어요.");
        }
        Path work = Files.createTempDirectory("so-companion-update");
        Path zip = work.resolve(ZIP_NAME);

        String expected = fetchText(release.shaUrl()).trim().split("\\s+")[0].toLowerCase();
        download(release.zipUrl(), zip, progress);
        String actual = sha256(zip);
        if (!actual.equals(expected)) {
            throw new IOException("내려받은 파일이 확인값과 달라요. 잠시 후 다시 시도해 주세요.");
        }

        Path script = work.resolve("swap.ps1");
        Files.writeString(script, SWAP_SCRIPT, StandardCharsets.UTF_8);
        new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                "-File", script.toString(),
                "-WaitPid", String.valueOf(ProcessHandle.current().pid()),
                "-Zip", zip.toString(),
                "-InstallDir", AppPaths.installDir().toString(),
                "-AppName", AppPaths.APP_NAME)
                .start();
    }

    // 캐릭터가 꺼지길 기다렸다가 설치 폴더를 바꾼다. 실패하면 이전 폴더로 되돌리고 이전 버전을 켠다.
    private static final String SWAP_SCRIPT = """
            param([int]$WaitPid, [string]$Zip, [string]$InstallDir, [string]$AppName)
            $ErrorActionPreference = 'Stop'
            try { Wait-Process -Id $WaitPid -Timeout 30 -ErrorAction SilentlyContinue } catch {}
            Start-Sleep -Milliseconds 500
            $backup = "$InstallDir.bak"
            $stage = Join-Path (Split-Path $Zip) 'stage'
            try {
                if (Test-Path $backup) { Remove-Item $backup -Recurse -Force }
                Expand-Archive -Path $Zip -DestinationPath $stage -Force
                $new = Join-Path $stage $AppName
                if (-not (Test-Path (Join-Path $new "$AppName.exe"))) { throw 'zip layout' }
                Rename-Item $InstallDir $backup
                Move-Item $new $InstallDir
                Start-Process (Join-Path $InstallDir "$AppName.exe") -ArgumentList '--updated'
                Remove-Item $backup -Recurse -Force -ErrorAction SilentlyContinue
            } catch {
                if ((Test-Path $backup) -and -not (Test-Path (Join-Path $InstallDir "$AppName.exe"))) {
                    if (Test-Path $InstallDir) { Remove-Item $InstallDir -Recurse -Force -ErrorAction SilentlyContinue }
                    Rename-Item $backup $InstallDir
                }
                Start-Process (Join-Path $InstallDir "$AppName.exe")
            }
            """;

    private String fetchText(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", AppPaths.APP_NAME).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("확인값을 받지 못했어요 (HTTP " + resp.statusCode() + ")");
        }
        return resp.body();
    }

    private void download(String url, Path to, DoubleConsumer progress) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5))
                .header("User-Agent", AppPaths.APP_NAME).GET().build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IOException("내려받지 못했어요 (HTTP " + resp.statusCode() + ")");
        }
        long total = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = resp.body(); var out = Files.newOutputStream(to)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    progress.accept(Math.min(1.0, done / (double) total));
                }
            }
        }
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

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }

    private static String firstLine(String s) {
        String line = s.strip().split("\\R", 2)[0].replaceFirst("^[#*\\-\\s]+", "");
        return line.length() > 60 ? line.substring(0, 60) + "…" : line;
    }
}
