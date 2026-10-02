package com.specodyssey.service;

import com.specodyssey.dao.SkillAliasDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.EditDistanceUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 스킬 이름·별칭(SKILL_ALIAS) 기반 매칭. 의미 기반 매칭(EmbeddingMatcher)이 이 매처를 먼저 쓰고,
 * 여기서 못 찾았을 때만 임베딩으로 넘어간다 (2026-09-30 동균 작성, 2026-10-02 정확도 개선).
 *
 * 앞 단계에서 찾으면 거기서 끝낸다. 잘못 연결하느니 못 찾는 쪽을 택한다.
 * 비교할 때는 대소문자와 공백·점·하이픈·밑줄을 무시한다 ("Next JS" = "Next.js", "Java Script" = "JavaScript").
 *
 * 1) 입력 전체가 SKILL 이름·별칭과 같음 → score 1.0 ("CI/CD"처럼 구분자가 들어간 이름도 여기서 잡힌다)
 * 2) 쉼표·슬래시 등 목록 구분자로 나눈 조각마다 아래를 차례로 본다 (여러 개면 모두). 띄어쓰기로는 먼저 나누지 않는다 —
 *    "Spring boot 3"·"Next JS"처럼 기술 하나를 띄어 쓰는 경우가 많아서다.
 *    a) 조각 전체가 이름·별칭과 같음
 *    b) 앞부분 일치: 조각이 이름으로 시작하고 그 이름이 조각의 절반 이상, 뒤는 숫자·기호이거나 js·ee 꼬리
 *       예) "Java 17"·"JavaEE" → Java, "React.js" → React, "Spring boot 3" → Spring Boot
 *    c) 이름의 앞부분만 씀: 조각으로 시작하는 이름이 하나뿐이고 그 이름의 70% 이상일 때 예) "Tailwind" → Tailwind CSS
 *    d) 오타: 75% 이상 같을 때만 (이웃 글자 순서 바뀜은 1글자). 4글자 이하는 오타를 허용하지 않는다 (REST↔Rust, SCSS↔CSS)
 *    e) 그래도 없으면 띄어쓰기로 나눈 단어가 이름·별칭과 같은지 예) "Java Spring" → Java, Spring / "자바 백엔드" → Java
 *
 * 2026-10-02 이전에는 d)만 있었고 이름 길이 40%까지 차이를 허용해서 "Java Spring"→JavaScript,
 * "JSP"→JavaScript, "ES6"→CSS3, "React.js"→Next.js처럼 다른 기술로 연결되는 일이 있었다.
 */
public class FuzzyNameMatcher implements SkillMatcher {

    private static final double MIN_TYPO_SIMILARITY = 0.75;
    // 이 길이보다 짧으면 한두 글자 차이가 곧 다른 기술이다 (JSP↔JS, ES6↔CSS3, REST↔Rust, SCSS↔CSS)
    private static final int MIN_TYPO_LENGTH = 5;
    // 앞부분 일치로 인정할 이름의 최소 길이 — Go·R 같은 짧은 이름이 아무 입력 앞에나 붙지 않게
    private static final int MIN_PREFIX_LENGTH = 3;
    // 이름의 앞부분만 쓴 입력("Tailwind")을 인정할 최소 비율
    private static final double MIN_PARTIAL_COVERAGE = 0.7;
    // 앞부분 뒤에 글자가 바로 이어져도 같은 기술로 보는 꼬리 ("Reactjs", "JavaEE")
    private static final Set<String> PREFIX_SUFFIXES = Set.of("js", "ee");
    // "+"는 넣지 않는다 — C++처럼 이름에 들어 있다
    private static final Pattern LIST_SEPARATOR = Pattern.compile("[,/&·()]+");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern IGNORED = Pattern.compile("[\\s._-]");

    private final SkillDao skillDao = new SkillDao();
    private final SkillAliasDao skillAliasDao = new SkillAliasDao();

    @Override
    public MatchResult match(String raw) throws SQLException {
        List<MatchResult> all = matchAll(raw);
        return all.isEmpty() ? MatchResult.none() : all.get(0);
    }

