package com.specodyssey.service;

import com.specodyssey.dao.EvaluationCriteriaDao;
import com.specodyssey.dao.EvaluationSessionDao;
import com.specodyssey.dao.EvaluationSessionItemDao;
import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.EvaluationCriteriaDto;
import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.dto.EvaluationSessionItemDto;
import com.specodyssey.dto.InterviewerCompareDto;
import com.specodyssey.dto.InterviewerCompareDto.Applicant;
import com.specodyssey.dto.InterviewerCompareDto.Criterion;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.SpecScoreHistoryDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 면접관 계정 기능 — 공유받은 이력 담기, 회사 요구 역량, 지원자 비교, 내 프로필.
 * 관련 요구사항: FR-82 · 83 · 84
 * 면접관은 공유 링크를 받은 지원자만 본다. 지원자를 검색하거나 직접 조회하는 경로는 없고(FR-85),
 * 담아 둔 링크도 지원자가 공유를 멈추면 더 이상 읽히지 않는다(FR-86).
 */
public class InterviewerService {

    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 5;
    private static final int COMPANY_NAME_MAX_LENGTH = 100; // EVALUATION_SESSION.company_name VARCHAR(100)

    private static final Map<String, String> PROFICIENCY_LABELS = Map.of(
            "BEGINNER", "입문", "INTERMEDIATE", "중급", "ADVANCED", "고급");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EvaluationSessionDao sessionDao = new EvaluationSessionDao();
    private final EvaluationSessionItemDao itemDao = new EvaluationSessionItemDao();
    private final EvaluationCriteriaDao criteriaDao = new EvaluationCriteriaDao();
    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final SkillDao skillDao = new SkillDao();
    private final UserDao userDao = new UserDao();
    private final ShareViewService shareViewService = new ShareViewService();

    /** 면접관의 비교 목록(계정당 하나). 없으면 만든다. */
    public EvaluationSessionDto getOrCreateSession(Long userId) throws SQLException {
        return getOrCreateSession(userId, null);
    }

    EvaluationSessionDto getOrCreateSession(Long userId, String companyName) throws SQLException {
        EvaluationSessionDto session = sessionDao.findByUserId(userId);
        if (session != null) {
            return session;
        }
        session = new EvaluationSessionDto();
        session.setUserId(userId);
        // 계정 세션은 user_id로 찾지만 session_token은 NOT NULL·UNIQUE라 값이 필요하다.
        // 기존 DAO의 수정·삭제가 토큰으로 소유자를 확인하므로 추측할 수 없는 값을 넣어 둔다.
        session.setSessionToken(randomToken());
        session.setCompanyName(companyName);
        try {
            sessionDao.insert(session);
        } catch (SQLIntegrityConstraintViolationException e) {
            // 동시에 들어온 다른 요청이 먼저 만들었다 — 그걸 쓴다
        }
        return sessionDao.findByUserId(userId);
    }

    // ---------------------------------------------------------------- 공유받은 이력

    /**
     * 받은 공유 링크를 비교 목록에 담는다.
     * @param linkOrToken 공유 링크 주소 전체 또는 토큰만
     * @throws IllegalArgumentException 열 수 없는 링크인 경우 — 메시지를 그대로 화면에 보여준다
     */
    public void addLink(Long userId, String linkOrToken) throws SQLException {
        ShareLinkDto link = shareLinkDao.findByToken(extractToken(linkOrToken));
        if (link == null) {
            throw new IllegalArgumentException("열 수 없는 링크입니다. 주소를 다시 확인하거나 지원자에게 새 링크를 요청해 주세요.");
        }
        EvaluationSessionDto session = getOrCreateSession(userId);
        LocalDateTime now = LocalDateTime.now();

        EvaluationSessionItemDto existing = itemDao.findBySessionIdAndShareLinkId(session.getId(), link.getId());
        if (existing == null) {
            EvaluationSessionItemDto item = new EvaluationSessionItemDto();
            item.setSessionId(session.getId());
            item.setShareLinkId(link.getId());
            item.setAddedAt(now);
            itemDao.insert(item);
        } else if (existing.isDeleted()) {
            try (Connection conn = DBUtil.getConnection()) {
                itemDao.restore(conn, existing.getId(), now);
            }
        } else {
            throw new IllegalArgumentException("이미 담은 지원자입니다.");
        }
    }

