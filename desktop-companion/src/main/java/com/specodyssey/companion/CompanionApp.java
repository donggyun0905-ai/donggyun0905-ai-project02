package com.specodyssey.companion;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 스펙 오디세이 데스크톱 캐릭터 — 시작점.
 *
 * 켜지는 순서: (이미 켜져 있으면 받은 값만 넘기고 끝) → (내려받은 폴더면 설치 폴더로 복사해 다시 켬) → specodyssey:// 등록
 * → 캐릭터 띄우기 → 1분마다 서버에서 할 말 받기 → 규칙(BubbleRules)대로 말풍선.
 * 서버(웹)와만 이야기하고 DB에는 붙지 않는다. 무엇을 말할지는 서버가 정한다.
 */
public class CompanionApp implements CharacterWindow.Listener, BubbleWindow.Listener {

    private static final long POLL_SECONDS = 10; // 사이트에서 한 일이 10초 안에 반영되게
    /** 계정이 바뀌었는지·로그아웃했는지만 묻는 간격 — 바뀌면 바로 할 말을 다시 받는다 (누르지 않아도 모습이 바로 바뀌게) */
    private static final long WHOAMI_SECONDS = 2;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    /** 사이트에서 로그아웃해 쉬는 중 안내 */
    private static final String SIGNED_OUT_KEY = "signed-out";
    /** 지금 로그아웃해 쉬는 중인가 — 2초 확인과 비교한다 (worker·화면 스레드가 같이 읽는다) */
    private volatile boolean resting;