    @Override
    public List<MatchResult> matchAll(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();

        // 1) 정확 일치는 DB 콜레이션(대소문자 무시) 규칙 그대로 먼저 확인한다
        SkillDto exact = skillDao.findByName(trimmed);
        if (exact != null) {
            return List.of(new MatchResult(exact.getId(), 1.0));
        }
        SkillAliasDto exactAlias = skillAliasDao.findByAliasName(trimmed);
        if (exactAlias != null) {
            return List.of(new MatchResult(exactAlias.getSkillId(), 1.0));
        }

        // 나머지 단계는 매번 DB를 읽지 않고 메모리 캐시를 쓴다 (2026-10-01, 매칭 속도 개선)
        Map<String, Long> byName = nameIndex(SkillCatalog.current());
        Long whole = byName.get(normalize(trimmed));
        if (whole != null) {
            return List.of(new MatchResult(whole, 1.0));
        }

        // 2) 목록 구분자로 나눈 조각마다, 입력 순서대로 같은 스킬은 한 번만
        List<MatchResult> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String piece : LIST_SEPARATOR.split(trimmed)) {
            if (piece.isBlank()) {
                continue;
            }
            for (MatchResult result : matchPiece(piece.trim(), byName)) {
                if (seen.add(result.skillId())) {
                    found.add(result);
                }
            }
        }
        return found;
    }

    private List<MatchResult> matchPiece(String piece, Map<String, Long> byName) {
        String query = normalize(piece);
        if (query.isEmpty()) {
            return List.of();
        }
        Long exact = byName.get(query);
        if (exact != null) {
            return List.of(new MatchResult(exact, 1.0));
        }
        MatchResult single = prefixMatch(query, byName);
        if (single == null) {
            single = partialNameMatch(query, byName);
        }
        if (single == null) {
            single = typoMatch(query, byName);
        }
        if (single != null) {
            return List.of(single);
        }
        return wordMatches(piece, byName);
    }

    // 정규화한 이름·별칭 → skill_id. 이름이 별칭보다 우선한다.
    private Map<String, Long> nameIndex(SkillCatalog.Snapshot catalog) {
        Map<String, Long> byName = new HashMap<>();
        for (SkillDto skill : catalog.skills()) {
            if (skill.getSkillName() != null) {
                byName.putIfAbsent(normalize(skill.getSkillName()), skill.getId());
            }
        }
        for (SkillAliasDto alias : catalog.aliases()) {
            if (alias.getAliasName() != null) {
                byName.putIfAbsent(normalize(alias.getAliasName()), alias.getSkillId());
            }
        }
        byName.remove("");
        return byName;
    }

    // b) 가장 긴 이름을 고른다 — "javascriptes6"은 "java"가 아니라 "javascript"로
    private MatchResult prefixMatch(String query, Map<String, Long> byName) {
        String best = null;
        for (String name : byName.keySet()) {
            if (name.length() < MIN_PREFIX_LENGTH || name.length() >= query.length()
                    || name.length() * 2 < query.length() || !query.startsWith(name)) {
                continue;
            }
            String rest = query.substring(name.length());
            // "Javadoc"처럼 글자가 그대로 이어지면 다른 단어일 수 있다 — 숫자·기호로 끊기거나 정해진 꼬리일 때만
            boolean boundary = !Character.isLetter(rest.charAt(0)) || PREFIX_SUFFIXES.contains(rest);
            if (boundary && (best == null || name.length() > best.length())) {
                best = name;
            }
        }
        return best == null ? null : new MatchResult(byName.get(best), (double) best.length() / query.length());
    }

    // c) 조각으로 시작하는 이름이 한 스킬뿐일 때만 — "Apache"처럼 후보가 여럿이면 고르지 않는다
    private MatchResult partialNameMatch(String query, Map<String, Long> byName) {
        if (query.length() < MIN_PREFIX_LENGTH) {
            return null;
        }
        Long skillId = null;
        String matchedName = null;
        for (Map.Entry<String, Long> entry : byName.entrySet()) {
            String name = entry.getKey();
            if (name.length() <= query.length() || !name.startsWith(query)) {
                continue;
            }
            if (skillId != null && !skillId.equals(entry.getValue())) {
                return null;
            }
            if (matchedName == null || name.length() < matchedName.length()) {
                matchedName = name;
            }
            skillId = entry.getValue();
        }
        if (skillId == null || query.length() < matchedName.length() * MIN_PARTIAL_COVERAGE) {
            return null;
        }
        return new MatchResult(skillId, (double) query.length() / matchedName.length());
    }

    // d)
    private MatchResult typoMatch(String query, Map<String, Long> byName) {
        if (query.length() < MIN_TYPO_LENGTH) {
            return null;
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String name : byName.keySet()) {
            int distance = EditDistanceUtil.transpositionAwareDistance(query, name);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = name;
            }
        }
        if (best == null) {
            return null;
        }
        double similarity = 1.0 - (double) bestDistance / Math.max(query.length(), best.length());
        return similarity >= MIN_TYPO_SIMILARITY ? new MatchResult(byName.get(best), similarity) : null;
    }

    // e) 입력에 나온 순서대로, 같은 스킬은 한 번만
    private List<MatchResult> wordMatches(String piece, Map<String, Long> byName) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String word : SPACE.split(piece)) {
            Long skillId = byName.get(normalize(word));
            if (skillId != null) {
                ids.add(skillId);
            }
        }
        List<MatchResult> found = new ArrayList<>();
        for (Long id : ids) {
            found.add(new MatchResult(id, 1.0));
        }
        return found;
    }

    private String normalize(String s) {
        return s == null ? "" : IGNORED.matcher(s.trim().toLowerCase()).replaceAll("");
    }
}
