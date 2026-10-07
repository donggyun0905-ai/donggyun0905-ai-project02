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

    /** 이 시각부터 오늘 미션을 안 했으면 연속 기록 경고 */
    static final int EVENING_HOUR = 20;
    /** D-day 경고를 띄우기 시작하는 남은 날 */
    static final int DDAY_WARN_DAYS = 3;
    private static final int NOTICE_LIMIT = 5;

    private final NotificationService notificationService = new NotificationService();
    private final MissionStreakService streakService = new MissionStreakService();
    private final MissionDao missionDao = new MissionDao();
    private final RoadmapService roadmapService = new RoadmapService();
    private final GlanceService glanceService = new GlanceService();
    private final ScoreService scoreService = new ScoreService();
    private final UserDao userDao = new UserDao();

    /** 말 하나. path는 앱 안 경로('/'로 시작) — 서블릿이 서버 주소를 붙여 준다 */
    public record Message(String key, String kind, String label, String text, String linkText, String path,
                          Long notificationId) {
    }

    /** 캐릭터 그림·이름 표시용 — index는 등급 순서 1~5 (tier1~5 그림) */
    public record Tier(int index, String name, String title, int score, String nextName, int pointsToNext) {
    }

    public record Snapshot(String userName, Tier tier, List<Message> messages) {
    }

    public Snapshot load(Long userId) throws SQLException {
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
        messages.addAll(missionMessages(today, now.getHour(), streak.streak(), streak.todayDone(), counts));

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

        return new Snapshot(name, tier(userId), ordered(messages));
    }

    /**
     * 오늘 미션 관련 말. counts = [배정 수, 끝낸 수] (배정 전이면 null).
     * 저녁에 연속 기록이 걸려 있으면 경고 하나로, 아니면 남은 개수를 할 일로.
     */
    static List<Message> missionMessages(LocalDate today, int hour, int streak, boolean todayDone, int[] counts) {
        List<Message> list = new ArrayList<>();
        if (todayDone) {
            return list;
        }
        int assigned = counts == null ? 0 : counts[0];
        int done = counts == null ? 0 : counts[1];
        if (hour >= EVENING_HOUR && streak > 0) {
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

    /** 경고 > 알림 > 할 일, 같은 종류는 들어온 순서 그대로 */
    static List<Message> ordered(List<Message> messages) {
        List<Message> copy = new ArrayList<>(messages);
        copy.sort(Comparator.comparingInt(m -> rank(m.kind())));
        return copy;
    }

    private static int rank(String kind) {
        return switch (kind) {
            case WARN -> 0;
            case NOTICE -> 1;
            default -> 2;
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
