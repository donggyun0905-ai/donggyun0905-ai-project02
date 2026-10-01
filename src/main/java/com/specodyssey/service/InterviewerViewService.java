package com.specodyssey.service;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareLinkViewLogDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 면접관 뷰(로그인 없이 공유 링크로 접근) — 토큰 검증 + 지원자가 공개 범위로 고른 데이터만
 * 조합해서 보여준다. 관련 요구사항: FR-81(이력 타임라인) · FR-85(토큰 검증) · NFR-9(열람 로그)
 *
 * FR-81 "이력 타임라인"과 SpecScoreService(FR-41·45·84)는 서로 다른 데이터를 다룬다 — 이 서비스는
 * USER_SPECS·USER_PROJECTS·USER_SKILLS를 직접 시간순으로 읽어 "뭘 했는지"를 보여주고,
 * SpecScoreService는 그 성취들을 종합한 "완성도 숫자"의 시계열만 다룬다(db-design.md 확인 완료).
 */
public class InterviewerViewService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final ShareLinkViewLogDao shareLinkViewLogDao = new ShareLinkViewLogDao();
    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final SkillDao skillDao = new SkillDao();
    private final SpecScoreService specScoreService = new SpecScoreService();

    public record TimelineItem(LocalDate sortDate, String dateLabel, String type, String title, String detail) {
    }

    public record SkillView(String name, String proficiency) {
    }

    public static final class ViewResult {
        private final ShareLinkDto link;
        private final UserDto user;
        private final String jobName;
        private final List<TimelineItem> timeline;
        private final List<SkillView> skills;
        private final SpecScoreService.GrowthSummary growthSummary;
        private final List<SpecScoreService.MonthlyScorePoint> growthSeries;

        public ViewResult(ShareLinkDto link, UserDto user, String jobName, List<TimelineItem> timeline,
                List<SkillView> skills, SpecScoreService.GrowthSummary growthSummary,
                List<SpecScoreService.MonthlyScorePoint> growthSeries) {
            this.link = link;
            this.user = user;
            this.jobName = jobName;
            this.timeline = timeline;
            this.skills = skills;
            this.growthSummary = growthSummary;
            this.growthSeries = growthSeries;
        }

        public ShareLinkDto getLink() {
            return link;
        }

        public UserDto getUser() {
            return user;
        }

        public String getJobName() {
            return jobName;
        }

        public List<TimelineItem> getTimeline() {
            return timeline;
        }

        public List<SkillView> getSkills() {
            return skills;
        }

        public SpecScoreService.GrowthSummary getGrowthSummary() {
            return growthSummary;
        }

        public List<SpecScoreService.MonthlyScorePoint> getGrowthSeries() {
            return growthSeries;
        }
    }

    /** 토큰이 유효하지 않으면(만료·비활성·존재하지 않음) null. 유효하면 열람 로그를 남기고 데이터를 모은다. */
    public ViewResult loadView(String token, String viewerIp) throws SQLException {
        ShareLinkDto link = shareLinkDao.findByToken(token);
        if (link == null) {
            return null;
        }

        ShareLinkViewLogDto viewLog = new ShareLinkViewLogDto();
        viewLog.setShareLinkId(link.getId());
        viewLog.setViewedAt(java.time.LocalDateTime.now());
        viewLog.setViewerIp(viewerIp);
        shareLinkViewLogDao.insert(viewLog);

        UserDto user = userDao.findById(link.getUserId());
        String jobName = null;
        if (user.getDesiredJobId() != null) {
            JobDto job = jobDao.findById(user.getDesiredJobId());
            jobName = job == null ? null : job.getJobName();
        }

        List<TimelineItem> timeline = link.isScopeBasic() ? buildTimeline(user) : List.of();
        List<SkillView> skills = link.isScopeSkills() ? buildSkills(user.getId()) : List.of();

        SpecScoreService.GrowthSummary growthSummary = null;
        List<SpecScoreService.MonthlyScorePoint> growthSeries = List.of();
        if (link.isScopeGrowth()) {
            growthSummary = specScoreService.getGrowthSummary(user.getId());
            growthSeries = specScoreService.getMonthlySeries(user.getId());
        }

        return new ViewResult(link, user, jobName, timeline, skills, growthSummary, growthSeries);
    }

    // 전공 1건(날짜 없음, 항상 맨 위) + 자격증(acquired_date) + 프로젝트(start_date~end_date)를
    // 시간순으로 합친다. 날짜가 없는 항목(전공)은 정렬에서 가장 먼저 오도록 LocalDate.MIN을 쓴다.
    private List<TimelineItem> buildTimeline(UserDto user) throws SQLException {
        List<TimelineItem> items = new ArrayList<>();
        if (user.getMajor() != null || user.getGrade() != null) {
            String major = user.getMajor() == null ? "" : user.getMajor();
            String grade = user.getGrade() == null ? "" : user.getGrade();
            items.add(new TimelineItem(LocalDate.MIN, "재학 중", "전공", "전공", (major + " " + grade).trim()));
        }
        for (UserSpecDto spec : userSpecDao.findByUserId(user.getId())) {
            if (!"CERT".equals(spec.getSpecType()) || spec.getAcquiredDate() == null) {
                continue;
            }
            items.add(new TimelineItem(spec.getAcquiredDate(), spec.getAcquiredDate().format(DATE_FORMAT),
                    "자격증", spec.getTitle() + " 취득", null));
        }
        for (UserProjectDto project : userProjectDao.findByUserId(user.getId())) {
            LocalDate sortDate = project.getStartDate() != null ? project.getStartDate() : project.getEndDate();
            if (sortDate == null) {
                continue;
            }
            String detail = project.getDescription();
            if (project.getTechStack() != null && !project.getTechStack().isBlank()) {
                detail = (detail == null ? "" : detail + " ") + "사용 기술: " + project.getTechStack();
            }
            items.add(new TimelineItem(sortDate, formatRange(project.getStartDate(), project.getEndDate()),
                    "프로젝트", project.getTitle(), detail));
        }
        items.sort(Comparator.comparing(TimelineItem::sortDate));
        return items;
    }

    private String formatRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return "";
        }
        if (start == null) {
            return "~ " + end.format(DATE_FORMAT);
        }
        if (end == null) {
            return start.format(DATE_FORMAT) + " ~";
        }
        return start.format(DATE_FORMAT) + " ~ " + end.format(DATE_FORMAT);
    }

    // skill_id가 아직 안 잡힌 수동 입력은 raw_input을 그대로 이름으로 보여준다.
    private List<SkillView> buildSkills(Long userId) throws SQLException {
        List<SkillView> views = new ArrayList<>();
        for (UserSkillDto userSkill : userSkillDao.findByUserId(userId)) {
            String name = userSkill.getRawInput();
            if (userSkill.getSkillId() != null) {
                SkillDto skill = skillDao.findById(userSkill.getSkillId());
                if (skill != null && skill.getSkillName() != null) {
                    name = skill.getSkillName();
                }
            }
            views.add(new SkillView(name, userSkill.getProficiency()));
        }
        return views;
    }
}
