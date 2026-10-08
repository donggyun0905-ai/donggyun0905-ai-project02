package com.specodyssey.service;

import com.specodyssey.dao.CertificationDao;
import com.specodyssey.dto.CertificationDto;
import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.EditDistanceUtil;
import com.specodyssey.util.GibberishDetector;
import com.specodyssey.util.LocalEmbedder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 프로필에서 기술·스펙 이름을 입력할 때 엉터리 글자를 걸러 내고, 오타·줄임말이면 맞는 이름을 제안한다.
 * 관련 요구사항: FR-23(스펙), FR-25(기술 스택), TD-1(임베딩 시맨틱 매칭) — 2026-10-06 사용자 요청
 *
 * 판단 순서
 * 1) 아는 이름(SKILL·SKILL_ALIAS, CERTIFICATION, 자격증 줄임말)과 같으면 통과
 * 2) 글자 모양이 엉터리(GibberishDetector)면 막는다 — 저장 요청도 서버에서 거절한다
 * 3) 기존 스킬 매칭(EmbeddingMatcher: 이름·별칭·오타·임베딩)이나 편집 거리·임베딩 유사도로 가까운 이름을 찾으면 제안
 * 4) 그래도 없으면 "목록에 없음" 안내 — 저장은 막지 않는다(새 기술일 수 있다)
 *
 * 임베딩 모델이 없는 PC에서는 3)의 임베딩 후보만 빠지고 나머지는 그대로 동작한다.
 * 임계값 실측(2026-10-06): 엉터리 입력과 가장 가까운 스킬은 0.58~0.61("sdafdsafdsa"→Kafka 0.61), 오타는 0.74~0.82
 * ("Pyhton"→Python 0.82, "Reakt"→React 0.74). 자격증 이름은 문장이 길어 점수가 높게 나와("dsfsdf"→ADsP 0.50,
 * "정보처리기싸"→정보처리기사 계열 0.79~0.82) 따로 잡는다.
 */
public class ProfileInputChecker {

    private static final Logger LOG = Logger.getLogger(ProfileInputChecker.class.getName());

    /** OK: 아는 이름 · SUGGEST: 고칠 후보 있음 · UNKNOWN: 목록에 없음(저장 가능) · GIBBERISH: 엉터리(저장 불가) */
    public enum Status { OK, SUGGEST, UNKNOWN, GIBBERISH }

    /** 검사 결과. message가 null이면 화면에 아무것도 띄우지 않는다. JSON으로 내보낸다. */
    public record Result(Status status, String message, List<String> suggestions) {
        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getSuggestions() {
            return suggestions;
        }
    }

    static final double SKILL_EMBEDDING_THRESHOLD = 0.70;
    static final double CERT_EMBEDDING_THRESHOLD = 0.75;
    static final double TYPO_THRESHOLD = 0.65;
    private static final int MIN_TYPO_LENGTH = 3;
    private static final int MAX_SUGGESTIONS = 3;
    private static final Pattern IGNORED = Pattern.compile("[\\s._-]");
    // 어학 항목의 점수·급수("토익 850", "OPIc IH")를 떼고 시험 이름만 본다
    private static final Pattern LANGUAGE_SCORE = Pattern.compile(
            "(?:\\s*\\d[\\d.,]*\\s*점?|\\s+(?:AL|IH|IM[123]?|IL|NH|NM|NL|LV\\.?\\s*\\d))\\s*$", Pattern.CASE_INSENSITIVE);

