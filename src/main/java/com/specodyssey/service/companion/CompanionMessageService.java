package com.specodyssey.service.companion;

import com.specodyssey.dao.MissionDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.NotificationDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.service.GlanceService;
import com.specodyssey.service.MissionStreakService;
import com.specodyssey.service.NotificationService;
import com.specodyssey.service.RoadmapService;
import com.specodyssey.service.ScoreService;
import com.specodyssey.service.TrendWidgetService;
import com.specodyssey.dto.TrendTechDto;
import com.specodyssey.util.AppClock;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 데스크톱 캐릭터가 지금 말할 것들 — 기존 서비스(알림·미션·연속 기록·한눈에 보기·점수)에서 모아 문장으로 만든다.
 * 무엇을 말할지는 전부 여기서 정하므로 문구를 바꿔도 exe를 다시 배포할 필요가 없다.
 *
 * 말마다 key를 붙인다 — 상황이 같으면 같은 key라 캐릭터가 같은 말을 반복하지 않고, 사용자가 그 일을 하면 key가 바뀌거나
 * 사라져서 캐릭터가 말풍선을 스스로 끈다 (예: 미션을 하나 풀면 mission-left:날짜:2 → :1).
 * 우선순위: 경고(WARN) > 사이트 알림(NOTICE) > 할 일(TODO). 칭찬(등급 상승)은 exe가 tier 변화로 직접 띄운다.
 */
public class CompanionMessageService {

    public static final String WARN = "WARN";
    public static final String NOTICE = "NOTICE";
    public static final String TODO = "TODO";
    public static final String PRAISE = "PRAISE";

    /** 이 시각부터 오늘 미션을 안 했으면 연속 기록 경고 */
    static final int EVENING_HOUR = 20;
    public static final int EVENING_HOUR_DEFAULT = EVENING_HOUR;
    /** D-day 경고를 띄우기 시작하는 남은 날 */
    static final int DDAY_WARN_DAYS = 3;
    private static final int NOTICE_LIMIT = 5;
    private static final int TREND_COUNT = 5;
    /** 로드맵 티어를 끝낸 뒤 이 날 수 안에만 칭찬한다 (처음 연결했을 때 옛날 일을 칭찬하지 않게) */
    static final int TIER_PRAISE_DAYS = 3;

    private final NotificationService notificationService = new NotificationService();
    private final MissionStreakService streakService = new MissionStreakService();
    private final MissionDao missionDao = new MissionDao();
    private final RoadmapService roadmapService = new RoadmapService();
    private final GlanceService glanceService = new GlanceService();
    private final ScoreService scoreService = new ScoreService();
    private final UserDao userDao = new UserDao();
    private final TrendWidgetService trendService = new TrendWidgetService();

    /** 말 하나. path는 앱 안 경로('/'로 시작) — 서블릿이 서버 주소를 붙여 준다 */
    public record Message(String key, String kind, String label, String text, String linkText, String path,
                          Long notificationId) {
    }

    /** 캐릭터 그림·이름 표시용 — index는 등급 순서 1~5 (tier1~5 그림) */
    public record Tier(int index, String name, String title, int score, String nextName, int pointsToNext) {
    }

    /** 트렌드 기술 하나 — 캐릭터가 한가할 때 작은 말풍선으로 돌려 보여 준다 */
    public record Trend(String name, String summary, String url) {
    }

    public record Snapshot(String userName, Tier tier, List<Message> messages, List<Trend> trends, String summary) {
    }

    public Snapshot load(Long userId) throws SQLException {
        return load(userId, EVENING_HOUR);
    }

