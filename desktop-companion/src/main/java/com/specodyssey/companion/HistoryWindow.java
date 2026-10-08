package com.specodyssey.companion;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/** 말풍선 기록 — 지금까지 띄운 말을 시간순으로. 항목을 누르면 그 화면을 연다. */
public class HistoryWindow extends JFrame {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private final JPanel list = new JPanel();
    private final Consumer<String> openUrl;

    public HistoryWindow(Consumer<String> openUrl) {
        super("말풍선 기록 — 스펙 오디세이");
        this.openUrl = openUrl;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setAlwaysOnTop(true);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(new Color(0xe8d3ab));
        list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        getContentPane().add(scroll, BorderLayout.CENTER);
        setSize(380, 480);
    }

    public void showItems(List<Settings.HistoryItem> items) {
        list.removeAll();
        if (items.isEmpty()) {
            JLabel empty = new JLabel("아직 띄운 말풍선이 없어요.");
            empty.setFont(Theme.font(Font.PLAIN, 13));
            empty.setForeground(Theme.INK_SOFT);
            list.add(empty);
        }
        for (Settings.HistoryItem item : items) {
            list.add(card(item));
            list.add(Box.createVerticalStrut(8));
        }
        list.revalidate();
        list.repaint();
        setLocationRelativeTo(null);
        setVisible(true);
        toFront();
    }

    private Component card(Settings.HistoryItem item) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(Theme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.kindColor(item.kind)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JLabel head = new JLabel(TIME.format(Instant.ofEpochMilli(item.time)) + "  ·  " + (item.label == null ? "" : item.label));
        head.setFont(Theme.font(Font.BOLD, 12));
        head.setForeground(Theme.kindColor(item.kind));
        // 280px이면 윈도우 배율(125% 등)에서 창 밖으로 넘쳐 글자가 잘렸다 — 창(380) 안에 들어오게 줄인다
        JLabel body = new JLabel("<html><body style='width:230px'>" + escape(item.text) + "</body></html>");
        body.setFont(Theme.font(Font.PLAIN, 13));
        body.setForeground(Theme.INK);
        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 40));
        if (item.url != null) {
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openUrl.accept(item.url);
                }
            });
        }
        return card;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
