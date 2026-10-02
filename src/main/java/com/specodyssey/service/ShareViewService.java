package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.SpecScoreHistoryDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.ProjectLinkDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.RoadmapDto;
import com.specodyssey.dto.RoadmapStepDto;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareLinkViewLogDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.ShareViewDto.TimelineItem;
import com.specodyssey.dto.SpecScoreHistoryDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.UrlRules;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * 면접관 뷰 — 공유 링크 토큰으로 지원자 이력을 읽는다. 관련 요구사항: FR-81 · 84 · 85, NFR-4 · 9
 * 면접관은 로그인하지 않으므로 접근 제어는 전부 여기서 한다: 토큰·활성·만료 확인 뒤,
 * 링크의 scope_*가 켜진 범위만 DB에서 읽는다.
 */
public class ShareViewService {

    private static final Logger LOG = Logger.getLogger(ShareViewService.class.getName());

    private static final int GROWTH_POINTS = 6; // 성장 그래프에 보여줄 최근 기록 수

    private static final Map<String, String> SPEC_TYPE_LABELS = Map.of(
            "CERT", "자격증", "LANGUAGE", "어학", "AWARD", "수상");
    private static final Map<String, String> PROFICIENCY_LABELS = Map.of(
            "BEGINNER", "입문", "INTERMEDIATE", "중급", "ADVANCED", "고급");

    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final ShareLinkViewLogDao viewLogDao = new ShareLinkViewLogDao();
    private final UserDao userDao = new UserDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final JobDao jobDao = new JobDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final ProjectLinkDao projectLinkDao = new ProjectLinkDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final SpecScoreHistoryDao specScoreHistoryDao = new SpecScoreHistoryDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();

    /**
     * @param viewerUserId 로그인한 사람이 열었으면 그 사용자 id, 아니면 null
     * @return 링크가 없거나, 공유가 중단됐거나, 만료됐거나, 지원자가 탈퇴했으면 null
     */
    public ShareViewDto loadView(String token, String viewerIp, Long viewerUserId) throws SQLException {
        if (token == null || token.isBlank()) {
            return null;
        }
        ShareLinkDto link = shareLinkDao.findByToken(token);
        ShareViewDto view = buildView(link);
        // 지원자 본인이 미리보기로 연 것은 열람 횟수에 넣지 않는다
        if (view != null && !link.getUserId().equals(viewerUserId)) {
            recordView(link.getId(), viewerIp);
        }
        return view;
    }

    /**
     * 면접관이 담아 둔 링크를 목록·비교 화면에서 다시 읽는다. 열람 기록은 남기지 않는다
     * (화면을 열 때마다 담아 둔 지원자 전원의 열람 횟수가 올라가면 안 된다).
     * @return 공유가 중단됐거나, 만료됐거나, 지원자가 탈퇴했으면 null
     */
    public ShareViewDto loadViewByLinkId(Long shareLinkId) throws SQLException {
        return buildView(shareLinkDao.findActiveById(shareLinkId));
    }

