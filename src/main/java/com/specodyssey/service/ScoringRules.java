package com.specodyssey.service;

import com.specodyssey.dao.ScoringRuleDao;

import java.sql.SQLException;
import java.util.Map;

/**
 * 점수·복습 주기 규칙을 SCORING_RULE 테이블에서 읽는다. 코드에 같은 기본값이 있어서 행이 없거나 테이블이
 * 아직 없거나 DB를 못 읽어도 기본값으로 그대로 동작한다. 읽은 값은 CACHE_MILLIS 동안 메모리에 둬서
 * 단계를 완료할 때마다 DB를 다시 읽지 않는다(값을 바꾸면 그 시간 안에 반영된다).
 * 일일 문제 풀이 점수(DAILY_POINTS_1..5)도 여기서 읽는다.
 */
final class ScoringRules {

    static final String LADDER_BUDGET = "LADDER_BUDGET";
    static final String WEIGHT_TIER_PREFIX = "WEIGHT_TIER_";
    static final String WEIGHT_IMPORTANCE_REQUIRED = "WEIGHT_IMPORTANCE_REQUIRED";
    static final String WEIGHT_IMPORTANCE_PREFERRED = "WEIGHT_IMPORTANCE_PREFERRED";
    static final String STEP_POINTS_MIN = "STEP_POINTS_MIN";
    static final String FIXED_STEP_POINTS = "FIXED_STEP_POINTS";
    static final String ROUND_SKILL_COUNT = "ROUND_SKILL_COUNT";
    static final String REVIEW_POINTS_BASE = "REVIEW_POINTS_BASE";
    static final String REVIEW_POINTS_DECAY = "REVIEW_POINTS_DECAY";
    static final String REVIEW_POINTS_MIN = "REVIEW_POINTS_MIN";
    static final String REVIEW_DAYS_PREFIX = "REVIEW_DAYS_";
    static final String PROJECT_UPDATE_DAYS = "PROJECT_UPDATE_DAYS";
    static final String ARTICLE_UPDATE_DAYS = "ARTICLE_UPDATE_DAYS";
    static final String TREND_STUDY_DAYS = "TREND_STUDY_DAYS";
    static final String PROJECT_UPDATE_POINTS_BASE = "PROJECT_UPDATE_POINTS_BASE";
    static final String ARTICLE_UPDATE_POINTS_BASE = "ARTICLE_UPDATE_POINTS_BASE";
    static final String TREND_STUDY_POINTS = "TREND_STUDY_POINTS";
    static final String UPKEEP_POINTS_DECAY = "UPKEEP_POINTS_DECAY";
    static final String UPKEEP_POINTS_MIN = "UPKEEP_POINTS_MIN";
    static final String STREAK_BONUS_PER_DAY = "STREAK_BONUS_PER_DAY";
    static final String STREAK_BONUS_MAX = "STREAK_BONUS_MAX";
    static final String STREAK_BONUS_DAY7 = "STREAK_BONUS_DAY7";
    static final String STREAK_BONUS_DAY30 = "STREAK_BONUS_DAY30";
    static final String DAILY_POINTS_PREFIX = "DAILY_POINTS_"; // 1..5 = 등급 순서별 문제 풀이 점수
    // D-day 계획(0/1 배낭)의 "무게" — 단계 하나를 끝내는 데 걸리는 평균 일수. 추정이라 관리자가 조정할 수 있게
    // 규칙으로 뒀다(2026-10-08). SKILL은 티어별로, 나머지는 단계 종류별로 본다.
    static final String EFFORT_DAYS_PREFIX = "EFFORT_DAYS_";

    private static final long CACHE_MILLIS = 60_000L;

