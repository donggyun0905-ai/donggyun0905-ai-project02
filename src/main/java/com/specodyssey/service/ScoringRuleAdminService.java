package com.specodyssey.service;

import com.specodyssey.dao.ScoringRuleDao;
import com.specodyssey.dao.ScoringRuleDao.RuleRow;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 화면의 점수·주기 규칙 보기/저장. 값은 SCORING_RULE에 저장되고 ScoringRules가 읽는다.
 * 모르는 키나 숫자가 아닌 값은 거절하고, 하나라도 잘못이면 아무것도 저장하지 않는다.
 */
public class ScoringRuleAdminService {

    static final int MAX_VALUE = 100_000;

    private final ScoringRuleDao dao = new ScoringRuleDao();

    /** 화면에 보일 규칙 한 줄 */
    public static final class RuleView {
        private final String key;
        private final String group;
        private final String description;
        private final int value;
        private final int defaultValue;

        RuleView(String key, String group, String description, int value, int defaultValue) {
            this.key = key;
            this.group = group;
            this.description = description;
            this.value = value;
            this.defaultValue = defaultValue;
        }

        public String getKey() {
            return key;
        }

        public String getGroup() {
            return group;
        }

        public String getDescription() {
            return description;
        }

        public int getValue() {
            return value;
        }

        public int getDefaultValue() {
            return defaultValue;
        }

        public boolean isChanged() {
            return value != defaultValue;
        }
    }

    /** 그룹 이름 → 그 그룹의 규칙들(화면 순서). 테이블에 아직 없는 규칙은 기본값으로 채워 보여 준다. */
    public Map<String, List<RuleView>> listByGroup() throws SQLException {
        Map<String, RuleRow> stored = new LinkedHashMap<>();
        for (RuleRow row : dao.findAllDetailed()) {
            stored.put(row.key(), row);
        }
        ScoringRules.refresh();
        List<String> keys = new ArrayList<>(stored.keySet());
        for (String key : ScoringRules.knownKeys()) {
            if (!stored.containsKey(key)) {
                keys.add(key);
            }
        }
        Map<String, List<RuleView>> grouped = new LinkedHashMap<>();
        for (String group : GROUPS) {
            grouped.put(group, new ArrayList<>());
        }
        for (String key : keys) {
            if (!ScoringRules.isKnown(key)) {
                continue; // 코드가 쓰지 않는 키는 보여 줘도 의미가 없다
            }
            RuleRow row = stored.get(key);
            int value = ScoringRules.get(key);
            grouped.get(groupOf(key)).add(new RuleView(key, groupOf(key),
                    row == null || row.description() == null ? key : row.description(), value, ScoringRules.defaultOf(key)));
        }
        grouped.values().removeIf(List::isEmpty);
        return grouped;
    }

    /**
     * 폼에서 온 값(키 → 문자열)을 저장한다. 바뀐 것만 쓴다.
     * @return 바꾼 규칙 수
     */
    public int save(Map<String, String> submitted) throws SQLException {
        Map<String, Integer> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : submitted.entrySet()) {
            if (!ScoringRules.isKnown(e.getKey())) {
                throw new IllegalArgumentException("알 수 없는 규칙입니다: " + e.getKey());
            }
            String text = e.getValue() == null ? "" : e.getValue().trim();
            int value;
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(e.getKey() + " 값은 숫자여야 합니다.");
            }
            int min = ScoringRules.allowsZero(e.getKey()) ? 0 : 1;
            if (value < min || value > MAX_VALUE) {
                throw new IllegalArgumentException(e.getKey() + " 값은 " + min + " 이상 " + MAX_VALUE + " 이하여야 합니다.");
            }
            parsed.put(e.getKey(), value);
        }
        Map<String, Integer> current = new LinkedHashMap<>();
        for (RuleRow row : dao.findAllDetailed()) {
            current.put(row.key(), row.value());
        }
        int changed = 0;
        for (Map.Entry<String, Integer> e : parsed.entrySet()) {
            Integer now = current.get(e.getKey());
            // 저장된 값이 없거나(기본값이 쓰이는 중) 같은 값이면 쓰지 않는다. 저장된 값이 있으면 그 값과 비교한다.
            int shown = now == null ? ScoringRules.defaultOf(e.getKey()) : now;
            if (shown != e.getValue()) {
                dao.upsert(e.getKey(), e.getValue());
                changed++;
            }
        }
        ScoringRules.refresh();
        return changed;
    }

    private static final List<String> GROUPS = List.of(
            "기술 사다리 점수", "복습", "프로젝트·글 업데이트 · 트렌딩 학습", "일일 문제 풀이", "연속 풀이 보너스");

    static String groupOf(String key) {
        if (key.startsWith("REVIEW_")) {
            return GROUPS.get(1);
        }
        if (key.startsWith("PROJECT_UPDATE") || key.startsWith("ARTICLE_UPDATE") || key.startsWith("TREND_STUDY")
                || key.startsWith("UPKEEP_")) {
            return GROUPS.get(2);
        }
        if (key.startsWith("DAILY_")) {
            return GROUPS.get(3);
        }
        if (key.startsWith("STREAK_")) {
            return GROUPS.get(4);
        }
        return GROUPS.get(0);
    }
}
