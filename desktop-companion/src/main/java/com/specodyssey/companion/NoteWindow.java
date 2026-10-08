package com.specodyssey.companion;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
 * 연습장 — 로드맵 화면의 연습장과 같은 내용 (서버 NoteService). 열 때 서버에서 불러오고, [저장](Ctrl+S)하면 서버에 저장해 웹에서도 바로 보인다.
 * 웹에서 그사이 고쳤으면 저장할 때 알려 주고 덮어쓸지 고르게 한다.
 */
public class NoteWindow extends JFrame {

    private final Settings settings;
    private final ApiClient api;
    private final ExecutorService worker;
    private final JTextArea text = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final JButton save = new JButton("저장");
    private String version;

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
        save.addActionListener(e -> save(false));
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
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setBackground(Theme.CARD);
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
            text.setText("");
            text.setEnabled(false);
            save.setEnabled(false);
            status.setText("사이트와 연결하면 연습장을 쓸 수 있어요.");
            return;
        }
        text.setEnabled(false);
        save.setEnabled(false);
        status.setText("불러오는 중…");
        String server = settings.server;
        String token = settings.token;
        worker.execute(() -> {
            try {
                ApiClient.Note note = api.note(server, token);
                SwingUtilities.invokeLater(() -> {
                    version = note.version();
                    text.setText(note.text() == null ? "" : note.text());
                    text.setCaretPosition(0);
                    text.setEnabled(true);
                    save.setEnabled(true);
                    status.setText("웹의 연습장과 같은 내용이에요. Ctrl+S로 저장");
                    text.requestFocusInWindow();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> status.setText("불러오지 못했어요: " + e.getMessage()));
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
