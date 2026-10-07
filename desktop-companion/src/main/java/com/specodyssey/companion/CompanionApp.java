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
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final Settings settings = Settings.load();
    private final ApiClient api = new ApiClient();
    private final Updater updater = new Updater();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "companion-worker");
        t.setDaemon(true);
        return t;
    });
    private final LaunchArgs launch;

    private CharacterWindow character;
    private BubbleWindow bubble;
    private HistoryWindow history;
    private BubbleRules rules;

    private List<Message> serverMessages = List.of();
    private final List<Message> urgent = new ArrayList<>();   // 인사·칭찬 — 바로
    private final List<Message> extras = new ArrayList<>();   // 연결 안내·업데이트 — 한가할 때
    private Updater.Release availableUpdate;
    private boolean greetAfterConnect;
    private boolean dirty;

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
        worker.scheduleWithFixedDelay(this::checkUpdate, 15, 24 * 60 * 60, TimeUnit.SECONDS);
        new Timer(3000, e -> tick()).start();
        tick();
    }

    // ---------------------------------------------------------------- 서버

    private void connect(String code, String server) {
        worker.execute(() -> {
            try {
                String token = api.exchange(server, code, System.getenv("COMPUTERNAME"));
                settings.server = server;
                settings.token = token;
                settings.save();
                SwingUtilities.invokeLater(() -> {
                    extras.removeIf(m -> m.key().equals("connect-guide"));
                    greetAfterConnect = true;
                    character.unpeek();
                });
                poll();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> forceShow(Message.local("connect-fail:" + System.currentTimeMillis(), Message.WARN,
                        "연결", "연결하지 못했어요. " + e.getMessage(), "사이트 열기", profileUrl())));
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
            ApiClient.Snapshot snap = api.messages(settings.server, settings.token);
            SwingUtilities.invokeLater(() -> onSnapshot(snap));
        } catch (ApiClient.UnauthorizedException e) {
            SwingUtilities.invokeLater(this::onDisconnected);
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> character.setOffline(true)); // 오류 창 없이 흐려지기만
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void onSnapshot(ApiClient.Snapshot snap) {
        character.setOffline(false);
        serverMessages = snap.messages();
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

    private void onDisconnected() {
        settings.token = null;
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
        try {
            Updater.Release r = updater.findNewer();
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
        bubble.setWaiting(rules.waitingCount(all));
        Message next = rules.next(all, urgent, now, settings.quietUntil);
        if (next != null) {
            showMessage(next);
        }
        rules.forgetGone(all, urgent);
        settings.spokenKeys = new java.util.HashSet<>(rules.spokenKeys());
        if (dirty) {
            dirty = false;
            settings.save();
        }
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
        if (m.key().startsWith("update:") && availableUpdate != null) {
            settings.skippedVersion = availableUpdate.version(); // 이 버전은 다시 조르지 않는다 (메뉴에는 남음)
            extras.removeIf(x -> x.key().equals(m.key()));
        }
        closeBubble(m, true);
    }

    @Override
    public void onOpen(Message m) {
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
        Message top = BubbleRules.mostUrgent(available());
        if (top == null) {
            top = Message.local("none:" + System.currentTimeMillis(), Message.PRAISE, "지금 할 일",
                    settings.isConnected() ? "지금은 급한 일이 없어요. 잘하고 있어요!" : "먼저 사이트와 연결해 주세요.",
                    settings.isConnected() ? null : "사이트 열기", settings.isConnected() ? null : profileUrl());
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
        item(menu, "말풍선 기록", () -> history.showItems(settings.history));
        menu.addSeparator();

        long now = System.currentTimeMillis();
        JMenu quiet = new JMenu(now < settings.quietUntil ? "조용히 (켜짐)" : "조용히");
        item(quiet, "1시간", () -> setQuiet(System.currentTimeMillis() + 60 * 60_000L));
        item(quiet, "오늘은 그만", () -> setQuiet(LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()));
        JMenuItem wake = item(quiet, "다시 말하기", () -> setQuiet(0));
        wake.setEnabled(now < settings.quietUntil);
        menu.add(quiet);

        JMenu interval = new JMenu("다시 말하는 간격");
        ButtonGroup ig = new ButtonGroup();
        for (int minutes : new int[]{10, 30, 60}) {
            radio(interval, ig, minutes + "분", settings.intervalMinutes == minutes, () -> {
                settings.intervalMinutes = minutes;
                rules.setIntervalMillis(minutes * 60_000L);
                dirty = true;
            });
        }
        menu.add(interval);

        JMenu size = new JMenu("크기");
        ButtonGroup sg = new ButtonGroup();
        String[][] sizes = {{"S", "작게"}, {"M", "보통"}, {"L", "크게"}};
        for (String[] s : sizes) {
            radio(size, sg, s[1], s[0].equals(settings.size), () -> resize(s[0]));
        }
        menu.add(size);
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
        item(menu, "사이트 열기", () -> browse(settings.siteBase() + "/dashboard"));
        if (settings.isConnected()) {
            item(menu, "연결 해제", this::disconnect);
        } else {
            item(menu, "연결하기", () -> browse(profileUrl()));
        }
        item(menu, "종료", this::exit);
        Theme.style(menu);
        showMenu(menu, e);
    }

    /** 우클릭 메뉴 — 캐릭터를 누를 때 창이 활성화되므로, 메뉴 밖을 누르면 창이 비활성화되며 Swing이 메뉴를 닫는다 */
    private void showMenu(JPopupMenu menu, MouseEvent e) {
        menu.show(e.getComponent(), e.getX(), e.getY());
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
        if (!AppPaths.isRunningFromInstallDir()) {
            forceShow(Message.local("update-fail:" + System.currentTimeMillis(), Message.WARN, "업데이트",
                    "설치된 캐릭터에서만 업데이트할 수 있어요.", null, null));
            return;
        }
        forceShow(Message.local("updating", Message.INFO, "업데이트", "새 버전을 내려받는 중이에요… 0%", null, null));
        worker.execute(() -> {
            try {
                updater.downloadAndLaunchSwap(r, p -> SwingUtilities.invokeLater(() -> bubble.show(
                        Message.local("updating", Message.INFO, "업데이트", "새 버전을 내려받는 중이에요… " + Math.round(p * 100) + "%", null, null),
                        0, character.characterBounds(), character.screenBounds())));
                settings.save();
                System.exit(0); // 교체 스크립트가 이어받아 새 버전을 켠다
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

    /** 이미 켜진 캐릭터에게 다른 실행이 넘긴 값 (웹 "캐릭터 켜기"를 또 누른 경우 등) */
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
                "사이트 내 프로필에서 '캐릭터 켜기'를 누르면 할 일을 알려 드릴게요.", "사이트 열기", profileUrl());
    }

    private String profileUrl() {
        return settings.siteBase() + "/profile#companion";
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
