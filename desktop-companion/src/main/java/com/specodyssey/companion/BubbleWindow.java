package com.specodyssey.companion;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 말풍선 — 크림색 카드, 종류별 왼쪽 띠 색, 제목 줄(종류 + ✕), 본문(최대 3줄), "○○ 하러 가기 →", 대기 중인 말 "+N",
 * 캐릭터를 가리키는 꼬리. 기본은 캐릭터 위, 화면 위쪽 끝이면 옆으로. 톡 튀어나오며 커지고, 사라질 때 흐려진다.
 * ✕ = 닫기만, 나머지 = 해당 화면 열기.
 */
public class BubbleWindow extends JWindow {

    public interface Listener {
        void onDismiss(Message m);

        void onOpen(Message m);
    }

    private static final int CARD_W = 270;
    private static final int PAD = 14;
    private static final int STRIPE = 5;
    private static final int RADIUS = 16;
    private static final int TAIL = 11;
    private static final int SHADOW = 8;
    private static final int MAX_LINES = 3;

    private enum Side { BELOW_TAIL_DOWN, LEFT_TAIL_RIGHT, RIGHT_TAIL_LEFT }

    private final Listener listener;
    private final View view = new View();
    private final Font labelFont = Theme.font(Font.BOLD, 12);
    private final Font textFont = Theme.font(Font.PLAIN, 14);
    private final Font linkFont = Theme.font(Font.BOLD, 12);

    private Message message;
    private int waiting;
    private List<String> lines = List.of();
    private boolean truncated;
    private int cardH;
    private Side side = Side.BELOW_TAIL_DOWN;
    private int tailAt;
    private float anim;
    private boolean closing;
    private Timer animTimer;
    private boolean hoverClose;

