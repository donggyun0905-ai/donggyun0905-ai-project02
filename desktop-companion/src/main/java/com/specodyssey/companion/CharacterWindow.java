package com.specodyssey.companion;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * 바탕화면 캐릭터 — 테두리 없는 투명 창, 항상 위, 작업 표시줄 아이콘 없음. 투명한 곳을 누르면 뒤 프로그램이 눌린다.
 * 그림은 등급 캐릭터(tier1~5) 한 장이고, 움직임은 코드로: 평소 숨쉬기 · 말할 때 통 튀기 · 올리면 살짝 커짐 ·
 * 조용히일 때 졸기(기울고 흐려짐, Zz) · 연결 끊김일 때 흐려짐 · 등급 상승 때 점프와 반짝임.
 */
public class CharacterWindow extends JFrame {

    public interface Listener {
        void onCharacterClick();

        void onMenu(MouseEvent e);

        void onMoved(int x, int y);
    }

    private static final int SIDE_PAD = 26;
    private static final int TOP_PAD = 40;
    private static final int BOTTOM_PAD = 6;
    private static final int PEEK_VISIBLE = 38;
    private static final double IMAGE_RATIO = 580.0 / 880.0;

    private final Listener listener;
    private final Canvas canvas = new Canvas();
    private BufferedImage source;
    private BufferedImage scaled;
    private int tier = 1;
    private int imgH;
    private int imgW;

    private boolean hover;
    private boolean quiet;
    private boolean offline;
    private boolean dragging;
    private long talkStart;
    private long celebrateStart;
    private Point hiddenFrom;

    public CharacterWindow(Listener listener, int height) {
        this.listener = listener;
        // 테두리 없는 JFrame — JWindow는 포커스를 받을 수 없어 우클릭 메뉴 밖을 눌러도 메뉴가 안 닫혔다.
        // 저절로 포커스를 가져가지는 않고(setAutoRequestFocus false), 캐릭터를 누를 때만 활성화된다.
        setUndecorated(true);
        setType(Window.Type.UTILITY);
        setAlwaysOnTop(true);
        setAutoRequestFocus(false);
        setBackground(new Color(0, 0, 0, 0));
        setContentPane(canvas);
        canvas.setOpaque(false);
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setTier(1);
        setCharacterHeight(height);
        MouseAdapter mouse = new Mouse();
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        new Timer(33, e -> canvas.repaint()).start();
    }

    // ---------------------------------------------------------------- 상태

    public void setTier(int tier) {
        int t = Math.max(1, Math.min(5, tier));
        if (source != null && t == this.tier) {
            return;
        }
        this.tier = t;
        try (InputStream in = CharacterWindow.class.getResourceAsStream("char/tier" + t + ".png")) {
            source = in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            source = null;
        }
        rescale();
    }

    public int tier() {
        return tier;
    }

    public void setCharacterHeight(int height) {
        imgH = height;
        imgW = (int) Math.round(height * IMAGE_RATIO);
        rescale();
        setSize(imgW + SIDE_PAD * 2, imgH + TOP_PAD + BOTTOM_PAD);
    }

    public void talk() {
        talkStart = System.currentTimeMillis();
    }

    public void celebrate() {
        celebrateStart = System.currentTimeMillis();
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public boolean isPeeking() {
        return hiddenFrom != null;
    }

    /** 숨기기 — 화면 오른쪽 가장자리로 들어가 머리만 살짝 보인다 */
    public void peek() {
        if (hiddenFrom != null) {
            return;
        }
        hiddenFrom = getLocation();
        Rectangle screen = screenBounds();
        setLocation(screen.x + screen.width - PEEK_VISIBLE - SIDE_PAD / 2, getY());
    }

    /** 숨긴 캐릭터를 다시 꺼낸다 */
    public void unpeek() {
        if (hiddenFrom == null) {
            return;
        }
        setLocation(hiddenFrom);
        hiddenFrom = null;
    }

    /** 그림이 그려지는 화면 영역 — 말풍선 위치 기준 */
    public Rectangle characterBounds() {
        Point p = getLocation();
        return new Rectangle(p.x + SIDE_PAD, p.y + TOP_PAD, imgW, imgH);
    }

    public Rectangle screenBounds() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        Rectangle b = gc.getBounds();
        var insets = getToolkit().getScreenInsets(gc);
        return new Rectangle(b.x + insets.left, b.y + insets.top,
                b.width - insets.left - insets.right, b.height - insets.top - insets.bottom);
    }

    /** 처음 위치 — 화면 오른쪽 아래, 작업 표시줄 바로 위 */
    public void placeDefault() {
        Rectangle s = screenBounds();
        setLocation(s.x + s.width - getWidth() - 24, s.y + s.height - getHeight() - 8);
    }

