package com.specodyssey.util;

/**
 * 레벤슈타인 편집 거리(삽입·삭제·치환 최소 횟수) — 오타·표기 차이를 흡수하는 퍼지 매칭에 공용으로 쓴다.
 * ProfileService(직무명 검색)와 FuzzyNameMatcher(SKILL 이름 매칭)가 같은 알고리즘을 각자 구현하고
 * 있던 걸 여기로 모았다.
 */
public final class EditDistanceUtil {

    private EditDistanceUtil() {
    }

    public static int distance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * distance와 같지만 이웃한 두 글자의 순서가 바뀐 것("Pyhton")을 1번으로 센다 (OSA 거리).
     * 오타는 순서 바뀜이 흔해서, 기준을 엄격하게 잡아도 이런 오타는 살릴 수 있다 — FuzzyNameMatcher가 쓴다.
     */
    public static int transpositionAwareDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + 1);
                }
            }
        }
        return dp[a.length()][b.length()];
    }
}
