package com.specodyssey.service;

import com.specodyssey.dao.EvaluationCriteriaDao;
import com.specodyssey.dao.EvaluationSessionDao;
import com.specodyssey.dao.EvaluationSessionItemDao;
import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.EvaluationCriteriaDto;
import com.specodyssey.dto.EvaluationSessionDto;
import com.specodyssey.dto.EvaluationSessionItemDto;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.util.DBUtil;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 면접관 비교(장바구니) — 로그인 없이 받은 공유 링크 여러 개를 담아 나란히 비교한다.
 * 관련 요구사항: FR-82(비교 세션) · FR-83(요구 역량·가중치)
 *
 * 면접관은 계정이 없으므로(FR-14) 소유 증명은 session_token(브라우저 쿠키) 하나뿐이다 — 이 토큰을
 * 아는 사람만 그 세션에 담긴 지원자·요구 역량을 보고 고칠 수 있다. SHARE_LINK 토큰과 같은 이유로
 * SecureRandom으로 추측 불가능하게 만든다.
 */
public class EvaluationCompareService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    // 평가 세션 만료 기간은 팀 결정 없음 — 공유 링크 발급 기본값(30일)과 같은 길이로 맞춘다.
    private static final int DEFAULT_EXPIRES_DAYS = 30;

    private final EvaluationSessionDao evaluationSessionDao = new EvaluationSessionDao();
    private final EvaluationSessionItemDao evaluationSessionItemDao = new EvaluationSessionItemDao();
    private final EvaluationCriteriaDao evaluationCriteriaDao = new EvaluationCriteriaDao();
    private final ShareLinkDao shareLinkDao = new ShareLinkDao();
    private final UserDao userDao = new UserDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();
    private final SkillDao skillDao = new SkillDao();
    private final SpecScoreService specScoreService = new SpecScoreService();

    public record CriterionView(Long id, String skillName, int weight) {
    }

    public record CandidateView(Long itemId, String major, String grade, long certCount, long projectCount,
            List<Boolean> hasSkillByCriterion, Integer fitScorePercent, SpecScoreService.GrowthSummary growth) {
    }

    public record CompareView(EvaluationSessionDto session, List<CriterionView> criteria,
            List<CandidateView> candidates) {
    }

    /** 쿠키에 저장된 토큰이 있으면 재사용하고, 없거나 만료됐으면 새로 만든다. */
    public EvaluationSessionDto getOrCreateSession(String existingToken) throws SQLException {
        if (existingToken != null) {
            EvaluationSessionDto found = evaluationSessionDao.findByToken(existingToken);
            if (found != null) {
                return found;
            }
        }
        EvaluationSessionDto session = new EvaluationSessionDto();
        session.setSessionToken(generateToken());
        session.setExpiresAt(LocalDateTime.now().plusDays(DEFAULT_EXPIRES_DAYS));
        Long id = evaluationSessionDao.insert(session);
        session.setId(id);
        return session;
    }

    public void renameSession(Long sessionId, String sessionToken, String companyName) throws SQLException {
        EvaluationSessionDto session = evaluationSessionDao.findByToken(sessionToken);
        if (session == null || !session.getId().equals(sessionId)) {
            return;
        }
        session.setCompanyName(companyName);
        try (Connection conn = DBUtil.getConnection()) {
            evaluationSessionDao.update(conn, session);
        }
    }

    // 지원자가 공유한 토큰을 그대로 붙여넣거나, 전체 URL을 붙여넣어도 받는다.
    public void addCandidate(Long sessionId, String pastedTokenOrUrl) throws SQLException {
        String token = extractToken(pastedTokenOrUrl);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("공유 링크를 입력해주세요.");
        }
        ShareLinkDto link = shareLinkDao.findByToken(token);
        if (link == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 공유 링크입니다.");
        }
        boolean alreadyAdded = evaluationSessionItemDao.findBySessionId(sessionId).stream()
                .anyMatch(i -> i.getShareLinkId().equals(link.getId()));
        if (alreadyAdded) {
            return; // 이미 담긴 지원자 — 조용히 무시 (복합 UNIQUE도 같은 의도)
        }
        EvaluationSessionItemDto item = new EvaluationSessionItemDto();
        item.setSessionId(sessionId);
        item.setShareLinkId(link.getId());
        item.setAddedAt(LocalDateTime.now());
        evaluationSessionItemDao.insert(item);
    }

    public void removeCandidate(Long itemId, String sessionToken) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            evaluationSessionItemDao.delete(conn, itemId, sessionToken);
        }
    }

    // 같은 기술을 또 추가하면 새로 만들지 않고 가중치만 덮어쓴다(UNIQUE(session_id, skill_id)와 같은 의도).
    public void addOrUpdateCriterion(Long sessionId, String sessionToken, String skillName, int weight)
            throws SQLException {
        if (weight <= 0) {
            throw new IllegalArgumentException("가중치는 1 이상이어야 합니다.");
        }
        SkillDto skill = skillDao.findByName(skillName == null ? null : skillName.trim());
        if (skill == null) {
            throw new IllegalArgumentException("등록된 기술명이 아닙니다: " + skillName);
        }
        EvaluationCriteriaDto existing = evaluationCriteriaDao.findBySessionId(sessionId).stream()
                .filter(c -> c.getSkillId().equals(skill.getId()))
                .findFirst().orElse(null);
        try (Connection conn = DBUtil.getConnection()) {
            if (existing != null) {
                evaluationCriteriaDao.update(conn, existing.getId(), weight, sessionToken);
            } else {
                EvaluationCriteriaDto criteria = new EvaluationCriteriaDto();
                criteria.setSessionId(sessionId);
                criteria.setSkillId(skill.getId());
                criteria.setWeight(weight);
                evaluationCriteriaDao.insert(conn, criteria);
            }
        }
    }

    public void removeCriterion(Long criteriaId, String sessionToken) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            evaluationCriteriaDao.delete(conn, criteriaId, sessionToken);
        }
    }

    /**
     * 비교표에 보여줄 전체 데이터를 조립한다. 지원자가 공유를 멈추거나 링크가 만료되면
     * (ShareLinkDao.findById가 활성·미만료만 돌려주므로) 자연히 비교표에서 빠진다.
     */
    public CompareView buildCompareView(Long sessionId, String sessionToken) throws SQLException {
        EvaluationSessionDto session = evaluationSessionDao.findByToken(sessionToken);

        List<EvaluationCriteriaDto> criteria = evaluationCriteriaDao.findBySessionId(sessionId);
        List<CriterionView> criterionViews = new ArrayList<>();
        for (EvaluationCriteriaDto c : criteria) {
            SkillDto skill = skillDao.findById(c.getSkillId());
            criterionViews.add(new CriterionView(c.getId(),
                    skill == null ? "(삭제된 기술)" : skill.getSkillName(), c.getWeight()));
        }
        int totalWeight = criteria.stream().mapToInt(EvaluationCriteriaDto::getWeight).sum();

        List<CandidateView> candidates = new ArrayList<>();
        for (EvaluationSessionItemDto item : evaluationSessionItemDao.findBySessionId(sessionId)) {
            ShareLinkDto link = shareLinkDao.findById(item.getShareLinkId());
            if (link == null) {
                continue; // 더 이상 유효하지 않은 링크 — 비교표에서 제외
            }
            UserDto user = userDao.findById(link.getUserId());
            long certCount = userSpecDao.findByUserId(user.getId()).stream()
                    .filter(s -> "CERT".equals(s.getSpecType())).count();
            long projectCount = userProjectDao.findByUserId(user.getId()).size();

            List<Boolean> hasSkillByCriterion = new ArrayList<>();
            Integer fitScorePercent = null;
            if (link.isScopeSkills()) {
                Set<Long> ownedSkillIds = new HashSet<>();
                for (UserSkillDto us : userSkillDao.findByUserId(user.getId())) {
                    if (us.getSkillId() != null) {
                        ownedSkillIds.add(us.getSkillId());
                    }
                }
                int matchedWeight = 0;
                for (EvaluationCriteriaDto c : criteria) {
                    boolean has = ownedSkillIds.contains(c.getSkillId());
                    hasSkillByCriterion.add(has);
                    if (has) {
                        matchedWeight += c.getWeight();
                    }
                }
                if (totalWeight > 0) {
                    fitScorePercent = (int) Math.round(matchedWeight * 100.0 / totalWeight);
                }
            } else {
                criteria.forEach(c -> hasSkillByCriterion.add(null));
            }

            SpecScoreService.GrowthSummary growth = link.isScopeGrowth()
                    ? specScoreService.getGrowthSummary(user.getId())
                    : null;

            candidates.add(new CandidateView(item.getId(), user.getMajor(), user.getGrade(), certCount,
                    projectCount, hasSkillByCriterion, fitScorePercent, growth));
        }

        return new CompareView(session, criterionViews, candidates);
    }

    private String extractToken(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        int idx = trimmed.lastIndexOf("/share/");
        return idx == -1 ? trimmed : trimmed.substring(idx + "/share/".length());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
