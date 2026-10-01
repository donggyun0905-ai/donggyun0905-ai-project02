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
}
