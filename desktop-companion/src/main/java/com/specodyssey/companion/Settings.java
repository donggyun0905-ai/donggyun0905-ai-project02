package com.specodyssey.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * %APPDATA%\SpecOdyssey\companion.json — 연결(서버 주소·토큰), 위치·크기, 말하는 간격, 조용히, 말풍선 기록.
 * 업데이트해도 이 파일은 그대로 남는다.
 */
public class Settings {

    public static final int HISTORY_MAX = 100;
    /** 연결 전 안내에서 열 사이트 (팀 개발 서버 기본 주소) */
    public static final String DEFAULT_SERVER = "http://localhost/spec_odyssey";

    public String server;
    public String token;
    public String userName;
    public Integer x;
    public Integer y;
    public String size = "M";
    public int intervalMinutes = 30;
    public long quietUntil;
    public int lastTier;
    public String skippedVersion;
    // 설정 창에서 바꾸는 것들 (docs/desktop-companion-plan.md 4절 기본값)
    public Boolean autoStart = Boolean.TRUE;      // 윈도우 시작할 때 자동 실행
    public Boolean trendEnabled = Boolean.TRUE;   // 한가할 때 트렌드 기술 돌려 보여 주기
    public int trendIntervalMinutes = 10;
    public int trendSeconds = 8;
    public int summaryHour = 9;                   // 하루 요약 시각
    public int eveningHour = 20;                  // 연속 기록 경고 시각
    public String lastSummaryDate;
    public Set<String> spokenKeys = new HashSet<>();
    public List<HistoryItem> history = new ArrayList<>();

    public static class HistoryItem {
        public long time;
        public String kind;
        public String label;
        public String text;
        public String url;

        public HistoryItem() {
        }

        public HistoryItem(long time, Message m) {
            this.time = time;
            this.kind = m.kind();
            this.label = m.label();
            this.text = m.text();
            this.url = m.url();
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path file() {
        return AppPaths.dataDir().resolve("companion.json");
    }

    public static Settings load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Settings s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Settings.class);
                if (s != null) {
                    s.fillDefaults();
                    return s;
                }
            }
        } catch (IOException | RuntimeException e) {
            // 깨진 설정 파일이면 처음부터 — 연결만 다시 하면 된다
        }
        return new Settings();
    }

    private void fillDefaults() {
        if (spokenKeys == null) {
            spokenKeys = new HashSet<>();
        }
        if (history == null) {
            history = new ArrayList<>();
        }
        if (size == null) {
            size = "M";
        }
        if (intervalMinutes <= 0) {
            intervalMinutes = 30;
        }
        if (autoStart == null) {
            autoStart = Boolean.TRUE;
        }
        if (trendEnabled == null) {
            trendEnabled = Boolean.TRUE;
        }
        if (trendIntervalMinutes <= 0) {
            trendIntervalMinutes = 10;
        }
        if (trendSeconds <= 0) {
            trendSeconds = 8;
        }
        if (summaryHour < 0 || summaryHour > 23) {
            summaryHour = 9;
        }
        if (eveningHour < 0 || eveningHour > 23) {
            eveningHour = 20;
        }
    }

    /** 원자적으로 저장 — 쓰다가 꺼져도 이전 파일이 남게 임시 파일에 쓰고 바꾼다 */
    public synchronized void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("companion.json.tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // 저장을 못 해도 캐릭터는 계속 동작한다 (다음 저장 때 다시 시도)
        }
    }

    public synchronized void addHistory(long now, Message m) {
        history.add(0, new HistoryItem(now, m));
        while (history.size() > HISTORY_MAX) {
            history.remove(history.size() - 1);
        }
    }

    public boolean isConnected() {
        return token != null && server != null;
    }

    public String siteBase() {
        return server != null ? server : DEFAULT_SERVER;
    }

    public int characterHeight() {
        return switch (size) {
            case "S" -> 110;
            case "L" -> 200;
            default -> 150;
        };
    }
}