    /**
     * 공유 링크로 이력서 파일을 내려받을 때 — 링크가 유효하고, 지원자가 이 링크에 이력서 공개를 골랐고,
     * 올려 둔 이력서가 있을 때만 파일 정보를 돌려준다. 로그인하지 않은 사람도 호출하므로 조건을 모두 여기서 확인한다.
     * @return 조건에 하나라도 안 맞으면 null
     */
    public DocumentDto loadResume(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return null;
        }
        ShareLinkDto link = shareLinkDao.findByToken(token);
        if (link == null || !link.isScopeResume()) {
            return null;
        }
        UserDto user = userDao.findById(link.getUserId());
        return user == null ? null : findResume(user);
    }

    /**
     * 공유 링크로 자소서 파일을 내려받을 때 — loadResume과 같은 조건(유효한 링크 + 이 링크에 자소서 공개를 골랐음 +
     * 올려 둔 자소서가 있음)을 모두 여기서 확인한다.
     * @return 조건에 하나라도 안 맞으면 null
     */
    public DocumentDto loadCoverLetter(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return null;
        }
        ShareLinkDto link = shareLinkDao.findByToken(token);
        if (link == null || !link.isScopeCoverLetter()) {
            return null;
        }
        UserDto user = userDao.findById(link.getUserId());
        return user == null ? null : findProfileDocument(user, user.getCoverLetterDocumentId());
    }

    // 지원자가 지정한 이력서. 지정하지 않았거나 그 서류가 지워졌으면 null
    private DocumentDto findResume(UserDto user) throws SQLException {
        return findProfileDocument(user, user.getResumeDocumentId());
    }

    // 이력서·자소서 공통 — 본인이 올린 파일만 인정한다
    private DocumentDto findProfileDocument(UserDto user, Long documentId) throws SQLException {
        if (documentId == null) {
            return null;
        }
        DocumentDto document = documentDao.findById(documentId);
        return (document == null || !document.getUserId().equals(user.getId())) ? null : document;
    }

    // link는 이미 활성·만료 확인을 거친 것이어야 한다. 링크의 scope_*가 켜진 범위만 읽는다.
    private ShareViewDto buildView(ShareLinkDto link) throws SQLException {
        if (link == null) {
            return null;
        }
        // 탈퇴한 지원자의 링크가 살아 있어도 열리지 않게 한다 (findById는 is_deleted = FALSE만 돌려준다)
        UserDto user = userDao.findById(link.getUserId());
        if (user == null) {
            return null;
        }

        ShareViewDto view = new ShareViewDto();
        view.setScopeBasic(link.isScopeBasic());
        view.setScopeSkills(link.isScopeSkills());
        view.setScopeGrowth(link.isScopeGrowth());
        view.setScopeResume(link.isScopeResume());
        view.setScopeCoverLetter(link.isScopeCoverLetter());

        if (link.isScopeBasic()) {
            view.setName(user.getName());
            view.setMajor(user.getMajor());
            view.setGrade(user.getGrade());
            if (user.getDesiredJobId() != null) {
                JobDto job = jobDao.findById(user.getDesiredJobId());
                view.setDesiredJobName(job == null ? null : job.getJobName());
            }
            fillTimeline(view, user.getId());
        }
        if (link.isScopeSkills()) {
            fillSkills(view, user.getId());
        }
        if (link.isScopeResume()) {
            DocumentDto resume = findResume(user);
            view.setResumeFileName(resume == null ? null : resume.getOriginalName());
        }
        if (link.isScopeCoverLetter()) {
            DocumentDto coverLetter = findProfileDocument(user, user.getCoverLetterDocumentId());
            view.setCoverLetterFileName(coverLetter == null ? null : coverLetter.getOriginalName());
        }
        if (link.isScopeGrowth()) {
            List<SpecScoreHistoryDto> history = specScoreHistoryDao.findByUserId(user.getId());
            view.setGrowth(history.subList(Math.max(0, history.size() - GROWTH_POINTS), history.size()));
        }
        return view;
    }

    // FR-81 자격증·어학·수상과 프로젝트를 한 줄로 세워 시간순으로 정렬한다. 날짜가 없는 항목은 맨 뒤.
    private void fillTimeline(ShareViewDto view, Long userId) throws SQLException {
        List<Dated> dated = new ArrayList<>();
        RoadmapDto primaryRoadmap = roadmapDao.findPrimaryByUserId(userId);
        for (UserSpecDto spec : userSpecDao.findByUserId(userId)) {
            if ("CERT".equals(spec.getSpecType())) {
                view.getCertNames().add(spec.getTitle());
            }
            String detail = join(spec.getIssuer(), spec.getScore());
            // CERT 증빙 서류는 내용을 검증하지 않고 첨부 자체를 신뢰하는 대신(RoadmapService 팀
            // 결정), 제출 시점에 "면접관 공유 화면에 노출된다"고 안내한다(2026-10-01 사용자 요청)
            // — 그 약속을 지키려면 실제로 여기서 보여줘야 한다.
            Long documentId = "CERT".equals(spec.getSpecType()) && primaryRoadmap != null
                    ? findCertDocumentId(primaryRoadmap, spec.getTitle()) : null;
            dated.add(new Dated(spec.getAcquiredDate(), new TimelineItem(
                    spec.getAcquiredDate() == null ? "날짜 미입력" : spec.getAcquiredDate().toString(),
                    SPEC_TYPE_LABELS.getOrDefault(spec.getSpecType(), spec.getSpecType()),
                    spec.getTitle(), detail, documentId)));
        }
        List<UserProjectDto> projects = userProjectDao.findByUserId(userId);
        view.setProjectCount(projects.size());
        Map<Long, List<ProjectLinkDto>> extraLinks = projectLinkDao.findByProjectIds(
                projects.stream().map(UserProjectDto::getId).collect(Collectors.toList()));
        for (UserProjectDto project : projects) {
            String techStack = isBlank(project.getTechStack()) ? null : "사용 기술: " + project.getTechStack();
            dated.add(new Dated(project.getStartDate(), new TimelineItem(
                    period(project.getStartDate(), project.getEndDate()),
                    "프로젝트", project.getTitle(), join(project.getDescription(), techStack), null,
                    projectLinks(project, extraLinks.get(project.getId())))));
        }
        dated.sort(Comparator.comparing((Dated d) -> d.date, Comparator.nullsLast(Comparator.naturalOrder())));

        for (Dated d : dated) {
            view.getTimeline().add(d.item);
        }
    }

    // 면접관이 누르는 링크라서 저장할 때 검증했더라도 내보내기 직전에 한 번 더 웹 주소(http/https)만 남긴다.
    static List<ShareViewDto.Link> projectLinks(UserProjectDto project, List<ProjectLinkDto> extra) {
        List<ShareViewDto.Link> links = new ArrayList<>();
        if (UrlRules.isWebUrl(project.getRepoUrl())) {
            links.add(new ShareViewDto.Link("코드 저장소", project.getRepoUrl()));
        }
        if (UrlRules.isWebUrl(project.getDeployUrl())) {
            links.add(new ShareViewDto.Link("배포 주소", project.getDeployUrl()));
        }
        if (extra != null) {
            for (ProjectLinkDto link : extra) {
                if (UrlRules.isWebUrl(link.getUrl())) {
                    links.add(new ShareViewDto.Link(link.getDisplayName(), link.getUrl()));
                }
            }
        }
        return links;
    }

    // USER_SPECS에는 로드맵 단계로 직접 연결하는 FK가 없어서 "대표 로드맵의 완료된 CERT 단계 중
    // 같은 자격증 이름" 매칭으로 역추적한다(느슨한 결합이지만, CERT 단계를 완료 처리할 때
    // UserSpecDto.title에 자격증명을 그대로 쓰는 것과 같은 전제). 프로필에서 수동으로 추가한
    // 자격증(로드맵 단계 없음)은 null을 돌려준다.
    private Long findCertDocumentId(RoadmapDto roadmap, String certTitle) throws SQLException {
        CertificationDto cert = certificationDao.findByName(certTitle);
        if (cert == null) {
            return null;
        }
        for (RoadmapStepDto step : roadmapStepDao.findByRoadmapId(roadmap.getId())) {
            if ("CERT".equals(step.getStepType()) && step.isCompleted()
                    && cert.getId().equals(step.getCertificationId())) {
                DocumentDto document = documentDao.findByRoadmapStepId(step.getId());
                if (document != null) {
                    return document.getId();
                }
            }
        }
        return null;
    }

    private void fillSkills(ShareViewDto view, Long userId) throws SQLException {
        for (UserSkillDto skill : userSkillDao.findByUserId(userId)) {
            // 숙련도는 선택 입력이라 null일 수 있다 — Map.of로 만든 맵은 null 키 조회에서 예외를 던진다
            String proficiency = skill.getProficiency() == null
                    ? null : PROFICIENCY_LABELS.get(skill.getProficiency());
            view.getSkills().add(
                    proficiency == null ? skill.getRawInput() : skill.getRawInput() + " · " + proficiency);
            if (skill.getSkillId() != null) {
                view.getSkillIds().add(skill.getSkillId());
            }
            view.getSkillNames().add(skill.getRawInput().trim().toLowerCase(Locale.ROOT));
        }
    }

    // NFR-9 열람 기록. 기록에 실패했다고 면접관 화면까지 막지는 않는다.
    private void recordView(Long shareLinkId, String viewerIp) {
        ShareLinkViewLogDto log = new ShareLinkViewLogDto();
        log.setShareLinkId(shareLinkId);
        log.setViewedAt(LocalDateTime.now());
        log.setViewerIp(viewerIp);
        try {
            viewLogDao.insert(log);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "공유 링크 열람 기록 저장 실패", e);
        }
    }

    private static String period(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return "날짜 미입력";
        }
        if (start == null) {
            return "~ " + end;
        }
        return end == null ? start + " ~ 진행 중" : start + " ~ " + end;
    }

    // 비어 있지 않은 것만 " · "로 잇는다. 둘 다 비면 null.
    private static String join(String first, String second) {
        if (isBlank(first)) {
            return isBlank(second) ? null : second;
        }
        return isBlank(second) ? first : first + " · " + second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class Dated {
        private final LocalDate date;
        private final TimelineItem item;

        private Dated(LocalDate date, TimelineItem item) {
            this.date = date;
            this.item = item;
        }
    }
}