    // 남의 목록에 있는 항목 id면 아무 일도 일어나지 않는다
    public void removeItem(Long userId, Long itemId) throws SQLException {
        EvaluationSessionDto session = getOrCreateSession(userId);
        try (Connection conn = DBUtil.getConnection()) {
            itemDao.delete(conn, itemId, session.getSessionToken());
        }
    }

    static final int MEMO_MAX_LENGTH = 1000;

    /**
     * 면접관 본인의 검토 기록 — 상태(검토 중/서류 합격/보류/불합격)·평점(1~5)·메모. 지원자에게는 보이지 않는다.
     * 다른 면접관 목록의 항목이면 아무 것도 바뀌지 않는다(세션 토큰으로 소유 확인).
     * @throws IllegalArgumentException 상태·평점·메모 길이가 잘못된 경우
     */
    public void updateEvaluation(Long userId, Long itemId, String reviewStatus, Integer rating, String memo)
            throws SQLException {
        if (!InterviewerCompareDto.REVIEW_STATUS_LABELS.containsKey(reviewStatus)) {
            throw new IllegalArgumentException("검토 상태를 다시 선택해주세요.");
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("평점은 1~5점으로 선택해주세요.");
        }
        String trimmedMemo = memo == null || memo.isBlank() ? null : memo.strip();
        if (trimmedMemo != null && trimmedMemo.length() > MEMO_MAX_LENGTH) {
            throw new IllegalArgumentException("메모는 " + MEMO_MAX_LENGTH + "자 이내로 입력해주세요.");
        }
        EvaluationSessionDto session = getOrCreateSession(userId);
        try (Connection conn = DBUtil.getConnection()) {
            itemDao.updateEvaluation(conn, itemId, session.getSessionToken(), reviewStatus, rating, trimmedMemo);
        }
    }

