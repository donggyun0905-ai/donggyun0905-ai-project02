package com.specodyssey.service;

import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.EditDistanceUtil;

import java.sql.SQLException;
import java.util.List;

/**
 * TD-1 임베딩 매칭(DJL+ONNX ko-sroberta)이 붙기 전까지 쓰는 중간 단계 구현
 * (팀 결정, 2026-09-30 — "시맨틱 매칭, 스킬 이름 일치라도 먼저").
 *
 * ⚠️ 진짜 의미 기반 매칭이 아니다. "파이썬"↔"Python"처럼 표기 체계가 다른 동의어는 여전히
 * 못 잡는다 — 그건 임베딩이 붙어야 풀리는 문제(TD-1 본 과제, 여전히 미착수)다.
 * 여기서 하는 건 같은 표기 체계 안에서의 오타·띄어쓰기·대소문자 차이(예: "리엑트"↔"리액트",
 * "스프링부트"↔"스프링 부트")를 흡수하는 것뿐이다 — ExactMatcher보다 한 단계 나은 안전한 기본값.
 *
 * 1) 정확 일치(SKILL.skill_name, 대소문자 무시) → score 1.0
 * 2) 실패하면 SKILL 전체와 편집거리를 비교해 가장 가까운 것을 찾는다. 이름 길이의 40%를
 *    넘는 차이는 다른 기술일 가능성이 커서 인정하지 않는다(ProfileService의 직무명 퍼지 매칭과
 *    같은 임계값, 팀 합의 2026-09-29 재사용).
 */
public class FuzzyNameMatcher implements SkillMatcher {

    private static final double MAX_EDIT_DISTANCE_RATIO = 0.4;

    private final SkillDao skillDao = new SkillDao();

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

        String normalizedQuery = normalize(trimmed);
        List<SkillDto> candidates = skillDao.findAll();

        SkillDto best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (SkillDto candidate : candidates) {
            if (candidate.getSkillName() == null) {
                continue;
            }
            int distance = EditDistanceUtil.distance(normalizedQuery, normalize(candidate.getSkillName()));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best == null) {
            return MatchResult.none();
        }

        int threshold = Math.max(1, (int) Math.ceil(normalizedQuery.length() * MAX_EDIT_DISTANCE_RATIO));
        if (bestDistance > threshold) {
            return MatchResult.none();
        }

        int maxLen = Math.max(normalizedQuery.length(), normalize(best.getSkillName()).length());
        double score = maxLen == 0 ? 0.0 : 1.0 - (double) bestDistance / maxLen;
        return new MatchResult(best.getId(), score);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replace(" ", "");
    }
}
