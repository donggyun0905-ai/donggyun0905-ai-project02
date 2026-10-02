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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 스킬 이름·별칭(SKILL_ALIAS) 기반 매칭. 의미 기반 매칭(EmbeddingMatcher)이 이 매처를 먼저 쓰고,
 * 여기서 못 찾았을 때만 임베딩으로 넘어간다 (2026-09-30 동균 작성, 2026-10-02 정확도 개선).
 *
 * 순서 — 앞 단계에서 찾으면 거기서 끝낸다. 잘못 연결하느니 못 찾는 쪽을 택한다.
 * 1) 입력 전체가 SKILL 이름·별칭과 정확히 같음 → score 1.0
 * 2) 단어 단위 정확 일치: 띄어쓰기·쉼표·슬래시로 나눈 단어가 이름·별칭과 같으면 그 스킬 (여러 개면 모두)
 *    예) "Java Spring" → Java, Spring / "자바 백엔드" → Java
 * 3) 앞부분 일치: 입력이 이름·별칭으로 시작하고 그것이 입력의 절반 이상이면 그 스킬
 *    예) "Java 17"·"JavaEE"·"Java8" → Java, "Reactjs"·"React.js" → React
 * 4) 오타: 이름의 75% 이상이 같을 때만 (이웃 글자 순서 바뀜은 1글자로 셈). 3글자 이하 입력은 오타를 허용하지 않는다
 *    예) "Pyhton" → Python, "Kubernets" → Kubernetes
 *
 * 2026-10-02 이전에는 4)만 있었고 이름 길이 40%까지 차이를 허용해서 "Java Spring"→JavaScript,
 * "JSP"→JavaScript, "ES6"→CSS3, "React.js"→Next.js처럼 다른 기술로 연결되는 일이 있었다.
 */
public class FuzzyNameMatcher implements SkillMatcher {

    private static final double MIN_TYPO_SIMILARITY = 0.75;
    // 이보다 짧은 입력은 한두 글자 차이가 곧 다른 기술이다 (JSP↔JS, ES6↔CSS3)
    private static final int MIN_TYPO_LENGTH = 4;
    // 앞부분 일치로 인정할 이름의 최소 길이 — Go·R 같은 짧은 이름이 아무 입력 앞에나 붙지 않게
    private static final int MIN_PREFIX_LENGTH = 3;
    // 앞부분 뒤에 글자가 바로 이어져도 같은 기술로 보는 꼬리 ("Reactjs", "JavaEE")
    private static final Set<String> PREFIX_SUFFIXES = Set.of("js", "ee");
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[\\s,/+&·()]+");

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

        // 1) 정확 일치는 DB 콜레이션(대소문자 무시) 규칙 그대로 확인한다
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

        List<MatchResult> words = wordMatches(trimmed, byName);
        if (!words.isEmpty()) {
            return words;
        }
        String query = normalize(trimmed);
        MatchResult prefix = prefixMatch(query, byName);
        if (prefix != null) {
            return List.of(prefix);
        }
        MatchResult typo = typoMatch(query, byName);
        return typo == null ? List.of() : List.of(typo);
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

    // 2) 입력에 나온 순서대로, 같은 스킬은 한 번만
    private List<MatchResult> wordMatches(String input, Map<String, Long> byName) {
        List<MatchResult> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String word : WORD_SEPARATOR.split(input)) {
            Long skillId = byName.get(normalize(word));
            if (skillId != null && seen.add(skillId)) {
                found.add(new MatchResult(skillId, 1.0));
            }
        }
        return found;
    }

    // 3) 가장 긴 이름을 고른다 — "javascriptes6"은 "java"가 아니라 "javascript"로
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

    // 4)
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

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replace(" ", "");
    }
}