    /** 검토 상태별 지원자 수 — 상태 필터 탭에 숫자로 보여준다. 키 "ALL"은 전체. */
    public static java.util.Map<String, Integer> countByReviewStatus(List<Applicant> applicants) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        counts.put("ALL", applicants.size());
        for (String status : InterviewerCompareDto.REVIEW_STATUS_LABELS.keySet()) {
            counts.put(status, 0);
        }
        for (Applicant a : applicants) {
            counts.merge(a.getReviewStatus(), 1, Integer::sum);
        }
        return counts;
    }

    /** 고른 검토 상태의 지원자만 남긴다(제자리). status가 null이면 그대로. */
    public static void filterByReviewStatus(List<Applicant> applicants, String status) {
        if (status != null) {
            applicants.removeIf(a -> !status.equals(a.getReviewStatus()));
        }
    }

    // ---------------------------------------------------------------- 회사 요구 역량 (FR-83)

    /**
     * @throws IllegalArgumentException 등록되지 않은 기술명이거나 가중치가 범위를 벗어난 경우
     */
    public void saveCriterion(Long userId, String skillName, int weight) throws SQLException {
        if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException("가중치는 " + MIN_WEIGHT + "~" + MAX_WEIGHT + " 사이로 입력해주세요.");
        }
        SkillDto skill = (skillName == null || skillName.isBlank()) ? null : skillDao.findByName(skillName.trim());
        if (skill == null) {
            throw new IllegalArgumentException("등록되지 않은 기술입니다. 목록에서 골라 주세요.");
        }
        criteriaDao.save(getOrCreateSession(userId).getId(), skill.getId(), weight);
    }

    public void removeCriterion(Long userId, Long criterionId) throws SQLException {
        EvaluationSessionDto session = getOrCreateSession(userId);
        try (Connection conn = DBUtil.getConnection()) {
            criteriaDao.delete(conn, criterionId, session.getSessionToken());
        }
    }

    /** 요구 역량 입력칸의 자동완성 목록. */
    public List<SkillDto> listSkills() throws SQLException {
        return skillDao.findAll();
    }

    // ---------------------------------------------------------------- 목록 · 비교 (FR-82)

    /** 담아 둔 지원자와 요구 역량, 지원자별 적합도. "공유받은 이력" 목록과 "지원자 비교" 화면이 같이 쓴다. */
    public InterviewerCompareDto loadCompare(Long userId) throws SQLException {
        return loadCompare(userId, CompareSort.ADDED);
    }

    /**
     * 나란히 보기용 — 면접관이 고른 기준으로 지원자 순서를 바꾼다.
     * "지원자 N" 이름은 정렬 전에 담은 순서로 붙여서, 정렬을 바꿔도 같은 지원자는 같은 번호로 불린다.
     */
    public InterviewerCompareDto loadCompare(Long userId, CompareSort sort) throws SQLException {
        EvaluationSessionDto session = getOrCreateSession(userId);
        InterviewerCompareDto compare = new InterviewerCompareDto();

        List<EvaluationCriteriaDto> criteriaRows = criteriaDao.findBySessionId(session.getId());
        int totalWeight = 0;
        for (EvaluationCriteriaDto row : criteriaRows) {
            SkillDto skill = skillDao.findById(row.getSkillId());
            compare.getCriteria().add(new Criterion(
                    row.getId(), skill == null ? "(삭제된 기술)" : skill.getSkillName(), row.getWeight()));
            totalWeight += row.getWeight();
        }
        compare.setTotalWeight(totalWeight);

        int order = 1;
        for (EvaluationSessionItemDto item : itemDao.findBySessionId(session.getId())) {
            Applicant applicant = new Applicant();
            applicant.setItemId(item.getId());
            applicant.setLabel("지원자 " + order++);
            applicant.setAddedDate(item.getAddedAt().toLocalDate().toString());
            applicant.setAddedAt(item.getAddedAt());
            if (item.getReviewStatus() != null) {
                applicant.setReviewStatus(item.getReviewStatus());
            }
            applicant.setRating(item.getRating());
            applicant.setMemo(item.getMemo());

            ShareLinkDto link = shareLinkDao.findActiveById(item.getShareLinkId());
            ShareViewDto view = link == null ? null : shareViewService.loadViewByLinkId(link.getId());
            if (view != null) {
                applicant.setToken(link.getToken());
                applicant.setView(view);
                // 이름은 기본 이력을 공개한 지원자만 보인다. 없으면 담은 순서("지원자 1")로 부른다.
                if (view.getName() != null && !view.getName().isBlank()) {
                    applicant.setLabel(view.getName());
                }
                applicant.setCertText(certText(view));
                applicant.setCertFullText(view.getCertNames().isEmpty() ? applicant.getCertText()
                        : String.join(", ", view.getCertNames()));
                applicant.setGrowthText(growthText(view));
                if (view.isScopeSkills()) {
                    double matchedWeight = 0;
                    for (int i = 0; i < criteriaRows.size(); i++) {
                        EvaluationCriteriaDto row = criteriaRows.get(i);
                        // 마스터에 매칭된 기술은 id로, 아직 매칭 전인 기술은 입력한 이름으로 맞춘다
                        String criterionName = compare.getCriteria().get(i).getSkillName();
                        ShareViewDto.SkillItem owned = findOwned(view, row.getSkillId(),
                                criterionName.trim().toLowerCase(Locale.ROOT));
                        applicant.getMatches().add(owned != null);
                        applicant.getMatchDetails().add(owned == null ? null : matchDetail(owned));
                        if (owned != null) {
                            matchedWeight += row.getWeight()
                                    * matchCredit(owned.getProficiency(), !owned.getProjectTitles().isEmpty());
                        }
                    }
                    applicant.setFitScore(fitScore(matchedWeight, totalWeight));
                }
            }
            compare.getApplicants().add(applicant);
        }
        sort.sort(compare.getApplicants());
        return compare;
    }

    // 적합도 = 맞춘 역량의 가중치 합 ÷ 전체 가중치 합 × 100. 요구 역량이 없으면 계산하지 않는다.
    static Integer fitScore(int matchedWeight, int totalWeight) {
        return fitScore((double) matchedWeight, totalWeight);
    }

    // 숙련도를 반영한 버전 — matchedWeight는 가중치 × 인정 비율(matchCredit)의 합
    static Integer fitScore(double matchedWeight, int totalWeight) {
        return totalWeight == 0 ? null : (int) Math.round(matchedWeight * 100 / totalWeight);
    }

    /**
     * 갖춘 기술 하나를 얼마나 인정할지(0~1). 이름만 있는 기술과 프로젝트로 써 본 고급 기술을 같게 보지 않는다.
     * 숙련도: 고급 1.0 · 중급 0.8 · 입문 0.5 · 미입력 0.6 (미입력을 입문보다 살짝 높게 — 정직하게 "입문"을 고른 사람이 손해 보지 않을 만큼만)
     * 그 기술을 쓴 프로젝트가 있으면 +0.2 (최대 1.0).
     */
    static double matchCredit(String proficiency, boolean usedInProject) {
        double base;
        if ("ADVANCED".equals(proficiency)) {
            base = 1.0;
        } else if ("INTERMEDIATE".equals(proficiency)) {
            base = 0.8;
        } else if ("BEGINNER".equals(proficiency)) {
            base = 0.5;
        } else {
            base = 0.6;
        }
        return Math.min(1.0, base + (usedInProject ? 0.2 : 0));
    }

    private static ShareViewDto.SkillItem findOwned(ShareViewDto view, Long skillId, String nameKey) {
        for (ShareViewDto.SkillItem item : view.getSkillItems()) {
            if ((skillId != null && skillId.equals(item.getSkillId())) || nameKey.equals(item.getNameKey())) {
                return item;
            }
        }
        return null;
    }

    // 비교 표 칸에 "갖춤" 아래 작게 — "고급 · 프로젝트 2개" / "숙련도 미입력"
    static String matchDetail(ShareViewDto.SkillItem owned) {
        String level = PROFICIENCY_LABELS.getOrDefault(owned.getProficiency() == null ? "" : owned.getProficiency(),
                "숙련도 미입력");
        int used = owned.getProjectTitles().size();
        return used == 0 ? level : level + " · 프로젝트 " + used + "개";
    }

    private static String certText(ShareViewDto view) {
        if (!view.isScopeBasic()) {
            return null;
        }
        List<String> names = view.getCertNames();
        if (names.isEmpty()) {
            return "없음";
        }
        return names.size() == 1 ? names.get(0) : names.get(0) + " 외 " + (names.size() - 1) + "개";
    }

    // FR-84 공개된 완성도 기록의 처음과 끝을 비교한다
    private static String growthText(ShareViewDto view) {
        if (!view.isScopeGrowth()) {
            return null;
        }
        List<SpecScoreHistoryDto> growth = view.getGrowth();
        if (growth.size() < 2) {
            return "기록이 아직 부족합니다";
        }
        SpecScoreHistoryDto first = growth.get(0);
        SpecScoreHistoryDto last = growth.get(growth.size() - 1);
        return "완성도 " + first.getCompletenessScore().stripTrailingZeros().toPlainString()
                + " → " + last.getCompletenessScore().stripTrailingZeros().toPlainString()
                + " (" + first.getSnapshotDate() + " ~ " + last.getSnapshotDate() + ")";
    }

    // ---------------------------------------------------------------- 내 프로필

    /**
     * @throws IllegalArgumentException 이름이 비었거나 이름·회사명이 너무 긴 경우
     */
    public void updateProfile(Long userId, String name, String email, String companyName) throws SQLException {
        PersonalInfo personalInfo = PersonalInfo.nameOnly(name);
        String company = normalizeCompanyName(companyName);

        UserDto user = userDao.findById(userId);
        user.setName(personalInfo.getName());
        user.setEmail(email == null || email.isBlank() ? null : email.trim());
        userDao.updateProfile(user);

        EvaluationSessionDto session = getOrCreateSession(userId);
        session.setCompanyName(company);
        try (Connection conn = DBUtil.getConnection()) {
            sessionDao.update(conn, session);
        }
    }

    public UserDto findUser(Long userId) throws SQLException {
        UserDto user = userDao.findById(userId);
        user.setPasswordHash(null);
        return user;
    }

    static String normalizeCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return null;
        }
        String trimmed = companyName.trim();
        if (trimmed.length() > COMPANY_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("회사명은 " + COMPANY_NAME_MAX_LENGTH + "자 이내로 입력해주세요.");
        }
        return trimmed;
    }

    // "http://…/share/토큰" 처럼 주소 전체를 붙여 넣어도 마지막 경로 조각(토큰)만 쓴다
    static String extractToken(String linkOrToken) {
        if (linkOrToken == null) {
            return "";
        }
        String value = linkOrToken.trim();
        int cut = value.indexOf('?');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        cut = value.indexOf('#');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.substring(value.lastIndexOf('/') + 1);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