    /** @param eveningHour 이 시각부터 연속 기록 경고 (캐릭터 설정에서 바꾼다, 기본 20시) */
    public Snapshot load(Long userId, int eveningHour) throws SQLException {
        UserDto user = userDao.findById(userId);
        String name = user == null ? null : (user.getName() != null ? user.getName() : user.getLoginId());
        LocalDateTime now = AppClock.now();
        LocalDate today = now.toLocalDate();

        List<Message> messages = new ArrayList<>();

        // ---- 사이트 알림 (댓글·답글·면접관 열람·D-day·미션)
        for (NotificationDto n : notificationService.findRecentUnread(userId, NOTICE_LIMIT)) {
            messages.add(new Message("noti:" + n.getId(), NOTICE, noticeLabel(n.getNotiType()), n.getMessage(),
                    "보러 가기", NotificationService.safeLink(n.getLinkUrl()), n.getId()));
        }

        // ---- 오늘 미션 · 연속 기록
        MissionStreakService.StreakView streak = streakService.getStreakView(userId);
        int[] counts;
        try (Connection conn = DBUtil.getConnection()) {
            Map<LocalDate, int[]> byDate = missionDao.countMissionsByDate(conn, userId, today, today);
            counts = byDate.get(today);
        }
        int evening = eveningHour < 0 || eveningHour > 23 ? EVENING_HOUR : eveningHour;
        messages.addAll(missionMessages(today, now.getHour(), evening, streak.streak(), streak.todayDone(), counts));
        Message streakPraise = streakPraise(today, streak.streak(), streak.todayDone());
        if (streakPraise != null) {
            messages.add(streakPraise);
        }

        // ---- 로드맵 다음 단계 · 가까운 D-day (대시보드 "한눈에 보기"와 같은 기준)
        RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(userId);
        List<RoadmapStepDto> steps = roadmap == null ? List.of() : roadmapService.getSteps(roadmap.getId());
        GlanceService.Glance glance = glanceService.load(userId, steps,
                roadmap == null ? null : roadmapService.computeProgress(steps));
        if (glance.getNextStepText() != null) {
            messages.add(new Message("step:" + glance.getNextStepText(), TODO, "로드맵 · " + glance.getNextStepType(),
                    "다음 단계: " + glance.getNextStepText(), "로드맵 보기", "/roadmap", null));
        } else if (roadmap == null) {
            messages.add(new Message("roadmap-none", TODO, "로드맵", "아직 로드맵이 없어요. 목표 직무로 만들어 볼까요?",
                    "로드맵 만들기", "/roadmap", null));
        }
        for (GlanceService.UpcomingDday d : glance.getDdays()) {
            if (d.daysLeft() <= DDAY_WARN_DAYS) {
                String when = d.daysLeft() == 0 ? "오늘이에요" : "D-" + d.daysLeft();
                messages.add(new Message("dday:" + d.title() + ":" + d.daysLeft(), WARN, "D-day",
                        d.title() + " — " + when + "!", "일정 보기", "/dday", null));
            }
        }

        if (roadmap != null) {
            messages.addAll(tierPraises(roadmap.getId(), steps, now));
        }

        List<Trend> trends = new ArrayList<>();
        Long jobId = user == null ? null : user.getDesiredJobId();
        try {
            for (TrendTechDto t : trendService.forJob(jobId, TREND_COUNT).items()) {
                trends.add(new Trend(t.getTechName(), t.getSummary(), t.getSourceUrl()));
            }
        } catch (SQLException | RuntimeException e) {
            // 트렌드는 없어도 그만 — 나머지 말은 그대로 준다
        }
        return new Snapshot(name, tier(userId), ordered(messages), trends, summary(counts, glance));
    }

    /** 하루 요약 한 줄 — 캐릭터가 정한 시각(기본 9시)에 한 번 말한다 */
    static String summary(int[] counts, GlanceService.Glance glance) {
        List<String> parts = new ArrayList<>();
        int assigned = counts == null ? 0 : counts[0];
        int done = counts == null ? 0 : counts[1];
        parts.add(assigned == 0 ? "오늘의 미션 3문제" : done >= assigned ? "미션 완료 👍" : "미션 " + (assigned - done) + "개 남음");
        if (glance != null && glance.getNextStepText() != null) {
            parts.add("로드맵: " + glance.getNextStepText());
        }
        if (glance != null && !glance.getDdays().isEmpty()) {
            GlanceService.UpcomingDday d = glance.getDdays().get(0);
            parts.add((d.daysLeft() == 0 ? "D-day " : "D-" + d.daysLeft() + " ") + d.title());
        }
        return "오늘 할 일 — " + String.join(" · ", parts);
    }

    /** 연속 7일·30일을 채운 날 칭찬 */
    static Message streakPraise(LocalDate today, int streak, boolean todayDone) {
        if (!todayDone || (streak != 7 && streak != 30)) {
            return null;
        }
        return new Message("streak-praise:" + today + ":" + streak, PRAISE, "연속 기록",
                "연속 " + streak + "일 달성! " + (streak == 7 ? "일주일 동안 매일 했어요." : "한 달 내내 해냈어요!"),
                "대시보드", "/dashboard", null);
    }

