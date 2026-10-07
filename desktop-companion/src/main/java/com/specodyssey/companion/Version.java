package com.specodyssey.companion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** 이 프로그램의 버전 (pom.xml의 version) 과 버전 비교 */
public final class Version {

    private static final String CURRENT = load();

    private Version() {
    }

    public static String current() {
        return CURRENT;
    }

    /** a가 b보다 새 버전이면 양수. "1.2.0", "v1.10.3", "companion-v0.2.0" 모두 숫자 부분만 비교한다 */
    public static int compare(String a, String b) {
        int[] x = parts(a);
        int[] y = parts(b);
        for (int i = 0; i < 3; i++) {
            if (x[i] != y[i]) {
                return Integer.compare(x[i], y[i]);
            }
        }
        return 0;
    }

    static int[] parts(String v) {
        int[] out = new int[3];
        if (v == null) {
            return out;
        }
        String digits = v.replaceAll("^[^0-9]*", "");
        String[] p = digits.split("[.\\-+]");
        for (int i = 0; i < 3 && i < p.length; i++) {
            try {
                out[i] = Integer.parseInt(p[i].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String load() {
        try (InputStream in = Version.class.getResourceAsStream("version.properties")) {
            Properties p = new Properties();
            if (in != null) {
                p.load(in);
            }
            String v = p.getProperty("version", "0.0.0");
            return v.startsWith("$") ? "0.0.0" : v;
        } catch (IOException e) {
            return "0.0.0";
        }
    }
}
