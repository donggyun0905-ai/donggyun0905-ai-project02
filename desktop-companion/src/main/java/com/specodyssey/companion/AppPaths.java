package com.specodyssey.companion;

import java.nio.file.Path;

/**
 * 프로그램이 쓰는 폴더.
 *  - 설치 폴더 %LOCALAPPDATA%\SpecOdysseyCompanion — 프로그램 파일 (업데이트 때 통째로 바뀐다)
 *  - 데이터 폴더 %APPDATA%\SpecOdyssey — 설정·연결 토큰·말풍선 기록 (업데이트해도 그대로)
 */
public final class AppPaths {

    public static final String APP_NAME = "SpecOdysseyCompanion";

    private AppPaths() {
    }

    public static Path dataDir() {
        String appData = System.getenv("APPDATA");
        return appData != null ? Path.of(appData, "SpecOdyssey") : Path.of(System.getProperty("user.home"), ".specodyssey");
    }

    public static Path installDir() {
        String local = System.getenv("LOCALAPPDATA");
        return local != null ? Path.of(local, APP_NAME) : Path.of(System.getProperty("user.home"), APP_NAME);
    }

    /** jpackage로 만든 exe로 실행 중이면 그 exe 경로, IDE·java -jar 실행이면 null */
    public static Path runningExe() {
        String p = System.getProperty("jpackage.app-path");
        return p == null || p.isBlank() ? null : Path.of(p);
    }

    /** 실행 중인 프로그램 폴더 (exe가 들어 있는 폴더) */
    public static Path runningAppDir() {
        Path exe = runningExe();
        return exe == null ? null : exe.getParent();
    }

    public static Path installedExe() {
        return installDir().resolve(APP_NAME + ".exe");
    }

    public static boolean isPackaged() {
        return runningExe() != null;
    }

    public static boolean isRunningFromInstallDir() {
        Path dir = runningAppDir();
        return dir != null && dir.toAbsolutePath().normalize().equals(installDir().toAbsolutePath().normalize());
    }
}