    // 자주 쓰는 자격증 줄임말 → CERTIFICATION.cert_name. 정규화(소문자·공백 제거)한 키로 찾는다.
    // "컴활"처럼 급수를 안 쓰면 두 급수를 모두 제안한다.
    private static final Map<String, List<String>> CERT_ABBREVIATIONS = Map.ofEntries(
            Map.entry("정처기", List.of("정보처리기사")),
            Map.entry("정처산기", List.of("정보처리산업기사")),
            Map.entry("정처기능사", List.of("정보처리기능사")),
            Map.entry("빅분기", List.of("빅데이터분석기사")),
            Map.entry("컴활", List.of("컴퓨터활용능력 1급", "컴퓨터활용능력 2급")),
            Map.entry("컴활1급", List.of("컴퓨터활용능력 1급")),
            Map.entry("컴활2급", List.of("컴퓨터활용능력 2급")),
            Map.entry("리마1급", List.of("리눅스마스터 1급")),
            Map.entry("리마2급", List.of("리눅스마스터 2급")),
            Map.entry("토익", List.of("TOEIC")),
            Map.entry("토익스피킹", List.of("TOEIC Speaking")),
            Map.entry("토스", List.of("TOEIC Speaking")),
            Map.entry("오픽", List.of("OPIc")));

    // 자격증 이름 벡터 — 이름이 바뀌지 않는 한 다시 계산하지 않는다 (40여 개라 첫 호출에 한 번)
    private static final Map<String, float[]> CERT_VECTORS = new ConcurrentHashMap<>();

    private final SkillMatcher skillMatcher;
    private final CertificationDao certificationDao;

    public ProfileInputChecker() {
        // 2026-10-08: 하이브리드 매칭 — 오타·표기 차이로 못 찾던 입력에 정식 이름을 더 잘 제안한다
        this(new HybridSkillMatcher(), new CertificationDao());
    }

    ProfileInputChecker(SkillMatcher skillMatcher, CertificationDao certificationDao) {
        this.skillMatcher = skillMatcher;
        this.certificationDao = certificationDao;
    }

    // ===== 기술 스택 (FR-25) =====

    public Result checkSkill(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return ok(null);
        }
        String input = raw.trim();
        SkillCatalog.Snapshot catalog = SkillCatalog.current();
        Map<String, Long> known = skillNameIndex(catalog);
        Map<Long, String> names = skillNames(catalog);

        Long exact = known.get(normalize(input));
        if (exact != null) {
            String name = names.get(exact);
            return normalize(name).equals(normalize(input)) ? ok(null) : ok("'" + name + "'(으)로 인식했어요.");
        }
        if (GibberishDetector.looksLikeGibberish(input)) {
            return gibberish("기술", skillCandidates(input, catalog, names));
        }