    /** 로드맵 티어(입문·핵심·심화·전문가)를 다 끝낸 지 TIER_PRAISE_DAYS일 안이면 칭찬 */
    static List<Message> tierPraises(Long roadmapId, List<RoadmapStepDto> steps, LocalDateTime now) {
        List<Message> out = new ArrayList<>();
        for (String tier : List.of("ENTRY", "CORE", "ADVANCED", "EXPERT")) {
            List<RoadmapStepDto> inTier = steps.stream()
                    .filter(s -> tier.equals(s.getTier()) && !s.isUpkeep())
                    .toList();
            if (inTier.isEmpty() || inTier.stream().anyMatch(s -> !s.isCompleted())) {
                continue;
            }
            LocalDateTime last = inTier.stream().map(RoadmapStepDto::getCompletedAt)
                    .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            if (last != null && !last.isBefore(now.minusDays(TIER_PRAISE_DAYS))) {
                out.add(new Message("tier-done:" + roadmapId + ":" + tier, PRAISE, "로드맵",
                        tierLabel(tier) + " 단계를 모두 끝냈어요! 다음 단계로 가 볼까요?", "로드맵 보기", "/roadmap", null));
            }
        }
        return out;
    }

    static String tierLabel(String tier) {
        return switch (tier) {
            case "ENTRY" -> "입문";
            case "CORE" -> "핵심";
            case "ADVANCED" -> "심화";
            default -> "전문가";
        };
    }

    /**
     * 오늘 미션 관련 말. counts = [배정 수, 끝낸 수] (배정 전이면 null).
     * 저녁에 연속 기록이 걸려 있으면 경고 하나로, 아니면 남은 개수를 할 일로.
     */
    static List<Message> missionMessages(LocalDate today, int hour, int eveningHour, int streak, boolean todayDone, int[] counts) {
        List<Message> list = new ArrayList<>();
        if (todayDone) {
            return list;
        }
        int assigned = counts == null ? 0 : counts[0];
        int done = counts == null ? 0 : counts[1];
        if (hour >= eveningHour && streak > 0) {
            list.add(new Message("streak:" + today, WARN, "연속 기록",
                    "연속 " + streak + "일째! 오늘 미션을 끝내면 기록이 이어져요", "미션 하러 가기", "/mission", null));
            return list;
        }
        if (assigned == 0) {
            list.add(new Message("mission-new:" + today, TODO, "오늘의 미션", "오늘의 미션 3문제가 기다리고 있어요",
                    "미션 하러 가기", "/mission", null));
        } else if (done < assigned) {
            int left = assigned - done;
            list.add(new Message("mission-left:" + today + ":" + left, TODO, "오늘의 미션",
                    "오늘 미션 " + left + "개 남았어요 (" + done + "/" + assigned + ")", "미션 하러 가기", "/mission", null));
        }
        return list;
    }

    /** 경고 > 알림 > 칭찬 > 할 일, 같은 종류는 들어온 순서 그대로 */
    static List<Message> ordered(List<Message> messages) {
        List<Message> copy = new ArrayList<>(messages);
        copy.sort(Comparator.comparingInt(m -> rank(m.kind())));
        return copy;
    }

    private static int rank(String kind) {
        return switch (kind) {
            case WARN -> 0;
            case NOTICE -> 1;
            case PRAISE -> 2;
            default -> 3;
        };
    }

    static String noticeLabel(String notiType) {
        if (notiType == null) {
            return "알림";
        }
        return switch (notiType) {
            case "COMMENT" -> "새 댓글";
            case "REPLY" -> "새 답글";
            case "SHARE_VIEW" -> "면접관 열람";
            case "DDAY" -> "D-day";
            case "MISSION" -> "오늘의 미션";
            default -> "알림";
        };
    }

    /** 지금 등급 — 캐릭터 그림(tier1~5)과 같은 기준. "오셍이들" 화면이 내 캐릭터를 표시할 때 쓴다 */
    public Tier currentTier(Long userId) throws SQLException {
        return tier(userId);
    }

    private Tier tier(Long userId) throws SQLException {
        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        int score = summary == null || summary.getTotalScore() == null ? 0 : summary.getTotalScore();
        List<LevelTierDto> tiers = new ArrayList<>(scoreService.getAllTiers());
        tiers.sort(Comparator.comparing(LevelTierDto::getMinScore));
        int index = 1;
        LevelTierDto current = null;
        LevelTierDto next = null;
        for (int i = 0; i < tiers.size(); i++) {
            LevelTierDto t = tiers.get(i);
            if (score >= t.getMinScore()) {
                current = t;
                index = i + 1;
                next = i + 1 < tiers.size() ? tiers.get(i + 1) : null;
            }
        }
        return new Tier(Math.max(1, Math.min(5, index)),
                current == null ? null : current.getTierName(),
                current == null ? null : current.getTitleName(),
                score,
                next == null ? null : next.getTierName(),
                next == null ? 0 : Math.max(0, next.getMinScore() - score));
    }
}
