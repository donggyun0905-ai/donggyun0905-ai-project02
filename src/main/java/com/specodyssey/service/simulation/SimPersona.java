package com.specodyssey.service.simulation;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 시뮬레이션 계정의 생활 패턴. 시작할 때 고르거나(parse) 고르지 않으면 무작위(random).
 * 날마다의 행동은 (판마다 새로 뽑는 seed, 날 번호)로 만든 난수 — 판마다 결과가 다르고, 같은 판 안에서는
 * 일시정지 후 이어 해도 같은 흐름이 이어진다.
 */
public enum SimPersona {

    /** 성실형 — 거의 매일 하고 잘 맞힌다. 로드맵 단계도 사흘에 하나 */
    DILIGENT("성실형", 0.95, 0.90, 3, 24),
    /** 보통형 — 다섯 날 중 하루쯤 빠진다 */
    STEADY("보통형", 0.80, 0.75, 5, 16),
    /** 작심삼일형 — 3일 하고 2일 쉬기를 되풀이, 가끔 그마저 빠진다 */
    ON_OFF("작심삼일형", 0.90, 0.60, 6, 10);

    private final String label;
    private final double activeChance;
    private final double correctChance;
    private final int stepEveryDays;
    /** 0점일 때 하루 점수(어림값) — 점수가 오를수록 등급별 문제 점수·로드맵 점수가 커져 빨라진다. 시작 날짜를 잡는 데만 쓴다 */
    private final int pointsPerDay;

    SimPersona(String label, double activeChance, double correctChance, int stepEveryDays, int pointsPerDay) {
        this.label = label;
        this.activeChance = activeChance;
        this.correctChance = correctChance;
        this.stepEveryDays = stepEveryDays;
        this.pointsPerDay = pointsPerDay;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 지금 점수에서 목표 점수까지 어림잡은 날 수 (1 ~ maxDays). 하루 점수 = 기본 × (1 + 점수/1500) —
     * 실제로 돌려 본 성실형이 0→500점 21일, 0→3,488점 70일 걸린 것에 맞췄다.
     */
    public int estimateDays(int currentScore, int targetScore, int maxDays) {
        double score = Math.max(0, currentScore);
        int days = 0;
        while (score < targetScore && days < maxDays) {
            score += pointsPerDay * (1 + score / 1500.0);
            days++;
        }
        return Math.max(1, days);
    }

    /** 성향을 고르지 않았을 때 — 셋 중 하나를 무작위로 */
    public static SimPersona random() {
        SimPersona[] all = values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }

    /**
     * 화면에서 고른 성향. 비었거나 RANDOM이면 null(무작위로 정하라는 뜻).
     * @throws IllegalArgumentException 없는 성향 이름
     */
    public static SimPersona parse(String name) {
        if (name == null || name.isBlank() || "RANDOM".equals(name)) {
            return null;
        }
        return valueOf(name);
    }

    public static SimPersona fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return STEADY;
        }
    }

    /** 이 판(seed)의 day번째 날(0부터) 행동을 정하는 난수 — 같은 판·같은 날이면 같은 순서 */
    public static Random randomFor(long seed, int day) {
        return new Random(seed * 1_000_003L + day * 7_919L);
    }

    /** 이날 앱을 켜서 미션을 했는지 */
    public boolean isActive(int day, Random random) {
        if (this == ON_OFF && day % 5 >= 3) {
            return false;
        }
        return random.nextDouble() < activeChance;
    }

    /** 문제 하나를 맞혔는지 */
    public boolean solves(Random random) {
        return random.nextDouble() < correctChance;
    }

    /** 이날 로드맵 다음 단계를 끝내는 날인지 (하는 날 중에서) */
    public boolean completesStep(int day) {
        return day % stepEveryDays == stepEveryDays - 1;
    }
}
