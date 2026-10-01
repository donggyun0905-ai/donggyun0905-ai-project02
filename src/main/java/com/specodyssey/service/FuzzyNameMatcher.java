package com.specodyssey.service;

import com.specodyssey.dao.SkillAliasDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.EditDistanceUtil;

import java.sql.SQLException;
import java.util.List;

/**
 * TD-1 임베딩 매칭(DJL+ONNX ko-sroberta)이 붙기 전까지 쓰는 중간 단계 구현
 * (팀 결정, 2026-09-30 — "시맨틱 매칭, 스킬 이름 일치라도 먼저").
 *
 * ⚠️ 진짜 의미 기반 매칭이 아니다. SKILL_ALIAS에 미리 등록해둔 것 이상의 동의어(사전에 없는
 * 새로운 표현)는 여전히 못 잡는다 — 그건 임베딩이 붙어야 풀리는 문제(TD-1 본 과제, 여전히
 * 미착수)다. 여기서 하는 건 ① 자주 쓰는 한글 표기·줄임말을 SKILL_ALIAS 사전으로 미리 커버하고,
 * ② 사전에도 없는 오타·띄어쓰기 차이는 편집거리로 흡수하는 것까지다.
 *
 * 1) 정확 일치(SKILL.skill_name, 대소문자 무시) → score 1.0
 * 2) 정확 일치(SKILL_ALIAS.alias_name) → score 1.0 (사전에 등록된 별칭이라 확실한 매칭으로 취급)
 * 3) 그래도 실패하면 SKILL 이름 + SKILL_ALIAS 별칭을 합친 후보군 전체와 편집거리를 비교해 가장
 *    가까운 것을 찾는다. 이름 길이의 40%를 넘는 차이는 다른 기술일 가능성이 커서 인정하지 않는다
 *    (ProfileService의 직무명 퍼지 매칭과 같은 임계값, 팀 합의 2026-09-29 재사용).
 */
public class FuzzyNameMatcher implements SkillMatcher {

    private static final double MAX_EDIT_DISTANCE_RATIO = 0.4;

    private final SkillDao skillDao = new SkillDao();
    private final SkillAliasDao skillAliasDao = new SkillAliasDao();

    @Override
    public MatchResult match(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return MatchResult.none();
        }
        String trimmed = raw.trim();

        SkillDto exact = skillDao.findByName(trimmed);
        if (exact != null) {
            return new MatchResult(exact.getId(), 1.0);
        }

        SkillAliasDto exactAlias = skillAliasDao.findByAliasName(trimmed);
        if (exactAlias != null) {
            return new MatchResult(exactAlias.getSkillId(), 1.0);
        }

        String normalizedQuery = normalize(trimmed);
        // 전체 후보는 매번 DB에서 읽지 않고 메모리 캐시를 쓴다 (2026-10-01, 매칭 속도 개선)
        SkillCatalog.Snapshot catalog = SkillCatalog.current();
        List<SkillDto> skills = catalog.skills();
        List<SkillAliasDto> aliases = catalog.aliases();

        Long bestSkillId = null;
        int bestDistance = Integer.MAX_VALUE;
        String bestName = null;
        for (SkillDto candidate : skills) {
            if (candidate.getSkillName() == null) {
                continue;
            }
            int distance = EditDistanceUtil.distance(normalizedQuery, normalize(candidate.getSkillName()));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSkillId = candidate.getId();
                bestName = candidate.getSkillName();
            }
        }
        for (SkillAliasDto candidate : aliases) {
            if (candidate.getAliasName() == null) {
                continue;
            }
            int distance = EditDistanceUtil.distance(normalizedQuery, normalize(candidate.getAliasName()));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSkillId = candidate.getSkillId();
                bestName = candidate.getAliasName();
            }
        }
        if (bestSkillId == null) {
            return MatchResult.none();
        }

        int threshold = Math.max(1, (int) Math.ceil(normalizedQuery.length() * MAX_EDIT_DISTANCE_RATIO));
        if (bestDistance > threshold) {
            return MatchResult.none();
        }

        int maxLen = Math.max(normalizedQuery.length(), normalize(bestName).length());
        double score = maxLen == 0 ? 0.0 : 1.0 - (double) bestDistance / maxLen;
        return new MatchResult(bestSkillId, score);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replace(" ", "");
    }
}
