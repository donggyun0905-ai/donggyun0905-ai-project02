package com.specodyssey.companion;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * 설정 — 다시 말하는 간격 · 트렌드 순환(켜기·간격·보여 주는 시간) · 하루 요약 시각 · 저녁 경고 시각 · 크기 · 자동 실행.
 * [저장]을 누르면 바로 적용되고 companion.json에 남는다.
 */
public class SettingsWindow extends JFrame {

    private final Settings settings;
    private final Runnable onSaved;

    private final JSpinner interval = spinner(10, 5, 120, 5);
    private final JCheckBox trendOn = new JCheckBox("한가할 때 오늘의 트렌드 기술을 보여 주기");
    private final JSpinner trendEvery = spinner(10, 1, 120, 1);
    private final JSpinner trendSeconds = spinner(8, 3, 60, 1);
    private final JSpinner summaryHour = spinner(9, 0, 23, 1);
    private final JSpinner eveningHour = spinner(20, 0, 23, 1);
    private final JComboBox<String> size = new JComboBox<>(new String[]{"작게", "보통", "크게"});
    private final JCheckBox autoStart = new JCheckBox("윈도우를 켜면 캐릭터도 자동으로 켜기");

    public SettingsWindow(Settings settings, Runnable onSaved) {
        super("설정 — 스펙 오디세이 캐릭터");
        this.settings = settings;
        this.onSaved = onSaved;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setAlwaysOnTop(true);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.CARD);
        body.setBorder(BorderFactory.createEmptyBorder(16, 18, 10, 18));

        body.add(section("말풍선"));
        body.add(row("말풍선을 끈 뒤 다시 말하기까지", interval, "분"));
        body.add(row("하루 요약을 말하는 시각", summaryHour, "시"));
        body.add(row("연속 기록이 끊기기 전 알려 주는 시각", eveningHour, "시"));
        body.add(Box.createVerticalStrut(10));
        body.add(section("오늘의 트렌드"));
        body.add(check(trendOn));
        body.add(row("몇 분마다", trendEvery, "분"));
        body.add(row("한 개를 보여 주는 시간", trendSeconds, "초"));
        body.add(Box.createVerticalStrut(10));
        body.add(section("캐릭터"));
        body.add(row("크기", size, ""));
        body.add(check(autoStart));

        JButton save = new JButton("저장");
        save.addActionListener(e -> save());
        JButton cancel = new JButton("닫기");
        cancel.addActionListener(e -> setVisible(false));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBackground(Theme.CARD);
        buttons.add(cancel);
        buttons.add(save);

        getContentPane().setBackground(Theme.CARD);
        getContentPane().add(body, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setLocationRelativeTo(null);
    }

    /** 지금 설정값을 채워 보여 준다 */
    public void open() {
        interval.setValue(settings.intervalMinutes);
        trendOn.setSelected(Boolean.TRUE.equals(settings.trendEnabled));
        trendEvery.setValue(settings.trendIntervalMinutes);
        trendSeconds.setValue(settings.trendSeconds);
        summaryHour.setValue(settings.summaryHour);
        eveningHour.setValue(settings.eveningHour);
        size.setSelectedIndex("S".equals(settings.size) ? 0 : "L".equals(settings.size) ? 2 : 1);
        autoStart.setSelected(Boolean.TRUE.equals(settings.autoStart));
        autoStart.setEnabled(AppPaths.isPackaged());
        setVisible(true);
        toFront();
    }

    private void save() {
        settings.intervalMinutes = (Integer) interval.getValue();
        settings.trendEnabled = trendOn.isSelected();
        settings.trendIntervalMinutes = (Integer) trendEvery.getValue();
        settings.trendSeconds = (Integer) trendSeconds.getValue();
        settings.summaryHour = (Integer) summaryHour.getValue();
        settings.eveningHour = (Integer) eveningHour.getValue();
        settings.size = new String[]{"S", "M", "L"}[size.getSelectedIndex()];
        settings.autoStart = autoStart.isSelected();
        settings.save();
        onSaved.run();
        setVisible(false);
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        s.setPreferredSize(new Dimension(70, 26));
        return s;
    }

    private static JComponent section(String title) {
        JLabel l = new JLabel(title);
        l.setFont(Theme.font(Font.BOLD, 14));
        l.setForeground(Theme.PRIMARY);
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JComponent row(String label, JComponent field, String unit) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setBackground(Theme.CARD);
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(Font.PLAIN, 13));
        l.setForeground(Theme.INK);
        l.setPreferredSize(new Dimension(230, 24));
        p.add(l);
        p.add(field);
        if (!unit.isEmpty()) {
            JLabel u = new JLabel(unit);
            u.setForeground(Theme.INK_SOFT);
            p.add(u);
        }
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JComponent check(JCheckBox box) {
        box.setBackground(Theme.CARD);
        box.setForeground(Theme.INK);
        box.setFont(Theme.font(Font.PLAIN, 13));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }
}