        List<SkillMatcher.MatchResult> matched = skillMatcher.matchAll(input);
        List<String> matchedNames = matched.stream().map(m -> names.get(m.skillId())).filter(n -> n != null).distinct().toList();
        if (!matchedNames.isEmpty() && matched.stream().allMatch(m -> m.score() >= 1.0)) {
            // "Java Spring"처럼 기술 여러 개를 한 칸에 쓴 경우 — 단어마다 정확히 찾았다
            return ok("'" + String.join("', '", matchedNames) + "'(으)로 인식했어요.");
        }
        // 기존 매칭(오타·앞부분·임베딩)이 찾았으면 그것만 — 후보를 더 붙이면 "Java 17"에 JavaScript까지 나온다
        List<String> suggestions = matchedNames.isEmpty() ? skillCandidates(input, catalog, names) : matchedNames;
        if (!suggestions.isEmpty()) {
            return new Result(Status.SUGGEST, "혹시 이 기술인가요? 누르면 이름이 바뀌어요.", limit(suggestions));
        }
        return new Result(Status.UNKNOWN,
                "목록에 없는 기술이에요. 그대로 저장할 수 있지만 격차 분석에는 반영되지 않을 수 있어요.", List.of());
    }

    /** 저장 요청을 서버에서 막을지 — 임베딩 없이 글자 모양만 본다(아는 이름이면 통과) */
    public boolean rejectsSkill(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return !skillNameIndex(SkillCatalog.current()).containsKey(normalize(raw))
                && GibberishDetector.looksLikeGibberish(raw);
    }

    // 편집 거리(오타)와 임베딩 유사도 중 높은 쪽으로 순위를 매긴다
    private List<String> skillCandidates(String input, SkillCatalog.Snapshot catalog, Map<Long, String> names) {
        Map<Long, Double> scores = new HashMap<>();
        String query = normalize(input);
        if (query.length() >= MIN_TYPO_LENGTH) {
            for (Map.Entry<String, Long> entry : skillNameIndex(catalog).entrySet()) {
                double similarity = typoSimilarity(query, entry.getKey());
                if (similarity >= TYPO_THRESHOLD) {
                    scores.merge(entry.getValue(), similarity, Math::max);
                }
            }
        }
        float[] vector = embed(input);
        if (vector != null) {
            for (SkillCatalog.SkillVector candidate : catalog.vectors()) {
                double similarity = LocalEmbedder.cosine(vector, candidate.vector());
                if (similarity >= SKILL_EMBEDDING_THRESHOLD) {
                    scores.merge(candidate.skillId(), similarity, Math::max);
                }
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(e -> names.get(e.getKey()))
                .filter(n -> n != null)
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private Map<String, Long> skillNameIndex(SkillCatalog.Snapshot catalog) {
        Map<String, Long> index = new HashMap<>();
        for (SkillDto skill : catalog.skills()) {
            if (skill.getSkillName() != null) {
                index.putIfAbsent(normalize(skill.getSkillName()), skill.getId());
            }
        }
        for (SkillAliasDto alias : catalog.aliases()) {
            if (alias.getAliasName() != null) {
                index.putIfAbsent(normalize(alias.getAliasName()), alias.getSkillId());
            }
        }
        index.remove("");
        return index;
    }

    private Map<Long, String> skillNames(SkillCatalog.Snapshot catalog) {
        Map<Long, String> names = new HashMap<>();
        for (SkillDto skill : catalog.skills()) {
            names.put(skill.getId(), skill.getSkillName());
        }
        return names;
    }

    // ===== 보유 스펙 (FR-23) =====

    /** specType: CERT(자격증) · LANGUAGE(어학) · AWARD(수상) */
    public Result checkSpec(String specType, String title) throws SQLException {
        if (title == null || title.isBlank()) {
            return ok(null);
        }
        String input = title.trim();
        if (!"CERT".equals(specType) && !"LANGUAGE".equals(specType)) {
            // 수상 등은 이름이 자유로워 엉터리 글자만 본다
            return GibberishDetector.looksLikeGibberish(input) ? gibberish("명칭", List.of()) : ok(null);
        }
        boolean language = "LANGUAGE".equals(specType);
        List<String> certNames = certificationDao.findAll().stream().map(CertificationDto::getCertName).toList();
        Map<String, String> byNormalized = new LinkedHashMap<>();
        for (String name : certNames) {
            byNormalized.putIfAbsent(normalize(name), name);
        }
        // 자격증 칸에 "토익 850"처럼 점수까지 쓴 경우도 — 떼고 나면 아는 이름일 때만 뗀다
        String stripped = LANGUAGE_SCORE.matcher(input).replaceAll("").trim();
        boolean strippedKnown = byNormalized.containsKey(normalize(stripped)) || CERT_ABBREVIATIONS.containsKey(normalize(stripped));
        String query = !stripped.isEmpty() && (language || strippedKnown) ? stripped : input;

        String normalized = normalize(query);
        if (byNormalized.containsKey(normalized)) {
            String name = byNormalized.get(normalized);
            return name.equals(query) ? ok(null) : ok("'" + name + "'(으)로 인식했어요.");
        }
        List<String> abbreviation = CERT_ABBREVIATIONS.getOrDefault(normalized, List.of()).stream()
                .filter(certNames::contains).toList();
        if (!abbreviation.isEmpty()) {
            // 어학은 "토익 850"처럼 줄여 써도 로드맵이 알아본다 — 자격증은 정식 이름이어야 로드맵 단계와 이어진다
            return language ? ok("'" + abbreviation.get(0) + "'(으)로 인식했어요.")
                    : new Result(Status.SUGGEST, "정식 이름으로 저장하면 로드맵의 자격증 단계와 이어져요.", abbreviation);
        }
        if (GibberishDetector.looksLikeGibberish(query)) {
            return gibberish("명칭", List.of());
        }

        List<String> candidates = certCandidates(query, certNames);
        if (!candidates.isEmpty()) {
            return new Result(Status.SUGGEST, "혹시 이 자격증인가요? 누르면 이름이 바뀌어요.", candidates);
        }
        if (language) {
            // TOEFL·JLPT 등 목록에 없는 어학 시험도 흔하다 — 따로 안내하지 않는다
            return ok(null);
        }
        return new Result(Status.UNKNOWN,
                "목록에 없는 자격증이에요. 그대로 저장할 수 있지만 로드맵의 자격증 단계와는 이어지지 않아요.", List.of());
    }

    /** 저장 요청을 서버에서 막을지 — 아는 자격증 이름·줄임말이면 통과 */
    public boolean rejectsSpec(String specType, String title) throws SQLException {
        if (title == null || title.isBlank()) {
            return false;
        }
        String normalized = normalize(title);
        if (CERT_ABBREVIATIONS.containsKey(normalized)) {
            return false;
        }
        boolean knownCert = certificationDao.findAll().stream().anyMatch(c -> normalize(c.getCertName()).equals(normalized));
        return !knownCert && GibberishDetector.looksLikeGibberish(title);
    }

    private List<String> certCandidates(String query, List<String> certNames) {
        Map<String, Double> scores = new HashMap<>();
        String normalized = normalize(query);
        for (String name : certNames) {
            String target = normalize(name);
            double similarity = normalized.length() >= MIN_TYPO_LENGTH ? typoSimilarity(normalized, target) : 0;
            // "정보처리" → 정보처리기사·산업기사·기능사, "AWS" → AWS 자격증들
            if (normalized.length() >= 2 && target.startsWith(normalized)) {
                similarity = Math.max(similarity, TYPO_THRESHOLD);
            }
            if (similarity >= TYPO_THRESHOLD) {
                scores.merge(name, similarity, Math::max);
            }
        }
        float[] vector = embed(query);
        if (vector != null) {
            for (String name : certNames) {
                float[] certVector = certVector(name);
                if (certVector == null) {
                    continue;
                }
                double similarity = LocalEmbedder.cosine(vector, certVector);
                if (similarity >= CERT_EMBEDDING_THRESHOLD) {
                    scores.merge(name, similarity, Math::max);
                }
            }
        }
        return limit(scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList());
    }

    private float[] certVector(String name) {
        float[] cached = CERT_VECTORS.get(name);
        if (cached != null) {
            return cached;
        }
        float[] vector = embed(name);
        if (vector != null) {
            CERT_VECTORS.put(name, vector);
        }
        return vector;
    }

    // ===== 공통 =====

    // 모델이 없거나 계산이 실패하면 null — 임베딩 후보만 빠진다
    private float[] embed(String text) {
        LocalEmbedder embedder = EmbeddingMatcher.embedder();
        if (embedder == null) {
            return null;
        }
        try {
            return embedder.embed(text);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "입력 검사용 임베딩 계산 실패 — 임베딩 후보 없이 진행합니다: " + text, e);
            return null;
        }
    }

    private static double typoSimilarity(String a, String b) {
        int distance = EditDistanceUtil.transpositionAwareDistance(a, b);
        return 1.0 - (double) distance / Math.max(a.length(), b.length());
    }

    private static Result ok(String message) {
        return new Result(Status.OK, message, List.of());
    }

    private static Result gibberish(String what, List<String> suggestions) {
        String message = suggestions.isEmpty()
                ? "의미 없는 글자처럼 보여요. " + what + "을 다시 확인해 주세요."
                : "의미 없는 글자처럼 보여요. 혹시 이 중 하나인가요?";
        return new Result(Status.GIBBERISH, message, suggestions);
    }

    private static List<String> limit(Iterable<String> names) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (out.size() == MAX_SUGGESTIONS) {
                break;
            }
            out.add(name);
        }
        return out;
    }

    private static String normalize(String s) {
        return s == null ? "" : IGNORED.matcher(s.trim().toLowerCase()).replaceAll("");
    }
}
