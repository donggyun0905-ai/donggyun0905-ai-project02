package com.specodyssey.companion;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/** 사이트(style.css)와 같은 색·글꼴 — 크림색 카드, 갈색 글씨, 금색 포인트 */
public final class Theme {

    public static final Color CARD = new Color(0xfdf8ec);
    public static final Color INK = new Color(0x3a2f24);
    public static final Color INK_SOFT = new Color(0x8a7a63);
    public static final Color BORDER = new Color(0xe4d4b0);
    public static final Color GOLD = new Color(0xc9a24b);
    public static final Color TEAL = new Color(0x1f7a6c);
    public static final Color DANGER = new Color(0xc0432a);
    public static final Color PRIMARY = new Color(0x8b4f2a);
    public static final Color HOVER = new Color(0xf6e9cf);

    private static final String FAMILY = pickFamily();

    private Theme() {
    }

    public static Font font(int style, float size) {
        return new Font(FAMILY, style, Math.round(size));
    }

    /** 말풍선 왼쪽 띠·제목 색 — 종류별 */
    public static Color kindColor(String kind) {
        if (kind == null) {
            return PRIMARY;
        }
        return switch (kind) {
            case Message.WARN -> DANGER;
            case Message.NOTICE -> TEAL;
            case Message.PRAISE -> GOLD;
            case Message.INFO -> INK_SOFT;
            default -> PRIMARY;
        };
    }

    /** 윈도우 기본 회색 메뉴 대신 사이트 색 메뉴 */
    public static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        Font menu = font(Font.PLAIN, 13);
        for (String k : new String[]{"PopupMenu", "MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem"}) {
            UIManager.put(k + ".background", CARD);
            UIManager.put(k + ".foreground", INK);
            UIManager.put(k + ".font", menu);
            UIManager.put(k + ".selectionBackground", HOVER);
            UIManager.put(k + ".selectionForeground", INK);
            UIManager.put(k + ".acceleratorForeground", INK_SOFT);
        }
        UIManager.put("PopupMenu.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD, 1), BorderFactory.createEmptyBorder(6, 2, 6, 2)));
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("Separator.background", CARD);
    }

    public static void style(JPopupMenu menu) {
        menu.setBackground(CARD);
        for (var c : menu.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setBackground(CARD);
                jc.setForeground(INK);
                if (jc instanceof JMenuItem item) {
                    item.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 18));
                }
                if (jc instanceof JMenu sub) {
                    sub.getPopupMenu().setBackground(CARD);
                    for (var s : sub.getMenuComponents()) {
                        if (s instanceof JComponent sj) {
                            sj.setBackground(CARD);
                            sj.setForeground(INK);
                            sj.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 18));
                        }
                    }
                }
            }
        }
    }

    private static String pickFamily() {
        String[] wanted = {"Pretendard", "Malgun Gothic", "맑은 고딕", "Dialog"};
        var have = java.util.Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String w : wanted) {
            if (have.contains(w)) {
                return w;
            }
        }
        return Font.DIALOG;
    }
}
