package com.specodyssey.companion;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * 연습장 — 로드맵 화면의 연습장과 같은 내용 (서버 NoteService). 처음 열 때 서버에서 불러오고, [저장](Ctrl+S)하면 서버에 저장해
 * 웹에서도 바로 보인다. 웹에서 그사이 고쳤으면 저장할 때 알려 주고 덮어쓸지 고르게 한다.
 *
 * 한 번 불러온 내용은 창을 닫았다 열어도 다시 불러오지 않는다 (2026-10-08) — 웹에서 고친 것을 보고 싶으면 [새로 불러오기].
 * 웹과 엇갈려도 저장할 때 버전으로 알아채므로 내용을 잃지 않는다. 다른 계정으로 바뀌면 {@link #reset()}으로 비운다.
 */
public class NoteWindow extends JFrame {

    private final Settings settings;
    private final ApiClient api;
    private final ExecutorService worker;
    private final JTextArea text = new JTextArea();
    // 한 줄 JLabel이면 긴 안내("불러오지 못했어요: …")가 "…"로 잘려 읽을 수 없었다 — 줄을 바꿔 다 보여 준다 (2026-10-08)
    private final JTextArea status = new JTextArea(" ");
    private final JButton save = new JButton("저장");
    private final JButton reload = new JButton("새로 불러오기");
    private String version;
    /** 지금 들고 있는 내용이 어느 연결(서버 + 토큰) 것인지 — 같으면 다시 불러오지 않는다 */
    private String loadedFor;

    public NoteWindow(Settings settings, ApiClient api, ExecutorService worker) {
        super("연습장 — 스펙 오디세이");
        this.settings = settings;
        this.api = api;
        this.worker = worker;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setAlwaysOnTop(true);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.font(Font.PLAIN, 14));
        text.setForeground(Theme.INK);
        text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        status.setForeground(Theme.INK_SOFT);
        status.setFont(Theme.font(Font.PLAIN, 12));
        status.setEditable(false);
        status.setFocusable(false);
        status.setOpaque(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 8));
        for (JButton b : new JButton[]{save, reload}) {
            b.setFont(Theme.font(Font.PLAIN, 12)); // 기본 글꼴에는 한글이 없을 수 있어 사이트 글꼴로
        }
        save.addActionListener(e -> save(false));
        reload.addActionListener(e -> load());
        text.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ctrl S"), "save");
        text.getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save(false);
            }
        });
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Theme.CARD);
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        bottom.add(status, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setBackground(Theme.CARD);
        right.add(reload);
        right.add(save);
        bottom.add(right, BorderLayout.EAST);

        getContentPane().setBackground(Theme.CARD);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);
        setSize(420, 460);
        setLocationRelativeTo(null);
    }

    public void open() {
        setVisible(true);
        toFront();
        if (!settings.isConnected()) {
            reset();
            text.setEnabled(false);
            save.setEnabled(false);
            reload.setEnabled(false);
            status.setText("사이트와 연결하면 연습장을 쓸 수 있어요.");
            return;
        }
        if (version != null && connectionKey().equals(loadedFor)) {
            // 이미 불러온 내용 — 다시 묻지 않고 그대로 (쓰던 내용도 그대로 남아 있다)
            text.setEnabled(true);
            save.setEnabled(true);
            reload.setEnabled(true);
            status.setText("지난번에 불러온 내용이에요. 웹에서 고쳤다면 [새로 불러오기]");
            text.requestFocusInWindow();
            return;
        }
        load();
    }

    /** 다른 계정으로 바뀌었거나 연결이 끊겼다 — 들고 있던 내용을 비운다 */
    public void reset() {
        version = null;
        loadedFor = null;
        text.setText("");
    }

    private String connectionKey() {
        return settings.server + "|" + settings.token;
    }

    private void load() {
        if (!settings.isConnected()) {
            return;
        }
        String key = connectionKey();
        text.setEnabled(false);
        save.setEnabled(false);
        reload.setEnabled(false);
        status.setText("불러오는 중…");
        String server = settings.server;
        String token = settings.token;
        worker.execute(() -> {
            try {
                ApiClient.Note note = api.note(server, token);
                SwingUtilities.invokeLater(() -> {
                    version = note.version();
                    loadedFor = key;
                    text.setText(note.text() == null ? "" : note.text());
                    text.setCaretPosition(0);
                    text.setEnabled(true);
                    save.setEnabled(true);
                    reload.setEnabled(true);
                    status.setText("웹의 연습장과 같은 내용이에요. Ctrl+S로 저장");
                    text.requestFocusInWindow();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    reload.setEnabled(true);
                    status.setText("불러오지 못했어요: " + e.getMessage());
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void save(boolean force) {
        if (!settings.isConnected() || !save.isEnabled()) {
            return;
        }
        save.setEnabled(false);
        status.setText("저장하는 중…");
        String body = text.getText();
        String server = settings.server;
        String token = settings.token;
        String base = version;
        worker.execute(() -> {
            try {
                ApiClient.NoteSave result = api.saveNote(server, token, body, base, force);
                SwingUtilities.invokeLater(() -> {
                    save.setEnabled(true);
                    if (result.conflict()) {
                        int choice = JOptionPane.showConfirmDialog(this,
                                "웹에서 연습장 내용이 바뀌었어요.\n지금 쓴 내용으로 덮어쓸까요? (아니요를 누르면 웹 내용을 불러와요)",
                                "연습장", JOptionPane.YES_NO_OPTION);
                        if (choice == JOptionPane.YES_OPTION) {
                            save(true);
                        } else {
                            version = result.version();
                            text.setText(result.serverText() == null ? "" : result.serverText());
                            status.setText("웹의 최신 내용을 불러왔어요.");
                        }
                        return;
                    }
                    version = result.version();
                    status.setText("저장했어요. 웹 연습장에도 반영됐어요.");
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    save.setEnabled(true);
                    status.setText("저장하지 못했어요: " + e.getMessage());
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
