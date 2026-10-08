package com.specodyssey.companion;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 설치 · 웹 주소(specodyssey://) 등록 · 시작 메뉴 바로가기 — 전부 현재 사용자 범위라 관리자 권한이 필요 없다.
 *
 * 설치: 내려받은 폴더(다운로드 등)에서 처음 켜면 %LOCALAPPDATA%\SpecOdysseyCompanion으로 복사하고 거기서 다시 켠다.
 * 정해진 폴더에 있어야 업데이트가 폴더를 통째로 바꿀 수 있다 (실행 중인 프로그램은 자기 파일을 못 바꾼다).
 */
public final class WindowsSetup {

    /** 설치 폴더로 옮겨 다시 켤 때 붙이는 표시 — 앞 프로그램이 끝나길 잠깐 기다리게 */
    public static final String INSTALLED_FLAG = "--installed";
    private static final String SHORTCUT_NAME = "스펙 오디세이 캐릭터.lnk";

    private WindowsSetup() {
    }

    /**
     * 설치 폴더 밖에서 실행됐으면 설치 폴더로 복사하고 거기서 다시 켠다.
     * @return 다시 켰으면 true — 지금 프로그램은 끝내야 한다
     */
    public static boolean installAndRelaunchIfNeeded(String[] args) {
        if (!AppPaths.isPackaged() || AppPaths.isRunningFromInstallDir()) {
            return false;
        }
        try {
            Path target = AppPaths.installDir();
            deleteTree(target);
            copyTree(AppPaths.runningAppDir(), target);
            List<String> cmd = new ArrayList<>();
            cmd.add(AppPaths.installedExe().toString());
            cmd.addAll(List.of(args));
            cmd.add(INSTALLED_FLAG);
            new ProcessBuilder(cmd).start();
            return true;
        } catch (IOException e) {
            return false; // 복사를 못 하면 지금 자리에서 그냥 쓴다 (업데이트만 안 될 뿐)
        }
    }

    /** specodyssey:// 를 이 exe로 연결하고 시작 메뉴에 바로가기를 만든다 — 켤 때마다 다시 맞춰 둔다 */
    public static void registerProtocolAndShortcut() {
        Path exe = AppPaths.runningExe();
        if (exe == null) {
            return; // IDE에서 실행 중 — 등록하지 않는다
        }
        // reg.exe에 따옴표가 든 값을 넘기면 Java의 인자 처리에서 따옴표가 깨진다 — PowerShell 스크립트를
        // -EncodedCommand(UTF-16 Base64)로 넘겨 따옴표·한글·공백을 그대로 쓴다
        String e = psQuote(exe.toString());
        StringBuilder ps = new StringBuilder();
        ps.append("$k='HKCU:\\Software\\Classes\\").append(LaunchArgs.SCHEME).append("';");
        ps.append("New-Item -Path $k -Force | Out-Null;");
        ps.append("Set-Item -Path $k -Value 'URL:Spec Odyssey Companion';");
        ps.append("New-ItemProperty -Path $k -Name 'URL Protocol' -Value '' -PropertyType String -Force | Out-Null;");
        ps.append("New-Item -Path \"$k\\DefaultIcon\" -Force | Out-Null;");
        ps.append("Set-Item -Path \"$k\\DefaultIcon\" -Value ('\"' + ").append(e).append(" + '\",0');");
        ps.append("New-Item -Path \"$k\\shell\\open\\command\" -Force | Out-Null;");
        ps.append("Set-Item -Path \"$k\\shell\\open\\command\" -Value ('\"' + ").append(e).append(" + '\" \"%1\"');");

        String appData = System.getenv("APPDATA");
        if (appData != null) {
            Path lnk = Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", SHORTCUT_NAME);
            // 설치 프로그램(Setup.exe)으로 설치했으면 시작 메뉴 "Spec Odyssey" 바로가기가 이미 있다 — 그때는 우리 것을 만들지 않는다
            Path fromInstaller = Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Spec Odyssey",
                    AppPaths.APP_NAME + ".lnk");
            ps.append("if (Test-Path ").append(psQuote(fromInstaller.toString())).append(") {");
            ps.append("Remove-Item ").append(psQuote(lnk.toString())).append(" -ErrorAction SilentlyContinue");
            ps.append("} else {");
            ps.append("$s=(New-Object -ComObject WScript.Shell).CreateShortcut(").append(psQuote(lnk.toString())).append(");");
            ps.append("$s.TargetPath=").append(e).append(";$s.Save();");
            ps.append("}");
        }
        runPowerShell(ps.toString());
    }

    /** 윈도우 시작할 때 자동 실행 — 현재 사용자 Run 키에 넣거나 뺀다 (관리자 권한 불필요). IDE 실행이면 아무것도 안 한다 */
    public static void setAutoStart(boolean on) {
        Path exe = AppPaths.runningExe();
        if (exe == null) {
            return;
        }
        String key = "'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'";
        String name = psQuote(AppPaths.APP_NAME);
        if (on) {
            runPowerShell("Set-ItemProperty -Path " + key + " -Name " + name
                    + " -Value ('\"' + " + psQuote(exe.toString()) + " + '\" --autostart')");
        } else {
            runPowerShell("Remove-ItemProperty -Path " + key + " -Name " + name + " -ErrorAction SilentlyContinue");
        }
    }

    static String psQuote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    static void runPowerShell(String script) {
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
        run("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
    }

    static void run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException e) {
            // 등록을 못 해도 캐릭터는 켜진다 — 웹 버튼으로 켜기만 안 될 뿐
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                file.toFile().setWritable(true); // jpackage가 만든 exe는 읽기 전용이라 그대로는 못 지운다
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