    private final Settings settings = Settings.load();
    private final ApiClient api = new ApiClient();
    private final Updater updater = new Updater(api);
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "companion-worker");
        t.setDaemon(true);
        return t;
    });
    private final LaunchArgs launch;

    private CharacterWindow character;
    private BubbleWindow bubble;
    private HistoryWindow history;
    private SettingsWindow settingsWindow;
    private NoteWindow noteWindow;
    private BubbleRules rules;

    private List<Message> serverMessages = List.of();
    private final List<Message> urgent = new ArrayList<>();   // 인사·칭찬 — 바로
    private final List<Message> extras = new ArrayList<>();   // 연결 안내·업데이트 — 한가할 때
    private Updater.Release availableUpdate;
    private boolean greetAfterConnect;
    private boolean dirty;
    // 하루 요약 · 트렌드 순환 (서버가 /messages에 같이 준다)
    private String summaryText;
    private String siteUrl;
    private List<ApiClient.Trend> trends = List.of();
    private Message trendShowing;      // 지금 떠 있는 트렌드 말풍선 (잠깐 보였다 사라짐, 30분 규칙과 별개)
    private long trendHideAt;
    private long nextTrendAt;
    private int trendIndex;

    public static void main(String[] args) {
        Theme.install();
        LaunchArgs launch = LaunchArgs.parse(args);
        CompanionApp app = new CompanionApp(launch);
        // 이미 켜진 캐릭터가 있으면 받은 값(연결 코드 등)만 넘기고 끝낸다.
        // 설치 직후 다시 켜진 경우(--installed)는 앞 프로그램이 소켓을 놓을 때까지 잠깐 기다린다.
        boolean afterInstall = List.of(args).contains(WindowsSetup.INSTALLED_FLAG);
        if (!SingleInstance.claim(launch.toLine(), app::onForwarded, afterInstall ? 25 : 1)) {
            System.exit(0);
        }
        // 내려받은 폴더에서 처음 켰으면 설치 폴더로 옮겨 거기서 다시 켠다 (업데이트가 폴더를 바꿀 수 있게)
        if (WindowsSetup.installAndRelaunchIfNeeded(args)) {
            System.exit(0);
        }
        if (AppPaths.isPackaged()) {
            Thread reg = new Thread(WindowsSetup::registerProtocolAndShortcut, "register");
            reg.setDaemon(true);
            reg.start();
        }
        SwingUtilities.invokeLater(app::start);
    }

    CompanionApp(LaunchArgs launch) {
        this.launch = launch;
    }

    private void start() {
        rules = new BubbleRules(settings.intervalMinutes * 60_000L, settings.spokenKeys);
        character = new CharacterWindow(this, settings.characterHeight());
        if (settings.lastTier > 0) {
            character.setTier(settings.lastTier);
        }
        character.placeAt(settings.x, settings.y);
        character.setVisible(true);
        bubble = new BubbleWindow(this);
        history = new HistoryWindow(this::browse);
        settingsWindow = new SettingsWindow(settings, this::onSettingsSaved);
        noteWindow = new NoteWindow(settings, api, worker);
        nextTrendAt = System.currentTimeMillis() + settings.trendIntervalMinutes * 60_000L;
        if (AppPaths.isPackaged()) {
            boolean autoStart = Boolean.TRUE.equals(settings.autoStart);
            worker.execute(() -> WindowsSetup.setAutoStart(autoStart)); // 기본 켜짐 — 설정 창에서 끌 수 있다
        }

        if (launch.updated()) {
            urgent.add(Message.local("updated:" + Version.current(), Message.PRAISE, "업데이트 완료",
                    "새 버전 " + Version.current() + "(으)로 바뀌었어요!", null, null));
        }
        if (launch.hasConnect()) {
            connect(launch.code(), launch.server());
        } else if (!settings.isConnected()) {
            extras.add(connectGuide());
        }
        worker.scheduleWithFixedDelay(this::poll, 0, POLL_SECONDS, TimeUnit.SECONDS);
        worker.scheduleWithFixedDelay(this::checkWhoAmI, WHOAMI_SECONDS, WHOAMI_SECONDS, TimeUnit.SECONDS);
        worker.scheduleWithFixedDelay(this::checkUpdate, 15, 24 * 60 * 60, TimeUnit.SECONDS);
        new Timer(1000, e -> tick()).start();
        tick();
    }

    // ---------------------------------------------------------------- 서버

    private void connect(String code, String server) {
        worker.execute(() -> {
            try {
                // 들고 있던 토큰을 같이 보낸다 — 서버가 예전 연결을 끊어 이 PC의 연결이 하나만 남는다
                String token = api.exchange(server, code, System.getenv("COMPUTERNAME"), settings.token);
                settings.server = server;
                settings.token = token;
                settings.save();
                SwingUtilities.invokeLater(() -> {
                    extras.removeIf(m -> m.key().equals("connect-guide"));
                    // 연결 전에 켜 두어 "연결해 주세요" 말풍선이 떠 있었으면 거둔다 — 안 그러면 인사가 그 뒤에서 기다린다
                    if (rules.current() != null && rules.current().key().equals("connect-guide")) {
                        closeBubble(rules.current(), false);
                    }
                    greetAfterConnect = true;
                    character.unpeek();
                });
                poll();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> forceShow(Message.local("connect-fail:" + System.currentTimeMillis(), Message.WARN,
                        "연결", "연결하지 못했어요. " + e.getMessage(), "사이트 열기", botUrl())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void poll() {
        if (!settings.isConnected()) {
            SwingUtilities.invokeLater(() -> character.setOffline(false));
            return;
        }
        try {
            ApiClient.Snapshot snap = api.messages(settings.server, settings.token, settings.eveningHour);
            SwingUtilities.invokeLater(() -> onSnapshot(snap));
        } catch (ApiClient.UnauthorizedException e) {
            SwingUtilities.invokeLater(this::onDisconnected);
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> character.setOffline(true)); // 오류 창 없이 흐려지기만
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 2초마다 — 이 PC 브라우저에서 다른 계정으로 로그인했거나 로그아웃했으면 10초를 기다리지 않고 바로 다시 받는다.
     * 서버는 이 확인에서 DB에 쓰지 않는다 (/whoami).
     */
    private void checkWhoAmI() {
        if (!settings.isConnected()) {
            return;
        }
        try {
            ApiClient.WhoAmI me = api.whoami(settings.server, settings.token);
            boolean changed = me.signedOut() != resting
                    || (!me.signedOut() && me.account() != null && !me.account().equals(settings.account));
            if (changed) {
                poll();
            }
        } catch (ApiClient.UnauthorizedException e) {
            poll(); // 연결이 끊겼다 — poll이 정리한다
        } catch (IOException e) {
            // 서버가 잠깐 안 되면 다음 확인에서
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void onSnapshot(ApiClient.Snapshot snap) {
        resting = snap.signedOut();
        if (snap.signedOut()) {
            onSignedOut(snap.siteUrl());
            return;
        }
        character.setOffline(false);
        if (extras.removeIf(m -> m.key().equals(SIGNED_OUT_KEY))) {
            // 다시 로그인했다 — 쉬는 안내를 거두고, 돌아온(또는 새로 옮겨 온) 계정 이름으로 인사한다
            if (rules.current() != null && rules.current().key().equals(SIGNED_OUT_KEY)) {
                closeBubble(rules.current(), false);
            }
            greetAfterConnect = true;
        }
        if (snap.account() != null && settings.account != null && !snap.account().equals(settings.account)) {
            onAccountSwitched();
        }
        settings.account = snap.account();
        serverMessages = snap.messages();
        summaryText = snap.summary();
        siteUrl = snap.siteUrl();
        trends = snap.trends() == null ? List.of() : snap.trends();
        if (availableUpdate == null && settings.isConnected()) {
            worker.execute(this::checkUpdate); // 연결된 뒤 처음 받은 때 한 번 (그 뒤로는 하루 한 번)
        }
        settings.userName = snap.userName();
        if (snap.tier() != null) {
            int index = snap.tier().index();
            if (settings.lastTier > 0 && index > settings.lastTier) {
                character.setTier(index);
                character.celebrate();
                String next = snap.tier().nextName() == null ? "최고 등급이에요!"
                        : "다음 " + snap.tier().nextName() + "까지 " + String.format("%,d", snap.tier().pointsToNext()) + "점";
                urgent.add(Message.local("tier:" + index, Message.PRAISE, "등급 상승",
                        snap.tier().name() + " 달성! " + next, "대시보드", snap.siteUrl()));
            }
            character.setTier(index);
            settings.lastTier = index;
        }
        if (greetAfterConnect) {
            greetAfterConnect = false;
            String who = snap.userName() == null ? "" : ", " + snap.userName() + "님";
            urgent.add(Message.local("hello:" + System.currentTimeMillis(), Message.PRAISE, "연결 완료",
                    "반가워요" + who + "! 앞으로 할 일을 여기서 알려 드릴게요.", null, null));
        }
        dirty = true;
        tick();
    }

    /**
     * 이 PC 브라우저에서 사이트를 로그아웃했다 — 연결은 둔 채 쉰다. 다시 로그인하면(다른 계정이어도) 저절로 이어진다.
     * 그동안은 그 계정의 할 일을 말하지 않는다.
     */
    private void onSignedOut(String loginUrl) {
        character.setOffline(true);
        serverMessages = List.of();
        summaryText = null;
        trends = List.of();
        // 그 계정 앞으로 줄 서 있던 말(하루 요약·등급 상승·인사)도 거둔다 — 로그아웃한 사이에 그 계정 할 일이 뜨지 않게
        urgent.clear();
        if (rules.current() != null && !rules.current().local()) {
            closeBubble(rules.current(), false);
        }
        if (extras.stream().noneMatch(m -> m.key().equals(SIGNED_OUT_KEY))) {
            Message resting = Message.local(SIGNED_OUT_KEY, Message.INFO, "쉬는 중",
                    "사이트에서 로그아웃해서 쉬고 있어요. 이 PC에서 다시 로그인하면 그 계정으로 저절로 이어져요.",
                    "로그인하기", loginUrl != null ? loginUrl : settings.siteBase() + "/login");
            extras.add(resting);
            // 막 로그아웃했다 — 떠 있던 말풍선(인사 등)을 거두고 쉬는 안내를 바로 한 번 보여 준다
            if (rules.current() != null) {
                closeBubble(rules.current(), false);
            }
            forceShow(resting);
        }
        dirty = true;
        tick();
    }

    /**
     * 이 PC 브라우저에서 다른 계정으로 로그인해 캐릭터가 그 계정으로 옮겨 왔다 — 지난 계정의 말을 거둔다.
     * 말풍선 기록은 계정마다 따로 남아 있어 지우지 않는다 (기록 창은 지금 계정 것만 보여 준다).
     */
    private void onAccountSwitched() {
        if (rules.current() != null) {
            closeBubble(rules.current(), false);
        }
        urgent.clear();
        settings.spokenKeys = new java.util.HashSet<>();
        settings.lastTier = 0;            // 새 계정 등급을 "등급 상승"으로 축하하지 않게
        settings.lastSummaryDate = null;  // 새 계정의 하루 요약은 다시
        rules = new BubbleRules(settings.intervalMinutes * 60_000L, settings.spokenKeys);
        noteWindow.reset();
        greetAfterConnect = true;
    }

    private void onDisconnected() {
        settings.token = null;
        settings.account = null;
        noteWindow.reset();
        extras.removeIf(m -> m.key().equals(SIGNED_OUT_KEY));
        serverMessages = List.of();
        if (extras.stream().noneMatch(m -> m.key().equals("connect-guide"))) {
            extras.add(connectGuide());
        }
        if (rules.current() != null && !rules.current().local()) {
            closeBubble(rules.current(), false);
        }
        dirty = true;
        tick();
    }

    private void checkUpdate() {
        if (!AppPaths.isPackaged()) {
            return; // IDE에서 실행 중에는 업데이트하지 않는다
        }
        if (!settings.isConnected()) {
            return; // 업데이트 확인도 우리 서버에 묻는다 — 연결된 뒤에
        }
        try {
            Updater.Release r = updater.findNewer(settings.server, settings.token);
            if (r != null) {
                SwingUtilities.invokeLater(() -> {
                    availableUpdate = r;
                    String key = "update:" + r.version();
                    if (!r.version().equals(settings.skippedVersion) && extras.stream().noneMatch(m -> m.key().equals(key))) {
                        String notes = r.notes() == null || r.notes().isBlank() ? "" : " 바뀐 점: " + r.notes();
                        extras.add(Message.local(key, Message.INFO, "새 버전 " + r.version(),
                                "새 버전이 나왔어요." + notes, "업데이트", "update:"));
                    }
                });
            }
        } catch (IOException e) {
            // 확인을 못 해도 다음 날 다시 본다
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- 말풍선 규칙

    private List<Message> available() {
        List<Message> all = new ArrayList<>(serverMessages);
        all.addAll(extras);
        return all;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        character.setQuiet(now < settings.quietUntil);
        List<Message> all = available();
        Message current = rules.current();
        if (current != null && rules.shouldAutoClose(serverMessages)) {
            Message next = rules.successor(serverMessages);
            if (next != null) { // 숫자만 바뀜 (미션 3개 → 2개 남음) → 닫지 않고 내용만 바꾼다
                rules.shown(next);
                bubble.show(next, rules.waitingCount(all), character.characterBounds(), character.screenBounds());
            } else {
                closeBubble(current, false); // 말한 일을 다 했다 → 스스로 끈다
            }
        }
        // 트렌드 말풍선은 정해진 시간만 보이고 저절로 사라진다
        if (trendShowing != null && now >= trendHideAt) {
            endTrend(now);
        }
        // 하루 요약 — 정한 시각(기본 9시)이 지났고 오늘 아직 안 했으면 한 번 (그날 처음 켜질 때도)
        String today = java.time.LocalDate.now(ZONE).toString();
        if (summaryText != null && settings.isConnected() && !resting && !today.equals(settings.lastSummaryDate)
                && java.time.LocalTime.now(ZONE).getHour() >= settings.summaryHour) {
            settings.lastSummaryDate = today;
            urgent.add(Message.local("summary:" + today, Message.TODO, "하루 요약", summaryText, "대시보드", siteUrl));
            dirty = true;
        }
        bubble.setWaiting(rules.waitingCount(all));
        Message next = rules.next(all, urgent, now, settings.quietUntil);
        if (next != null) {
            trendShowing = null; // 중요한 말이 오면 트렌드 말풍선 자리를 넘겨준다
            showMessage(next);
        } else if (Boolean.TRUE.equals(settings.trendEnabled) && rules.current() == null && trendShowing == null
                && now >= nextTrendAt && now >= settings.quietUntil && rules.waitingCount(all) == 0 && !trends.isEmpty()
                && !character.isPeeking()) {
            showTrend(now); // 한가할 때만
        }
        rules.forgetGone(all, urgent);
        settings.spokenKeys = new java.util.HashSet<>(rules.spokenKeys());
        if (dirty) {
            dirty = false;
            settings.save();
        }
    }

    private void showTrend(long now) {
        ApiClient.Trend t = trends.get(trendIndex++ % trends.size());
        String text = t.summary() == null || t.summary().isBlank() ? t.name() : t.name() + " — " + t.summary();
        trendShowing = Message.local("trend:" + t.name(), Message.INFO, "오늘의 트렌드", text,
                t.url() == null ? null : "자세히", t.url());
        trendHideAt = now + settings.trendSeconds * 1000L;
        bubble.show(trendShowing, 0, character.characterBounds(), character.screenBounds());
    }

    private void endTrend(long now) {
        if (trendShowing != null && bubble.isShowing(trendShowing)) {
            bubble.close();
        }
        trendShowing = null;
        nextTrendAt = now + settings.trendIntervalMinutes * 60_000L;
    }

    private void onSettingsSaved() {
        rules.setIntervalMillis(settings.intervalMinutes * 60_000L);
        if (settings.characterHeight() != character.characterBounds().height) {
            resize(settings.size);
        }
        nextTrendAt = System.currentTimeMillis() + settings.trendIntervalMinutes * 60_000L;
        boolean autoStart = Boolean.TRUE.equals(settings.autoStart);
        worker.execute(() -> WindowsSetup.setAutoStart(autoStart));
        worker.execute(this::poll); // 저녁 경고 시각이 바뀌었을 수 있다
        dirty = true;
    }

    private void showMessage(Message m) {
        if (character.isPeeking()) {
            character.unpeek();
        }
        rules.shown(m);
        urgent.removeIf(u -> u.key().equals(m.key()));
        settings.addHistory(System.currentTimeMillis(), m);
        dirty = true;
        character.talk();
        bubble.show(m, rules.waitingCount(available()), character.characterBounds(), character.screenBounds());
    }

    /** 사용자가 직접 부른 말 — 대기·조용히와 상관없이 지금 */
    private void forceShow(Message m) {
        if (rules.current() != null) {
            rules.closed(System.currentTimeMillis());
        }
        showMessage(m);
    }

    private void closeBubble(Message m, boolean markRead) {
        bubble.close();
        rules.closed(System.currentTimeMillis());
        if (markRead && m != null && m.notificationId() != null && settings.isConnected()) {
            String server = settings.server;
            String token = settings.token;
            long id = m.notificationId();
            worker.execute(() -> {
                try {
                    api.markRead(server, token, id); // 웹 헤더의 안 읽은 개수도 같이 준다
                } catch (IOException ignored) {
                    // 못 하면 웹에서 읽음 처리하면 된다
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        dirty = true;
    }

    // ---------------------------------------------------------------- 말풍선·캐릭터 조작

    @Override
    public void onDismiss(Message m) {
        if (m.key().startsWith("trend:")) {
            endTrend(System.currentTimeMillis());
            return;
        }
        if (m.key().startsWith("update:") && availableUpdate != null) {
            settings.skippedVersion = availableUpdate.version(); // 이 버전은 다시 조르지 않는다 (메뉴에는 남음)
            extras.removeIf(x -> x.key().equals(m.key()));
        }
        closeBubble(m, true);
    }

    @Override
    public void onOpen(Message m) {
        if (m.key().startsWith("trend:")) {
            browse(m.url());
            endTrend(System.currentTimeMillis());
            return;
        }
        if (m.key().startsWith("update:")) {
            startUpdate();
            return;
        }
        if (m.url() != null) {
            browse(m.url());
        }
        closeBubble(m, true);
    }

    @Override
    public void onCharacterClick() {
        worker.execute(this::poll); // 누를 때마다 서버에서 바로 다시 받아 온다
        if (character.isPeeking()) {
            character.unpeek();
            settingsMoved();
            return;
        }
        Message top = resting
                ? extras.stream().filter(m -> m.key().equals(SIGNED_OUT_KEY)).findFirst().orElse(null)
                : BubbleRules.mostUrgent(available());
        if (top == null) {
            top = Message.local("none:" + System.currentTimeMillis(), Message.PRAISE, "지금 할 일",
                    settings.isConnected() ? "지금은 급한 일이 없어요. 잘하고 있어요!" : "먼저 사이트와 연결해 주세요.",
                    settings.isConnected() ? null : "사이트 열기", settings.isConnected() ? null : botUrl());
        }
        if (bubble.isShowing(top)) {
            character.talk();
            return;
        }
        forceShow(top);
    }

    @Override
    public void onMoved(int x, int y) {
        settingsMoved();
    }

    private void settingsMoved() {
        if (!character.isPeeking()) {
            settings.x = character.getX();
            settings.y = character.getY();
            dirty = true;
        }
        bubble.follow(character.characterBounds(), character.screenBounds());
    }

    @Override
    public void onMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        item(menu, "지금 할 일 말해 줘", this::onCharacterClick);
        item(menu, "말풍선 기록", () -> history.showItems(settings.historyFor(settings.account)));
        item(menu, "연습장", noteWindow::open);
        menu.addSeparator();

        long now = System.currentTimeMillis();
        JMenu quiet = new JMenu(now < settings.quietUntil ? "조용히 (켜짐)" : "조용히");
        item(quiet, "1시간", () -> setQuiet(System.currentTimeMillis() + 60 * 60_000L));
        item(quiet, "오늘은 그만", () -> setQuiet(LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()));
        JMenuItem wake = item(quiet, "다시 말하기", () -> setQuiet(0));
        wake.setEnabled(now < settings.quietUntil);
        menu.add(quiet);

        item(menu, character.isPeeking() ? "다시 보이기" : "숨기기", () -> {
            if (character.isPeeking()) {
                character.unpeek();
            } else {
                bubble.close();
                character.peek();
            }
            bubble.follow(character.characterBounds(), character.screenBounds());
        });
        menu.addSeparator();

        if (availableUpdate != null) {
            JMenuItem up = item(menu, "업데이트 (" + availableUpdate.version() + ")", this::startUpdate);
            up.setFont(Theme.font(Font.BOLD, 13));
        }
        item(menu, "설정", settingsWindow::open);
        item(menu, "사이트 열기", () -> browse(settings.siteBase() + "/dashboard"));
        if (settings.isConnected()) {
            item(menu, "연결 해제", this::disconnect);
        } else {
            item(menu, "연결하기", () -> browse(botUrl()));
        }
        item(menu, "종료", this::exit);
        Theme.style(menu);
        showMenu(menu, e);
    }

    /** 우클릭 메뉴 — 캐릭터를 누를 때 창이 활성화되므로, 메뉴 밖을 누르면 창이 비활성화되며 Swing이 메뉴를 닫는다 */
    private void showMenu(JPopupMenu menu, MouseEvent e) {
        // 마우스 자리에 띄우면 커서에 가려진다 — 캐릭터 머리 위 가운데에 띄운다 (위에 자리가 없으면 캐릭터 왼쪽)
        java.awt.Dimension size = menu.getPreferredSize();
        Rectangle ch = character.characterBounds();
        Rectangle screen = character.screenBounds();
        int x = ch.x + ch.width / 2 - size.width / 2;
        int y = ch.y - size.height - 6;
        if (y < screen.y) {
            x = ch.x - size.width - 6;
            y = ch.y;
        }
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - size.width));
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - size.height));
        java.awt.Point origin = e.getComponent().getLocationOnScreen();
        menu.show(e.getComponent(), x - origin.x, y - origin.y);
    }

    private void setQuiet(long until) {
        settings.quietUntil = until;
        if (until > System.currentTimeMillis() && rules.current() != null) {
            closeBubble(rules.current(), false);
        }
        dirty = true;
        tick();
    }

    private void resize(String code) {
        Rectangle before = character.getBounds();
        settings.size = code;
        character.setCharacterHeight(settings.characterHeight());
        // 발밑 위치를 그대로 두고 키만 바꾼다
        character.setLocation(before.x + before.width - character.getWidth(), before.y + before.height - character.getHeight());
        settingsMoved();
    }

    private void disconnect() {
        String server = settings.server;
        String token = settings.token;
        worker.execute(() -> {
            try {
                api.disconnect(server, token);
            } catch (IOException ignored) {
                // 서버가 꺼져 있어도 여기서는 끊는다 (웹 프로필에서 해제할 수 있다)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        onDisconnected();
    }

    private void startUpdate() {
        Updater.Release r = availableUpdate;
        if (r == null) {
            return;
        }
        if (!AppPaths.isPackaged() || !settings.isConnected()) {
            forceShow(Message.local("update-fail:" + System.currentTimeMillis(), Message.WARN, "업데이트",
                    "설치된 캐릭터에서, 사이트와 연결된 상태로 업데이트할 수 있어요.", null, null));
            return;
        }
        String server = settings.server;
        String token = settings.token;
        forceShow(Message.local("updating", Message.INFO, "업데이트", "새 버전을 내려받는 중이에요… 0%", null, null));
        worker.execute(() -> {
            try {
                updater.downloadAndRunInstaller(server, token, r, p -> SwingUtilities.invokeLater(() -> bubble.show(
                        Message.local("updating", Message.INFO, "업데이트", "새 버전을 내려받는 중이에요… " + Math.round(p * 100) + "%", null, null),
                        0, character.characterBounds(), character.screenBounds())));
                settings.save();
                System.exit(0); // 설치 프로그램이 새 버전으로 바꾸고 끝나면 캐릭터를 다시 켠다
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> forceShow(Message.local("update-fail:" + System.currentTimeMillis(), Message.WARN,
                        "업데이트", "업데이트하지 못했어요. " + e.getMessage(), null, null)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void exit() {
        settings.spokenKeys = new java.util.HashSet<>(rules.spokenKeys());
        settings.save();
        System.exit(0);
    }

    /** 이미 켜진 캐릭터에게 다른 실행이 넘긴 값 (웹 "캐릭터 연결"을 또 누른 경우 등) */
    void onForwarded(String line) {
        LaunchArgs args = LaunchArgs.parse(line.isBlank() ? new String[0] : line.split(" "));
        SwingUtilities.invokeLater(() -> {
            if (character == null) {
                return;
            }
            character.unpeek();
            character.talk();
            if (args.hasConnect()) {
                connect(args.code(), args.server());
            }
        });
    }

    // ---------------------------------------------------------------- 도움

    private Message connectGuide() {
        return Message.local("connect-guide", Message.INFO, "연결",
                "사이트 '오셍이들' 메뉴에서 '캐릭터 연결'을 누르면 할 일을 알려 드릴게요.", "사이트 열기", botUrl());
    }

    /** 사이트의 "오셍이들" 화면 — 캐릭터 연결·연결된 PC 관리 (예전에는 내 프로필에 있었다) */
    private String botUrl() {
        return settings.siteBase() + "/bot";
    }

    private JMenuItem item(java.awt.Container menu, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        menu.add(item);
        return item;
    }

    private void radio(JMenu menu, ButtonGroup group, String text, boolean selected, Runnable action) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(text, selected);
        item.addActionListener(e -> action.run());
        group.add(item);
        menu.add(item);
    }

    private void browse(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return; // 웹 주소만 연다
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (IOException | RuntimeException ignored) {
            // 아래 방법으로 한 번 더
        }
        WindowsSetup.run("rundll32", "url.dll,FileProtocolHandler", url);
    }
}