    /** 저장된 위치가 지금 화면들 안에 있을 때만 쓴다 (모니터를 뺐으면 기본 위치로) */
    public void placeAt(Integer x, Integer y) {
        if (x == null || y == null) {
            placeDefault();
            return;
        }
        Rectangle probe = new Rectangle(x + SIDE_PAD, y + TOP_PAD, Math.max(1, imgW / 2), Math.max(1, imgH / 2));
        for (var device : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().intersects(probe)) {
                setLocation(x, y);
                return;
            }
        }
        placeDefault();
    }

    // ---------------------------------------------------------------- 그리기

    private void rescale() {
        if (source == null || imgH <= 0) {
            scaled = null;
            return;
        }
        double dpi = 1.0;
        try {
            dpi = getGraphicsConfiguration().getDefaultTransform().getScaleX();
        } catch (RuntimeException ignored) {
            // 화면 정보를 못 얻으면 1배
        }
        int targetW = Math.max(1, (int) Math.round(imgW * dpi * 1.1));
        int targetH = Math.max(1, (int) Math.round(imgH * dpi * 1.1));
        // 큰 그림을 한 번에 줄이면 거칠어서 절반씩 여러 번 줄인다
        BufferedImage img = source;
        int w = img.getWidth();
        int h = img.getHeight();
        do {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();
            img = next;
        } while (w != targetW || h != targetH);
        scaled = img;
    }

    private class Canvas extends JComponent {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (scaled == null) {
                g.dispose();
                return;
            }
            long now = System.currentTimeMillis();
            double dy = quiet ? 0 : Math.sin(now / 3000.0 * Math.PI * 2) * 2.5; // 숨쉬기
            double rot = 0;
            float alpha = 1f;

            long talked = now - talkStart;
            if (talked < 700) { // 통 튀기
                dy -= Math.abs(Math.sin(talked / 700.0 * Math.PI * 2)) * 7;
                rot = Math.sin(talked / 700.0 * Math.PI * 4) * 0.035;
            }
            long party = now - celebrateStart;
            boolean celebrating = party < 1400;
            if (celebrating) { // 크게 점프
                double k = party / 1400.0;
                dy -= Math.sin(Math.min(1, k * 1.6) * Math.PI) * 26;
            }
            if (dragging) {
                rot = 0.12;
            }
            if (quiet) {
                rot = -0.17;
                alpha = 0.7f;
            }
            if (offline) {
                alpha = Math.min(alpha, 0.5f);
            }
            if (isPeeking()) {
                alpha = Math.min(alpha, 0.85f);
            }
            double scale = hover && !dragging ? 1.05 : 1.0;

            int x0 = SIDE_PAD;
            int y0 = TOP_PAD;
            double cx = x0 + imgW / 2.0;
            double bottom = y0 + imgH;
            AffineTransform at = new AffineTransform();
            at.translate(cx, bottom + dy);
            at.rotate(rot);
            at.scale(scale, scale);
            at.translate(-imgW / 2.0, -imgH);
            Graphics2D gi = (Graphics2D) g.create();
            gi.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            gi.transform(at);
            gi.drawImage(scaled, 0, 0, imgW, imgH, null);
            gi.dispose();

            if (quiet) {
                drawZz(g, now, (int) (cx + imgW * 0.25), y0 + 6);
            }
            if (celebrating) {
                drawSparkles(g, now - celebrateStart, cx, y0 + imgH * 0.45);
            }
            g.dispose();
        }

        private void drawZz(Graphics2D g, long now, int x, int y) {
            double t = (now % 2400) / 2400.0;
            g.setFont(Theme.font(Font.BOLD, 15));
            for (int i = 0; i < 2; i++) {
                double k = (t + i * 0.5) % 1.0;
                g.setColor(new Color(58, 47, 36, (int) (200 * (1 - k))));
                g.drawString(i == 0 ? "Z" : "z", (int) (x + k * 14 + i * 9), (int) (y + 18 - k * 18));
            }
        }

        private void drawSparkles(Graphics2D g, long t, double cx, double cy) {
            double k = t / 1400.0;
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4 + k;
                double r = 30 + k * 55;
                double sx = cx + Math.cos(ang) * r;
                double sy = cy + Math.sin(ang) * r * 0.8;
                int a = (int) (230 * (1 - k));
                g.setColor(new Color(201, 162, 75, Math.max(0, a)));
                g.fill(star(sx, sy, 6 - k * 3));
            }
        }

        private java.awt.Shape star(double x, double y, double r) {
            Path2D p = new Path2D.Double();
            for (int i = 0; i < 8; i++) {
                double rr = i % 2 == 0 ? r : r * 0.35;
                double a = i * Math.PI / 4;
                double px = x + Math.cos(a) * rr;
                double py = y + Math.sin(a) * rr;
                if (i == 0) {
                    p.moveTo(px, py);
                } else {
                    p.lineTo(px, py);
                }
            }
            p.closePath();
            return p;
        }
    }

    // ---------------------------------------------------------------- 마우스

    private class Mouse extends MouseAdapter {
        private Point pressScreen;
        private Point pressWindow;

        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                return; // 우클릭 메뉴는 뗄 때 한 번만 연다
            }
            pressScreen = e.getLocationOnScreen();
            pressWindow = getLocation();
            dragging = false;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (pressScreen == null || !SwingUtilities.isLeftMouseButton(e) || isPeeking()) {
                return;
            }
            Point now = e.getLocationOnScreen();
            int dx = now.x - pressScreen.x;
            int dy = now.y - pressScreen.y;
            if (!dragging && Math.abs(dx) + Math.abs(dy) < 5) {
                return;
            }
            dragging = true;
            setLocation(pressWindow.x + dx, pressWindow.y + dy);
            listener.onMoved(getX(), getY());
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
                listener.onMenu(e);
                return;
            }
            if (dragging) {
                dragging = false;
                talkStart = System.currentTimeMillis() - 350; // 착지하듯 한 번 튐
                listener.onMoved(getX(), getY());
            } else if (SwingUtilities.isLeftMouseButton(e) && pressScreen != null) {
                listener.onCharacterClick();
            }
            pressScreen = null;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            hover = true;
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hover = false;
        }
    }
}
