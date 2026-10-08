package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.RoadmapDao;
import com.specodyssey.dao.RoadmapStepDao;
import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.SpecScoreHistoryDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserEducationDao;
import com.specodyssey.dao.ProjectDocumentItemDao;
import com.specodyssey.dao.ProjectLinkDao;
import com.specodyssey.dao.ProjectTechNoteDao;
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
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.SpecScoreHistoryDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.ProjectDocumentItemDto;
import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.dto.ProjectTechNoteDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.util.UrlRules;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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


    // 화면에 보여 줄 종류 순서까지 담는다 — Map.of는 순서를 보장하지 않아 "보유 스펙" 묶음이 매번 뒤바뀌었다
    private static final Map<String, String> SPEC_TYPE_LABELS = new LinkedHashMap<>();

    static {
        SPEC_TYPE_LABELS.put("CERT", "자격증");
        SPEC_TYPE_LABELS.put("LANGUAGE", "어학");
        SPEC_TYPE_LABELS.put("AWARD", "수상");
        SPEC_TYPE_LABELS.put("EXPERIENCE", "경험");
    }
    private static final String SUBMITTED = "SUBMITTED";
    private static final Map<String, String> PROFICIENCY_LABELS = Map.of(
            "BEGINNER", "입문", "INTERMEDIATE", "중급", "ADVANCED", "고급");

    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final ShareLinkViewLogDao viewLogDao = new ShareLinkViewLogDao();
    private final UserDao userDao = new UserDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final JobDao jobDao = new JobDao();
    private final ActivityHistoryService activityHistoryService = new ActivityHistoryService();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final ProjectLinkDao projectLinkDao = new ProjectLinkDao();
    private final ProjectTechNoteDao projectTechNoteDao = new ProjectTechNoteDao();
    private final ProjectDocumentItemDao projectDocumentItemDao = new ProjectDocumentItemDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final SpecScoreHistoryDao specScoreHistoryDao = new SpecScoreHistoryDao();
    private final CertificationDao certificationDao = new CertificationDao();
    private final SkillDao skillDao = new SkillDao();
    private final DocumentContentService documentContentService = new DocumentContentService();
    private final UserEducationDao educationDao = new UserEducationDao();
    private final RoadmapDao roadmapDao = new RoadmapDao();
    private final RoadmapStepDao roadmapStepDao = new RoadmapStepDao();
    private final NotificationService notificationService = new NotificationService();

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
            Long viewLogId = recordView(link.getId(), viewerIp);
            // 지원자에게 "면접관이 열람했다" 알림 — 열 때마다 하나씩 (열람 기록이 남았을 때만)
            if (viewLogId != null) {
                notificationService.notifyShareView(link.getUserId(), link.getLabel(), viewLogId);
            }
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

    /**
     * 공유 화면에서 연 서류(/share/documents/{토큰}/{문서id}) — 그 링크의 화면에 실제로 실린 서류만 내준다.
     * 문서 id는 순번이라 추측하기 쉬워서, "토큰 주인의 문서면 아무거나"로 열면 이력서 공개를 끈 링크로도
     * 이력서를 받을 수 있었다. 이제 아래 둘 중 하나여야 한다.
     *   - 기본 이력을 공개한 링크: 타임라인 자격증 항목의 증빙 서류
     *   - 프로젝트 서류를 공개한 링크: 프로젝트에 제출한 서류(PROJECT_DOCUMENT_ITEM)
     * @return 조건에 하나라도 안 맞으면 null (화면에서는 404 — 이유를 구분하지 않는다)
     */
    public DocumentDto loadSharedDocument(String token, Long documentId) throws SQLException {
        if (token == null || token.isBlank() || documentId == null) {
            return null;
        }
        ShareLinkDto link = shareLinkDao.findByToken(token);
        if (link == null || userDao.findById(link.getUserId()) == null) {
            return null;
        }
        if (!sharedDocumentIds(link).contains(documentId)) {
            return null;
        }
        DocumentDto document = documentDao.findById(documentId);
        return (document == null || !document.getUserId().equals(link.getUserId())) ? null : document;
    }

    // 이 링크의 화면에 실리는 서류 id — buildView가 화면에 내보내는 것과 같은 규칙
    private Set<Long> sharedDocumentIds(ShareLinkDto link) throws SQLException {
        Set<Long> ids = new HashSet<>();
        Long userId = link.getUserId();
        if (link.isScopeBasic()) {
            RoadmapDto primaryRoadmap = roadmapDao.findPrimaryByUserId(userId);
            if (primaryRoadmap != null) {
                for (UserSpecDto spec : userSpecDao.findByUserId(userId)) {
                    if ("CERT".equals(spec.getSpecType())) {
                        Long id = findCertDocumentId(primaryRoadmap, spec.getTitle());
                        if (id != null) {
                            ids.add(id);
                        }
                    }
                }
            }
        }
        if (link.isScopeBasic() && link.isScopeProjectDocs()) {
            for (UserProjectDto project : userProjectDao.findByUserId(userId)) {
                for (ProjectDocumentItemDto item : projectDocumentItemDao.findByProjectId(project.getId())) {
                    if (SUBMITTED.equals(item.getStatus()) && item.getDocumentId() != null) {
                        ids.add(item.getDocumentId());
                    }
                }
            }
        }
        return ids;
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
        view.setScopeAge(link.isScopeAge());
        view.setScopeActivity(link.isScopeActivity());
        view.setScopeEducation(link.isScopeEducation());
        // 프로젝트 서류는 타임라인(기본 이력) 안에 실리므로 기본 이력도 공개한 링크에서만 의미가 있다
        view.setScopeProjectDocs(link.isScopeBasic() && link.isScopeProjectDocs());

        // 기술 → 프로젝트 근거는 프로젝트 목록(기본 이력)을 공개한 링크에서만 쓴다
        List<ProjectEvidence> projectEvidence = List.of();
        if (link.isScopeBasic()) {
            view.setName(user.getName());
            view.setMajor(user.getMajor());
            view.setGrade(user.getGrade());
            if (user.getDesiredJobId() != null) {
                JobDto job = jobDao.findById(user.getDesiredJobId());
                view.setDesiredJobName(job == null ? null : job.getJobName());
            }
            projectEvidence = fillTimeline(view, user.getId(), view.isScopeProjectDocs());
        }
        // 학력은 블라인드 채용을 고려해 기본 이력과 따로 고른 링크에서만
        if (link.isScopeEducation()) {
            view.setEducation(educationDao.findByUserId(user.getId()));
        }
        // 나이는 기본 이력과 따로 고른 링크에서만 (면접관 비교 화면의 나이순 정렬용, NFR-4)
        if (link.isScopeAge()) {
            view.setAge(user.getAge());
        }
        if (link.isScopeSkills()) {
            fillSkills(view, user.getId(), projectEvidence);
        }
        if (link.isScopeResume()) {
            DocumentDto resume = findResume(user);
            view.setResumeFileName(resume == null ? null : resume.getOriginalName());
            view.setResumeFileReadable(documentContentService.exists(resume)); // DB에 있거나(새 서류) 이 PC 디스크에 있을 때
        }
        if (link.isScopeCoverLetter()) {
            DocumentDto coverLetter = findProfileDocument(user, user.getCoverLetterDocumentId());
            view.setCoverLetterFileName(coverLetter == null ? null : coverLetter.getOriginalName());
            view.setCoverLetterFileReadable(
                    documentContentService.exists(coverLetter));
        }
        // 활동 내역 — 며칠에 무엇을 했는지까지 드러나 지원자가 켠 링크에서만 (NFR-4)
        if (link.isScopeActivity()) {
            view.setActivity(activityHistoryService.load(user.getId()));
        }
        if (link.isScopeGrowth()) {
            List<SpecScoreHistoryDto> history = specScoreHistoryDao.findByUserId(user.getId());
            // 최근 몇 개만 자르면 변화가 안 보여 전체 기록을 주·월·년으로 묶는다 (2026-10-08)
            view.setGrowth(history);
            view.setGrowthChart(GrowthChart.build(history));
        }
        return view;
    }

    // FR-81 자격증·어학·수상과 프로젝트를 한 줄로 세워 시간순으로 정렬한다. 날짜가 없는 항목은 맨 뒤.
    // 돌려주는 값은 기술 카드의 "사용한 프로젝트" 근거용 — 프로젝트를 한 번만 읽으려고 여기서 같이 모은다.
    private List<ProjectEvidence> fillTimeline(ShareViewDto view, Long userId, boolean shareProjectDocs)
            throws SQLException {
        List<ProjectEvidence> evidence = new ArrayList<>();
        List<Dated> dated = new ArrayList<>();
        Map<String, List<TimelineItem>> specsByType = new LinkedHashMap<>();
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
            // 경험(인턴·대외활동)은 기간으로, 나머지는 취득일로 보여준다
            String dateText = "EXPERIENCE".equals(spec.getSpecType())
                    ? period(spec.getAcquiredDate(), spec.getEndDate())
                    : spec.getAcquiredDate() == null ? "날짜 미입력" : spec.getAcquiredDate().toString();
            String typeLabel = SPEC_TYPE_LABELS.getOrDefault(spec.getSpecType(), spec.getSpecType());
            TimelineItem specItem = new TimelineItem(dateText, typeLabel, spec.getTitle(), detail, documentId);
            dated.add(new Dated(spec.getAcquiredDate(), specItem));
            // 같은 항목을 "보유 스펙" 카드에도 넣는다 — 타임라인은 시간순 서사, 이쪽은 종류별로 훑는 용도
            specsByType.computeIfAbsent(typeLabel, k -> new ArrayList<>()).add(specItem);
        }
        List<UserProjectDto> projects = userProjectDao.findByUserId(userId);
        view.setProjectCount(projects.size());
        Map<Long, List<ProjectLinkDto>> extraLinks = projectLinkDao.findByProjectIds(
                projects.stream().map(UserProjectDto::getId).collect(Collectors.toList()));
        for (UserProjectDto project : projects) {
            String techStack = isBlank(project.getTechStack()) ? null : "사용 기술: " + project.getTechStack();
            TimelineItem item = new TimelineItem(
                    period(project.getStartDate(), project.getEndDate()),
                    "프로젝트", project.getTitle(), join(project.getDescription(), techStack), null,
                    projectLinks(project, extraLinks.get(project.getId())));
            List<ProjectTechNoteDto> notes = projectTechNoteDao.findByProjectId(project.getId());
            fillProjectDetail(item, project, notes, shareProjectDocs);
            dated.add(new Dated(project.getStartDate(), item));
            evidence.add(ProjectEvidence.of(project, notes));
        }
        dated.sort(Comparator.comparing((Dated d) -> d.date, Comparator.nullsLast(Comparator.naturalOrder())));

        for (Dated d : dated) {
            view.getTimeline().add(d.item);
        }
        view.setSpecGroups(orderedByType(specsByType));
        return evidence;
    }

    /** SPEC_TYPE_LABELS 순서(자격증 → 어학 → 수상 → 경험)대로 다시 담는다 — 넣은 순서는 사용자마다 다르다. */
    static Map<String, List<TimelineItem>> orderedByType(Map<String, List<TimelineItem>> byType) {
        Map<String, List<TimelineItem>> ordered = new LinkedHashMap<>();
        for (String label : SPEC_TYPE_LABELS.values()) {
            List<TimelineItem> items = byType.get(label);
            if (items != null && !items.isEmpty()) {
                ordered.put(label, items);
            }
        }
        // 라벨을 모르는 spec_type(스키마가 자유 값이다)도 빠뜨리지 않는다
        byType.forEach(ordered::putIfAbsent);
        return ordered;
    }

    // 면접관이 프로젝트에서 가장 오래 보는 부분 — 팀 규모·내 역할, 회고, 기술별 활용 설명, 어떤 서류까지 갖췄는지.
    // 서류 파일은 지원자가 링크에 "프로젝트 서류"(scope_project_docs)를 고른 경우에만 열린다.
    private void fillProjectDetail(TimelineItem item, UserProjectDto project, List<ProjectTechNoteDto> techNotes,
                                   boolean shareProjectDocs) throws SQLException {
        item.setTeamSize(project.getTeamSize());
        item.setMyRole(isBlank(project.getMyRole()) ? null : project.getMyRole().trim());
        if (!isBlank(project.getRetrospective())) {
            item.setRetrospective(project.getRetrospective().trim());
        }
        List<ShareViewDto.TechNote> notes = new ArrayList<>();
        for (ProjectTechNoteDto note : techNotes) {
            if (!isBlank(note.getDescription())) {
                notes.add(new ShareViewDto.TechNote(note.getSkillName(), note.getDescription().trim()));
            }
        }
        item.setTechNotes(notes);
        List<ShareViewDto.SubmittedDoc> docs = submittedDocs(projectDocumentItemDao.findByProjectId(project.getId()));
        // 파일은 "프로젝트 서류"를 공개한 링크에서만 연다 — 아니면 종류 이름만 보인다
        if (shareProjectDocs) {
            for (ShareViewDto.SubmittedDoc doc : docs) {
                if (doc.getSourceDocumentId() == null) {
                    continue;
                }
                DocumentDto file = documentDao.findById(doc.getSourceDocumentId());
                if (file != null && file.getUserId().equals(project.getUserId())) {
                    if (documentContentService.exists(file)) {
                        doc.share(file.getId(), file.getOriginalName(), previewType(file.getOriginalName()));
                    } else {
                        doc.markMissing(file.getOriginalName());
                    }
                }
            }
        }
        item.setSubmittedDocs(docs);
    }

    // 실제로 제출한 것만, 화면 표시 순서(ProjectSubmissionService.DOC_TYPE_LABELS)대로
    static List<ShareViewDto.SubmittedDoc> submittedDocs(List<ProjectDocumentItemDto> items) {
        List<ShareViewDto.SubmittedDoc> docs = new ArrayList<>();
        for (Map.Entry<String, String> e : ProjectSubmissionService.DOC_TYPE_LABELS.entrySet()) {
            for (ProjectDocumentItemDto i : items) {
                if (SUBMITTED.equals(i.getStatus()) && e.getKey().equals(i.getDocType())) {
                    docs.add(new ShareViewDto.SubmittedDoc(e.getKey(), e.getValue(), i.getDocumentId()));
                    break;
                }
            }
        }
        return docs;
    }

    // 화면 안에서 바로 보여줄 수 있는 형식 — PDF는 iframe, 이미지는 img. 그 외는 내려받기만
    static String previewType(String fileName) {
        if (FileStorageUtil.isPdf(fileName)) {
            return "pdf";
        }
        return FileStorageUtil.isInlineSafe(fileName) ? "image" : null;
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

    private void fillSkills(ShareViewDto view, Long userId, List<ProjectEvidence> projects) throws SQLException {
        for (UserSkillDto skill : userSkillDao.findByUserId(userId)) {
            // 숙련도는 선택 입력이라 null일 수 있다 — Map.of로 만든 맵은 null 키 조회에서 예외를 던진다
            String proficiency = skill.getProficiency() == null
                    ? null : PROFICIENCY_LABELS.get(skill.getProficiency());
            String label = proficiency == null ? skill.getRawInput() : skill.getRawInput() + " · " + proficiency;
            view.getSkills().add(label);
            String nameKey = normalize(skill.getRawInput());
            if (skill.getSkillId() != null) {
                view.getSkillIds().add(skill.getSkillId());
            }
            view.getSkillNames().add(nameKey);

            // 표준 이름("Spring Boot")도 같이 맞춰 본다 — 입력 원문("스프링부트")과 프로젝트 tech_stack 표기가 다를 수 있다
            String canonicalKey = null;
            if (skill.getSkillId() != null && !projects.isEmpty()) {
                SkillDto master = skillDao.findById(skill.getSkillId());
                canonicalKey = master == null ? null : normalize(master.getSkillName());
            }
            view.getSkillItems().add(new ShareViewDto.SkillItem(label, skill.getSkillId(), nameKey,
                    skill.getProficiency(), usedIn(projects, skill.getSkillId(), nameKey, canonicalKey)));
        }
    }

    // FR-81 보강 — 이 기술을 쓴 프로젝트 제목. 기술 활용 설명(PROJECT_TECH_NOTE)에 있거나 tech_stack에 같은 이름이 있으면 쓴 것으로 본다.
    static List<String> usedIn(List<ProjectEvidence> projects, Long skillId, String nameKey, String canonicalKey) {
        List<String> titles = new ArrayList<>();
        for (ProjectEvidence p : projects) {
            boolean used = (skillId != null && p.noteSkillIds.contains(skillId))
                    || p.stackKeys.contains(nameKey)
                    || (canonicalKey != null && p.stackKeys.contains(canonicalKey));
            if (used) {
                titles.add(p.title);
            }
        }
        return titles;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** 프로젝트 하나에서 기술 근거를 찾는 데 필요한 것만. */
    static final class ProjectEvidence {
        private final String title;
        private final Set<Long> noteSkillIds;
        private final Set<String> stackKeys;

        ProjectEvidence(String title, Set<Long> noteSkillIds, Set<String> stackKeys) {
            this.title = title;
            this.noteSkillIds = noteSkillIds;
            this.stackKeys = stackKeys;
        }

        static ProjectEvidence of(UserProjectDto project, List<ProjectTechNoteDto> notes) {
            Set<Long> ids = new HashSet<>();
            for (ProjectTechNoteDto note : notes) {
                ids.add(note.getSkillId());
            }
            return new ProjectEvidence(project.getTitle(), ids, stackKeys(project.getTechStack()));
        }

        // tech_stack은 자유 입력이라 쉼표·슬래시·가운뎃점 등으로 나뉘어 있다
        static Set<String> stackKeys(String techStack) {
            Set<String> keys = new HashSet<>();
            if (techStack != null) {
                for (String part : techStack.split("[,/·|+;\\n]")) {
                    String key = normalize(part);
                    if (!key.isEmpty()) {
                        keys.add(key);
                    }
                }
            }
            return keys;
        }
    }

    // NFR-9 열람 기록. 기록에 실패했다고 면접관 화면까지 막지는 않는다.
    private Long recordView(Long shareLinkId, String viewerIp) {
        ShareLinkViewLogDto log = new ShareLinkViewLogDto();
        log.setShareLinkId(shareLinkId);
        log.setViewedAt(LocalDateTime.now());
        log.setViewerIp(viewerIp);
        try {
            return viewLogDao.insert(log);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "공유 링크 열람 기록 저장 실패", e);
            return null;
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
