package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.DdayAlertDao;
import com.specodyssey.dao.LevelTierDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.DdayAlertDto;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserScoreSummaryDto;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 왼쪽 위젯 "한눈에 보기" — 다가오는 D-day, 로드맵의 지금 할 일, 다음 등급까지 남은 점수를 한 상자에 모은다
 * (2026-10-05 사용자 요청: 서류 보관함 아래가 비어 보여 세 가지를 한꺼번에 보는 위젯으로).
 * 화면은 출력만 하도록 문구 다듬기·퍼센트 계산을 여기서 끝낸다.
 */
public class GlanceService {

    static final int DDAY_COUNT = 3;
    static final int STEP_TEXT_MAX = 42;

    // JSP의 EL이 읽을 수 있게 getter를 같이 둔다 — Tomcat 10.1(BeanELResolver)은 getX()만, Tomcat 11은 x()만 찾는다
    public record UpcomingDday(String title, long daysLeft) {
        public String getTitle() { return title; }
        public long getDaysLeft() { return daysLeft; }
    }

    /** nextStep* 가 비어 있으면 로드맵이 없거나 다 끝낸 상태, nextTierName이 비어 있으면 최고 등급이다. */
    public record Glance(List<UpcomingDday> ddays, String nextStepType, String nextStepText,
                         String tierName, String tierTitle, int totalScore,
                         String nextTierName, int pointsToNextTier, int tierPercent) {
        public List<UpcomingDday> getDdays() { return ddays; }
        public String getNextStepType() { return nextStepType; }
        public String getNextStepText() { return nextStepText; }
        public String getTierName() { return tierName; }
        public String getTierTitle() { return tierTitle; }
        public int getTotalScore() { return totalScore; }
        public String getNextTierName() { return nextTierName; }
        public int getPointsToNextTier() { return pointsToNextTier; }
        public int getTierPercent() { return tierPercent; }
    }

    private final DdayAlertDao ddayAlertDao = new DdayAlertDao();
    private final LevelTierDao levelTierDao = new LevelTierDao();
    private final RoadmapService roadmapService = new RoadmapService();
    private final ScoreService scoreService = new ScoreService();
    private final CertificationDao certificationDao = new CertificationDao();
    private final SkillDao skillDao = new SkillDao();

    public Glance load(Long userId) throws SQLException {
        LocalDate today = LocalDate.now();
        List<UpcomingDday> ddays = ddayAlertDao.findByUserId(userId).stream()
                .filter(a -> a.getTargetDate() != null && !a.getTargetDate().isBefore(today))
                .sorted(Comparator.comparing(DdayAlertDto::getTargetDate))
                .limit(DDAY_COUNT)
                .map(a -> new UpcomingDday(a.getTitle(), ChronoUnit.DAYS.between(today, a.getTargetDate())))
                .toList();

        RoadmapStepDto next = nextStep(userId);

        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        int totalScore = summary == null || summary.getTotalScore() == null ? 0 : summary.getTotalScore();
        LevelTierDto tier = scoreService.getTierForScore(totalScore);
        LevelTierDto nextTier = tier == null ? null : levelTierDao.findAll().stream()
                .filter(t -> t.getMinScore() > tier.getMinScore())
                .min(Comparator.comparing(LevelTierDto::getMinScore))
                .orElse(null);
        int percent = 100;
        int pointsLeft = 0;
        if (tier != null && nextTier != null) {
            int span = nextTier.getMinScore() - tier.getMinScore();
            percent = span <= 0 ? 100 : Math.max(0, Math.min(100, (totalScore - tier.getMinScore()) * 100 / span));
            pointsLeft = Math.max(0, nextTier.getMinScore() - totalScore);
        }

        return new Glance(ddays,
                next == null ? null : stepTypeLabel(next.getStepType()),
                next == null ? null : stepText(next),
                tier == null ? null : tier.getTierName(), tier == null ? null : tier.getTitleName(), totalScore,
                nextTier == null ? null : nextTier.getTierName(), pointsLeft, percent);
    }

    // 대시보드 "지금 할 일"과 같은 기준 — 지금 열린 티어에서 아직 안 끝낸 첫 단계
    private RoadmapStepDto nextStep(Long userId) throws SQLException {
        RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(userId);
        if (roadmap == null) {
            return null;
        }
        List<RoadmapStepDto> steps = roadmapService.getSteps(roadmap.getId());
        TierProgress current = roadmapService.computeProgress(steps).getCurrentTier();
        if (current == null) {
            return null;
        }
        return steps.stream()
                .filter(s -> current.getTier().equals(s.getTier()) && !s.isCompleted())
                .findFirst()
                .orElse(null);
    }

    // 좁은 칸에서 설명 문장이 잘려 정작 이름이 안 보이지 않게 — 자격증·기술 단계는 이름을 바로 보여준다
    private String stepText(RoadmapStepDto step) throws SQLException {
        if ("CERT".equals(step.getStepType()) && step.getCertificationId() != null) {
            CertificationDto cert = certificationDao.findById(step.getCertificationId());
            if (cert != null) {
                return cert.getCertName() + " 취득";
            }
        }
        if ("SKILL".equals(step.getStepType()) && step.getRelatedSkillId() != null) {
            SkillDto skill = skillDao.findById(step.getRelatedSkillId());
            if (skill != null) {
                return skill.getSkillName() + " " + skillAction(step.getTier());
            }
        }
        return shortReason(step.getReason());
    }

    // 기술 단계는 같은 기술이 티어마다 하는 일이 다르다(입문 노트 → 핵심 프로젝트 → 심화 업그레이드 → 전문가 글)
    static String skillAction(String tier) {
        if (tier == null) {
            return "익히기";
        }
        return switch (tier) {
            case "ENTRY" -> "공부노트 쓰기";
            case "CORE" -> "프로젝트에 써 보기";
            case "ADVANCED" -> "프로젝트 업그레이드";
            case "EXPERT" -> "기술 설명 글 쓰기";
            default -> "익히기";
        };
    }

    static String stepTypeLabel(String stepType) {
        if (stepType == null) {
            return "단계";
        }
        return switch (stepType) {
            case "CERT" -> "자격증";
            case "PROJECT" -> "프로젝트";
            case "SKILL" -> "기술";
            case "REVIEW" -> "복습";
            case "PROJECT_UPDATE" -> "프로젝트 업데이트";
            case "ARTICLE_UPDATE" -> "기술 글 업데이트";
            case "TREND_STUDY" -> "트렌딩 학습";
            default -> "단계";
        };
    }

    // 좁은 위젯에 맞게 — "아이디어: 제목 — 긴 설명"은 제목만, 나머지는 첫 문장을 잘라 보여준다
    static String shortReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        String text = reason.trim();
        if (text.startsWith(RoadmapGenerator.PROJECT_IDEA_PREFIX)) {
            text = text.substring(RoadmapGenerator.PROJECT_IDEA_PREFIX.length());
            int dash = text.indexOf(" — ");
            if (dash > 0) {
                text = text.substring(0, dash);
            }
        } else {
            int sentenceEnd = text.indexOf(". ");
            if (sentenceEnd > 0) {
                text = text.substring(0, sentenceEnd + 1);
            }
        }
        return text.length() <= STEP_TEXT_MAX ? text : text.substring(0, STEP_TEXT_MAX - 1) + "…";
    }
}
