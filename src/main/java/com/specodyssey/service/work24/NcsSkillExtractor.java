package com.specodyssey.service.work24;

import com.specodyssey.dto.SkillDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * NCS 지식·기술 문장(예: "REST(REpresentational State Transfer) API 활용 기술")에서 SKILL 표준 명칭을 찾는다.
 *
 * NCS 항목은 기술명이 아니라 설명형 문장이라 ExactMatcher(이름 완전 일치)로는 거의 안 맞는다.
 * 그래서 문장 안에 SKILL 이름(또는 아래 별칭)이 단어로 들어 있는지를 본다.
 * - "Java"가 "JavaScript" 안에서, "SQL"이 "MySQL" 안에서 잡히지 않게 앞뒤가 영문·숫자·기호(+#.)이면 다른 단어로 본다.
 * - C, R, Go처럼 짧은 이름은 문장 속에서 오탐이 많아 별칭으로만 찾는다.
 */
public class NcsSkillExtractor {

    // NCS 문장은 한글 표기가 많다 — SKILL 표준 명칭 → 추가로 인정할 표기(정규식)
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("Java", List.of("자바(?!\\s*스크립트)")),
            Map.entry("JavaScript", List.of("자바\\s*스크립트")),
            Map.entry("Python", List.of("파이썬")),
            Map.entry("C", List.of("C\\s*언어")),
            Map.entry("C++", List.of("C\\s*\\+\\+")),
            Map.entry("REST API", List.of("REST(?![A-Za-z])", "RESTful")),
            Map.entry("Linux", List.of("리눅스")),
            Map.entry("Docker", List.of("도커")),
            Map.entry("Kubernetes", List.of("쿠버네티스")),
            Map.entry("Oracle Database", List.of("Oracle", "오라클")),
            Map.entry("MS SQL Server", List.of("MSSQL", "MS-SQL")),
            Map.entry("Microservices Architecture", List.of("MSA", "마이크로\\s*서비스")),
            Map.entry("Spring", List.of("스프링(?!\\s*부트)")),
            Map.entry("Spring Boot", List.of("스프링\\s*부트")),
            Map.entry("HTML5", List.of("HTML")),
            Map.entry("CSS3", List.of("CSS")),
            Map.entry("Design Patterns", List.of("디자인\\s*패턴")),
            Map.entry("Unit Testing", List.of("단위\\s*테스트")),
            Map.entry("Agile/Scrum", List.of("애자일", "Agile", "Scrum")),
            Map.entry("TCP/IP", List.of("TCP")),
            Map.entry("Git", List.of("깃(?![가-힣A-Za-z])"))
    );
    private static final Set<String> ALIAS_ONLY = Set.of("C", "R", "Go");
    private static final String BEFORE = "(?<![A-Za-z0-9+#.])";
    private static final String AFTER = "(?![A-Za-z0-9+#])";

    private final Map<Long, Pattern> patterns = new LinkedHashMap<>();
    private final Map<Long, String> names = new LinkedHashMap<>();

    public NcsSkillExtractor(List<SkillDto> skills) {
        for (SkillDto skill : skills) {
            String name = skill.getSkillName();
            List<String> alts = new ArrayList<>(ALIASES.getOrDefault(name, List.of()));
            if (!ALIAS_ONLY.contains(name)) {
                alts.add(0, Pattern.quote(name));
            }
            if (alts.isEmpty()) {
                continue;
            }
            patterns.put(skill.getId(), Pattern.compile(BEFORE + "(?:" + String.join("|", alts) + ")" + AFTER,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            names.put(skill.getId(), name);
        }
    }

    /** 문장들에서 찾은 SKILL id (중복 없이, 처음 나온 순서) */
    public Set<Long> extract(List<String> sentences) {
        Set<Long> found = new LinkedHashSet<>();
        for (String sentence : sentences) {
            patterns.forEach((id, p) -> {
                if (p.matcher(sentence).find()) {
                    found.add(id);
                }
            });
        }
        return found;
    }

    public String nameOf(Long skillId) {
        return names.get(skillId);
    }
}