    // sql/17_schema_scoring_rule.sql 의 기본값과 같아야 한다
    private static final Map<String, Integer> DEFAULTS = Map.ofEntries(
            Map.entry(LADDER_BUDGET, 2500),
            Map.entry("WEIGHT_TIER_ENTRY", 1),
            Map.entry("WEIGHT_TIER_CORE", 2),
            Map.entry("WEIGHT_TIER_ADVANCED", 2),
            Map.entry("WEIGHT_TIER_EXPERT", 3),
            Map.entry(WEIGHT_IMPORTANCE_REQUIRED, 2),
            Map.entry(WEIGHT_IMPORTANCE_PREFERRED, 1),
            Map.entry(STEP_POINTS_MIN, 5),
            Map.entry(FIXED_STEP_POINTS, 100),
            Map.entry(ROUND_SKILL_COUNT, 5),
            Map.entry(REVIEW_POINTS_BASE, 40),
            Map.entry(REVIEW_POINTS_DECAY, 10),
            Map.entry(REVIEW_POINTS_MIN, 5),
            Map.entry("REVIEW_DAYS_ENTRY", 30),
            Map.entry("REVIEW_DAYS_CORE", 60),
            Map.entry("REVIEW_DAYS_ADVANCED", 90),
            Map.entry("REVIEW_DAYS_EXPERT", 120),
            Map.entry(PROJECT_UPDATE_DAYS, 90),
            Map.entry(ARTICLE_UPDATE_DAYS, 150),
            Map.entry(TREND_STUDY_DAYS, 30),
            Map.entry(PROJECT_UPDATE_POINTS_BASE, 60),
            Map.entry(ARTICLE_UPDATE_POINTS_BASE, 60),
            Map.entry(TREND_STUDY_POINTS, 40),
            Map.entry(UPKEEP_POINTS_DECAY, 10),
            Map.entry(UPKEEP_POINTS_MIN, 5),
            Map.entry("DAILY_POINTS_1", 6),
            Map.entry("DAILY_POINTS_2", 6),
            Map.entry("DAILY_POINTS_3", 8),
            Map.entry("DAILY_POINTS_4", 10),
            Map.entry("DAILY_POINTS_5", 12),
            Map.entry(STREAK_BONUS_PER_DAY, 2),
            Map.entry(STREAK_BONUS_MAX, 20),
            Map.entry(STREAK_BONUS_DAY7, 30),
            Map.entry(STREAK_BONUS_DAY30, 100),
            // 소요 일수 추정 — 입문은 공부 노트 하나, 핵심은 프로젝트에 적용, 심화는 업그레이드, 전문가는 글쓰기.
            // 자격증은 접수·시험 일정이 끼어 가장 길다. 복습은 이미 익힌 것을 다시 정리하는 것이라 짧다.
            Map.entry("EFFORT_DAYS_SKILL_ENTRY", 3),
            Map.entry("EFFORT_DAYS_SKILL_CORE", 7),
            Map.entry("EFFORT_DAYS_SKILL_ADVANCED", 5),
            Map.entry("EFFORT_DAYS_SKILL_EXPERT", 4),
            Map.entry("EFFORT_DAYS_CERT", 21),
            Map.entry("EFFORT_DAYS_PROJECT", 10),
            Map.entry("EFFORT_DAYS_REVIEW", 1),
            Map.entry("EFFORT_DAYS_DEFAULT", 3));

    private static final ScoringRuleDao DAO = new ScoringRuleDao();

    private static volatile Map<String, Integer> cached = Map.of();
    private static volatile long loadedAt;

    private ScoringRules() {
    }

    static int get(String key) {
        Integer fallback = DEFAULTS.get(key);
        if (fallback == null) {
            throw new IllegalArgumentException("알 수 없는 규칙 키: " + key);
        }
        Integer stored = load().get(key);
        // 0 이하의 값은 점수·주기를 망가뜨리므로(0으로 나누기·즉시 복습 폭주) 기본값으로 되돌린다.
        // 감쇠 폭만 0을 허용한다(감쇠 없음).
        if (stored == null || stored < 0 || (stored == 0 && !allowsZero(key))) {
            return fallback;
        }
        return stored;
    }

    static boolean allowsZero(String key) {
        return REVIEW_POINTS_DECAY.equals(key) || UPKEEP_POINTS_DECAY.equals(key)
                || STREAK_BONUS_PER_DAY.equals(key) || STREAK_BONUS_DAY7.equals(key) || STREAK_BONUS_DAY30.equals(key);
    }

    static boolean isKnown(String key) {
        return DEFAULTS.containsKey(key);
    }

    static int defaultOf(String key) {
        return DEFAULTS.get(key);
    }

    static java.util.Set<String> knownKeys() {
        return DEFAULTS.keySet();
    }

    // 값을 바꾼 직후(관리 도구·테스트) 바로 반영하고 싶을 때
    static void refresh() {
        loadedAt = 0L;
    }

    private static Map<String, Integer> load() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < CACHE_MILLIS) {
            return cached;
        }
        synchronized (ScoringRules.class) {
            if (now - loadedAt < CACHE_MILLIS) {
                return cached;
            }
            try {
                cached = DAO.findAll();
            } catch (SQLException e) {
                // 테이블이 아직 없거나 DB가 잠시 안 되면 기본값으로 계속 간다(마지막으로 읽은 값이 있으면 그대로 둔다).
            }
            loadedAt = now;
            return cached;
        }
    }
}