    public BubbleWindow(Listener listener) {
        this.listener = listener;
        setType(Window.Type.UTILITY);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        view.setOpaque(false);
        setContentPane(view);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (message == null || closing) {
                    return;
                }
                if (closeBox().contains(e.getPoint())) {
                    listener.onDismiss(message);
                } else if (cardRect().contains(e.getPoint())) {
                    listener.onOpen(message);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean over = closeBox().contains(e.getPoint());
                if (over != hoverClose) {
                    hoverClose = over;
                    view.repaint();
                }
                view.setCursor(Cursor.getPredefinedCursor(cardRect().contains(e.getPoint()) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        view.addMouseListener(mouse);
        view.addMouseMotionListener(mouse);
    }

    public Message message() {
        return message;
    }

    public boolean isShowing(Message m) {
        return message != null && !closing && m != null && message.key().equals(m.key());
    }

    /** 말풍선을 띄운다 (이미 떠 있으면 내용만 바꾼다) */
    public void show(Message m, int waitingCount, Rectangle character, Rectangle screen) {
        boolean fresh = message == null || closing;
        message = m;
        waiting = waitingCount;
        layoutText();
        place(character, screen);
        view.setToolTipText(truncated ? m.text() : null);
        if (fresh) {
            closing = false;
            animate(0f, 1f, 180, null);
            setVisible(true);
        } else {
            view.repaint();
        }
    }

    public void setWaiting(int waitingCount) {
        if (waiting != waitingCount) {
            waiting = waitingCount;
            view.repaint();
        }
    }

    /** 캐릭터가 움직이면 따라간다 */
    public void follow(Rectangle character, Rectangle screen) {
        if (message != null && isVisible()) {
            place(character, screen);
        }
    }

    public void close() {
        if (message == null || closing) {
            return;
        }
        closing = true;
        animate(1f, 0f, 180, () -> {
            setVisible(false);
            message = null;
            closing = false;
        });
    }

    // ---------------------------------------------------------------- 배치

    private void place(Rectangle ch, Rectangle screen) {
        int w = CARD_W + SHADOW * 2 + TAIL;
        int h = cardH + SHADOW * 2 + TAIL;
        setSize(w, h);
        int cx = ch.x + ch.width / 2;
        int top = ch.y + 4;
        int x;
        int y;
        if (top - (cardH + TAIL) >= screen.y) {
            side = Side.BELOW_TAIL_DOWN;
            x = cx - CARD_W / 2 - SHADOW;
            x = Math.max(screen.x, Math.min(x, screen.x + screen.width - w));
            y = top - cardH - TAIL - SHADOW;
            tailAt = Math.max(RADIUS + 6, Math.min(CARD_W - RADIUS - 6, cx - (x + SHADOW)));
        } else if (ch.x - CARD_W - TAIL >= screen.x) {
            side = Side.LEFT_TAIL_RIGHT;
            x = ch.x - CARD_W - TAIL - SHADOW;
            y = Math.max(screen.y, ch.y + 10 - SHADOW);
            tailAt = 26;
        } else {
            side = Side.RIGHT_TAIL_LEFT;
            x = ch.x + ch.width - SHADOW;
            y = Math.max(screen.y, ch.y + 10 - SHADOW);
            tailAt = 26;
        }
        setLocation(x, y);
    }

    private Rectangle cardRect() {
        int x = SHADOW + (side == Side.RIGHT_TAIL_LEFT ? TAIL : 0);
        return new Rectangle(x, SHADOW, CARD_W, cardH);
    }

    private Rectangle closeBox() {
        Rectangle c = cardRect();
        return new Rectangle(c.x + c.width - 30, c.y + 6, 24, 22);
    }

    private void layoutText() {
        FontMetrics fm = getFontMetrics(textFont);
        int width = CARD_W - PAD * 2 - STRIPE;
        lines = new ArrayList<>();
        truncated = false;
        String text = message.text() == null ? "" : message.text();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || fm.stringWidth(line.toString() + c) > width) {
                // 단어 중간이면 마지막 공백에서 끊는다
                int cut = c == '\n' ? -1 : line.lastIndexOf(" ");
                String rest = "";
                boolean brokeEarlier = cut > line.length() / 2;
                if (brokeEarlier) {
                    rest = line.substring(cut + 1);
                    line.setLength(cut);
                }
                lines.add(line.toString());
                line = new StringBuilder(rest);
                // 앞 공백에서 끊었으면 지금 글자(공백 포함)는 다음 줄에 그대로, 바로 여기서 끊었으면 공백은 버린다
                if (c != '\n' && (brokeEarlier || c != ' ')) {
                    line.append(c);
                }
            } else {
                line.append(c);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.size() > MAX_LINES) {
            truncated = true;
            List<String> cut = new ArrayList<>(lines.subList(0, MAX_LINES));
            String last = cut.get(MAX_LINES - 1);
            while (!last.isEmpty() && fm.stringWidth(last + "…") > width) {
                last = last.substring(0, last.length() - 1);
            }
            cut.set(MAX_LINES - 1, last + "…");
            lines = cut;
        }
        int lineH = fm.getHeight();
        // 아래 줄("+N" · "○○ 하러 가기 →") 자리는 늘 비워 둔다 — 대기 수가 나중에 생겨도 글자와 겹치지 않게
        cardH = PAD + 18 + 6 + lines.size() * lineH + 10 + 18 + PAD;
    }

    private void animate(float from, float to, int ms, Runnable done) {
        if (animTimer != null) {
            animTimer.stop();
        }
        long start = System.currentTimeMillis();
        anim = from;
        animTimer = new Timer(15, e -> {
            float k = Math.min(1f, (System.currentTimeMillis() - start) / (float) ms);
            anim = from + (to - from) * (float) (1 - Math.pow(1 - k, 3));
            view.repaint();
            if (k >= 1f) {
                ((Timer) e.getSource()).stop();
                if (done != null) {
                    done.run();
                }
            }
        });
        animTimer.start();
    }

    // ---------------------------------------------------------------- 그리기

    private class View extends JComponent {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, anim))));
            if (message == null) {
                g.dispose();
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            Rectangle c = cardRect();
            // 꼬리 쪽에서 톡 튀어나오며 커진다
            double s = 0.88 + 0.12 * anim;
            double ox;
            double oy;
            switch (side) {
                case LEFT_TAIL_RIGHT -> { ox = c.x + c.width; oy = c.y + tailAt; }
                case RIGHT_TAIL_LEFT -> { ox = c.x; oy = c.y + tailAt; }
                default -> { ox = c.x + tailAt; oy = c.y + c.height; }
            }
            g.translate(ox, oy);
            g.scale(s, s);
            g.translate(-ox, -oy);

            Path2D shape = bubbleShape(c);
            // 그림자
            for (int i = SHADOW; i > 0; i -= 2) {
                g.setColor(new Color(58, 47, 36, 6));
                g.setStroke(new BasicStroke(i * 2f));
                g.translate(0, 2);
                g.draw(shape);
                g.translate(0, -2);
            }
            g.setColor(Theme.CARD);
            g.fill(shape);
            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(shape);

            // 종류별 왼쪽 띠
            Color accent = Theme.kindColor(message.kind());
            Graphics2D clip = (Graphics2D) g.create();
            clip.clip(new RoundRectangle2D.Double(c.x, c.y, c.width, c.height, RADIUS * 2, RADIUS * 2));
            clip.setColor(accent);
            clip.fillRect(c.x, c.y, STRIPE, c.height);
            clip.dispose();

            int x = c.x + STRIPE + PAD;
            int y = c.y + PAD;
            // 제목 줄
            g.setFont(labelFont);
            g.setColor(accent);
            g.drawString(message.label() == null ? "" : message.label(), x, y + 13);
            Rectangle cb = closeBox();
            g.setColor(hoverClose ? Theme.INK : Theme.INK_SOFT);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int mx = cb.x + cb.width / 2;
            int my = cb.y + cb.height / 2;
            g.drawLine(mx - 4, my - 4, mx + 4, my + 4);
            g.drawLine(mx - 4, my + 4, mx + 4, my - 4);

            // 본문
            g.setFont(textFont);
            g.setColor(Theme.INK);
            FontMetrics fm = g.getFontMetrics();
            int ty = y + 18 + 6 + fm.getAscent();
            for (String l : lines) {
                g.drawString(l, x, ty);
                ty += fm.getHeight();
            }

            // 아래 줄 — 대기 중인 말 수, 가는 곳
            int bottom = c.y + c.height - PAD;
            if (waiting > 0) {
                String badge = "+" + waiting;
                g.setFont(labelFont);
                int bw = g.getFontMetrics().stringWidth(badge) + 12;
                g.setColor(Theme.HOVER);
                g.fillRoundRect(x, bottom - 16, bw, 18, 18, 18);
                g.setColor(Theme.INK_SOFT);
                g.drawString(badge, x + 6, bottom - 3);
            }
            if (message.url() != null && message.linkText() != null) {
                g.setFont(linkFont);
                g.setColor(Theme.PRIMARY);
                String link = message.linkText() + " →";
                int lw = g.getFontMetrics().stringWidth(link);
                g.drawString(link, c.x + c.width - PAD - lw, bottom - 2);
            }
            g.dispose();
        }

        private Path2D bubbleShape(Rectangle c) {
            Path2D p = new Path2D.Double();
            p.append(new RoundRectangle2D.Double(c.x, c.y, c.width, c.height, RADIUS * 2, RADIUS * 2), false);
            Path2D tail = new Path2D.Double();
            switch (side) {
                case LEFT_TAIL_RIGHT -> {
                    int ty = c.y + tailAt;
                    tail.moveTo(c.x + c.width - 1, ty - 9);
                    tail.lineTo(c.x + c.width + TAIL, ty);
                    tail.lineTo(c.x + c.width - 1, ty + 9);
                }
                case RIGHT_TAIL_LEFT -> {
                    int ty = c.y + tailAt;
                    tail.moveTo(c.x + 1, ty - 9);
                    tail.lineTo(c.x - TAIL, ty);
                    tail.lineTo(c.x + 1, ty + 9);
                }
                default -> {
                    int tx = c.x + tailAt;
                    tail.moveTo(tx - 9, c.y + c.height - 1);
                    tail.lineTo(tx, c.y + c.height + TAIL);
                    tail.lineTo(tx + 9, c.y + c.height - 1);
                }
            }
            tail.closePath();
            java.awt.geom.Area area = new java.awt.geom.Area(p);
            area.add(new java.awt.geom.Area(tail));
            Path2D out = new Path2D.Double();
            out.append(area, false);
            return out;
        }
    }
}
