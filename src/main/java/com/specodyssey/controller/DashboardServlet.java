package com.specodyssey.controller;

import com.specodyssey.dao.DdayAlertDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.LevelTierDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.DailyMissionViewDto;
import com.specodyssey.dto.DdayAlertDto;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.service.GapAnalysisService;
import com.specodyssey.service.RoadmapService;
import com.specodyssey.service.ScoreService;
import com.specodyssey.service.SpecScoreService;
import com.specodyssey.service.DailyMissionService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 대시보드 화면. 관련 요구사항: FR-41~44
 * 화면설계 PDF "9. 대시보드" 기준 — 처음엔 고정 예시 데이터로만 만들어져 있었다(팀 지시,
 * 화면 우선). 2026-09-30, 이미 만들어진 각 기능 서비스(로드맵·점수·미션·격차분석·D-day)를
 * 읽기 전용으로 모아 실제 데이터로 교체한다.
 *
 * "스펙 완성도" 점수(FR-41, SPEC_SCORE_HISTORY)는 SpecScoreService가 계산한다(2026-09-30 연결).
 * 공식과 설계 판단은 SpecScoreService 클래스 주석 참고.
 */
@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(DashboardServlet.class.getName());

    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();
    private final RoadmapService roadmapService = new RoadmapService();
    private final ScoreService scoreService = new ScoreService();
    private final SpecScoreService specScoreService = new SpecScoreService();
    private final LevelTierDao levelTierDao = new LevelTierDao();
    private final DailyMissionService dailyMissionService = new DailyMissionService();
    private final DdayAlertDao ddayAlertDao = new DdayAlertDao();
    private final GapAnalysisService gapAnalysisService = new GapAnalysisService();
    private final SkillDao skillDao = new SkillDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        try {
            UserDto user = userDao.findById(userId);
            loadHeaderAndJourney(req, user);
            loadSpecCounts(req, userId);
            loadScoreAndTier(req, userId);
            loadDailyMissions(req, userId);
            loadUpcomingDdays(req, userId);
            loadGapAnalysis(req, userId);
        } catch (SQLException e) {
            throw new ServletException("대시보드를 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req, resp);
    }

    // 헤더 문구("목표 직무 X · 지금은 Y 단계") + "여정 지도" 카드 — 로드맵 담당 영역.
    private void loadHeaderAndJourney(HttpServletRequest req, UserDto user) throws SQLException {
        if (user.getDesiredJobId() != null) {
            JobDto job = jobDao.findById(user.getDesiredJobId());
            req.setAttribute("desiredJobName", job == null ? null : job.getJobName());
        }

        RoadmapDto roadmap = roadmapService.getPrimaryRoadmap(user.getId());
        if (roadmap == null) {
            return;
        }
        List<RoadmapStepDto> steps = roadmapService.getSteps(roadmap.getId());
        RoadmapService.RoadmapProgress progress = roadmapService.computeProgress(steps);
        req.setAttribute("journeyProgress", progress);

        RoadmapService.TierProgress currentTier = progress.getCurrentTier();
        req.setAttribute("currentTier", currentTier);
        if (currentTier != null) {
            steps.stream()
                    .filter(s -> currentTier.getTier().equals(s.getTier()) && !s.isCompleted())
                    .findFirst()
                    .ifPresent(step -> req.setAttribute("nextStepReason", step.getReason()));
        }
    }

    // "스펙 완성도" 카드 — 개수 + 종합 점수(SpecScoreService). 오늘 스냅샷이 없으면 하나 남긴다.
    private void loadSpecCounts(HttpServletRequest req, Long userId) throws SQLException {
        long certCount = userSpecDao.findByUserId(userId).stream()
                .filter(s -> "CERT".equals(s.getSpecType())).count();
        req.setAttribute("certCount", certCount);
        req.setAttribute("projectCount", (long) userProjectDao.findByUserId(userId).size());
        req.setAttribute("skillCount", (long) userSkillDao.findByUserId(userId).size());

        // 스냅샷 실패(예: DB 순간 오류)가 대시보드 전체를 막으면 안 된다 — 여기서만 삼키고
        // 완성도는 스냅샷 여부와 무관하게 항상 최신 값을 즉석 계산해서 보여준다.
        try {
            specScoreService.snapshotIfNotYetToday(userId);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "SPEC_SCORE_HISTORY 스냅샷 기록 실패 — 화면은 계속 보여준다", e);
        }
        req.setAttribute("completenessScore", specScoreService.computeCompletenessScore(userId));
    }

    // "나의 등급" 카드 — 다음 등급까지 남은 점수는 LEVEL_TIER를 min_score 순으로 훑어 계산한다.
    // 진행률(%)도 여기서 계산해서 넘긴다 — JSP는 출력만 한다(claude.md: JSP에 계산 금지).
    private void loadScoreAndTier(HttpServletRequest req, Long userId) throws SQLException {
        UserScoreSummaryDto summary = scoreService.getSummary(userId);
        int totalScore = summary.getTotalScore() == null ? 0 : summary.getTotalScore();
        req.setAttribute("totalScore", totalScore);
        req.setAttribute("streakCount", summary.getStreakCount() == null ? 0 : summary.getStreakCount());

        LevelTierDto currentTier = scoreService.getTier(summary.getCurrentTierId());
        req.setAttribute("currentScoreTier", currentTier);
        req.setAttribute("tierLogoPath", scoreService.getTierLogoPath(summary.getCurrentTierId()));

        List<LevelTierDto> allTiers = levelTierDao.findAll().stream()
                .sorted(Comparator.comparing(LevelTierDto::getMinScore))
                .collect(Collectors.toList());
        LevelTierDto nextTier = null;
        for (LevelTierDto tier : allTiers) {
            if (currentTier != null && tier.getMinScore() > currentTier.getMinScore()) {
                nextTier = tier;
                break;
            }
        }
        if (nextTier != null && currentTier != null) {
            req.setAttribute("nextTierName", nextTier.getTierName());
            req.setAttribute("nextTierTitle", nextTier.getTitleName());
            req.setAttribute("pointsToNextTier", Math.max(0, nextTier.getMinScore() - totalScore));
            int span = nextTier.getMinScore() - currentTier.getMinScore();
            int done = totalScore - currentTier.getMinScore();
            int percent = span <= 0 ? 100 : Math.max(0, Math.min(100, done * 100 / span));
            req.setAttribute("tierProgressPercent", percent);
        } else {
            req.setAttribute("tierProgressPercent", 100);
        }
    }

    private void loadDailyMissions(HttpServletRequest req, Long userId) throws SQLException {
        DailyMissionService.TodayMissions today = dailyMissionService.getOrAssignToday(userId);
        List<DailyMissionViewDto> missions = today.getMissions();
        req.setAttribute("dailyMissions", missions);
        req.setAttribute("dailyMissionDone", today.getDoneCount());
        int total = missions.size();
        req.setAttribute("dailyMissionPercent", total == 0 ? 0 : (int) (today.getDoneCount() * 100 / total));
    }

    /** 화면에 필요한 만큼만 담은 D-day 표시용 뷰 — 남은 일수를 미리 계산해서 넘긴다. */
    public static final class DdayView {
        private final String title;
        private final long daysLeft;

        public DdayView(String title, long daysLeft) {
            this.title = title;
            this.daysLeft = daysLeft;
        }

        public String getTitle() {
            return title;
        }

        public long getDaysLeft() {
            return daysLeft;
        }
    }

    // "다가오는 일정" 카드 — 지난 날짜는 제외하고 가까운 순 3개만.
    private void loadUpcomingDdays(HttpServletRequest req, Long userId) throws SQLException {
        LocalDate today = LocalDate.now();
        List<DdayView> upcoming = ddayAlertDao.findByUserId(userId).stream()
                .filter(a -> a.getTargetDate() != null && !a.getTargetDate().isBefore(today))
                .sorted(Comparator.comparing(DdayAlertDto::getTargetDate))
                .limit(3)
                .map(a -> new DdayView(a.getTitle(), ChronoUnit.DAYS.between(today, a.getTargetDate())))
                .collect(Collectors.toList());
        req.setAttribute("upcomingDdays", upcoming);
    }

    // "부족한 필수 역량" 카드 — 가장 최근 격차 분석의 MISSING 항목.
    private void loadGapAnalysis(HttpServletRequest req, Long userId) throws SQLException {
        GapAnalysisDto latest = gapAnalysisService.getLatest(userId);
        req.setAttribute("hasGapAnalysis", latest != null);
        if (latest == null) {
            return;
        }
        List<GapAnalysisItemDto> items = gapAnalysisService.getItems(latest.getId());
        List<String> missingNames = new ArrayList<>();
        int metCount = 0;
        for (GapAnalysisItemDto item : items) {
            if ("MET".equals(item.getStatus())) {
                metCount++;
                continue;
            }
            SkillDto skill = skillDao.findById(item.getSkillId());
            if (skill != null) {
                missingNames.add(skill.getSkillName());
            }
        }
        Collections.sort(missingNames);
        req.setAttribute("missingSkillNames", missingNames);
        req.setAttribute("gapMetCount", metCount);
        req.setAttribute("gapTotalCount", items.size());
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
