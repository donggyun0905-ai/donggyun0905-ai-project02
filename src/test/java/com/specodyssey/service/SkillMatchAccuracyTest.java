package com.specodyssey.service;

import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.AppConfig;
import com.specodyssey.util.LocalEmbedder;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 기술명 매칭 정확도 측정 — 정답 세트 50개(src/test/resources/skill-match-gold.tsv)로
 * ① 정확 일치만(ExactMatcher) ② 이름·별칭·편집 거리(FuzzyNameMatcher) ③ 하이브리드(EmbeddingMatcher = ② + 임베딩)를 비교한다.
 * 관련 요구사항: TD-1 (2026-10-10, 외부 피드백 "매칭 정확도를 숫자로")
 *
 * 결과는 target/skill-match-accuracy.txt에 표로 남긴다 — docs/matching-accuracy.md의 수치가 여기서 나온다.
 * 임베딩 모델(EMBEDDING_MODEL_DIR)이 없는 PC에서는 ③이 ②와 같게 나온다(임베딩 단계만 빠짐). 결과 파일에 함께 적는다.
 * 공유 DB의 SKILL·SKILL_ALIAS 시드를 읽기만 한다.
 */
class SkillMatchAccuracyTest {

    private record Case(String category, String input, String expected) {
    }

    private record Score(int correct, int total, int falsePositives, int missed) {
        double accuracy() {
            return total == 0 ? 0 : (double) correct / total;
        }
    }

    @Test
    void 하이브리드_매칭이_정확_일치보다_정확하다() throws Exception {
        List<Case> cases = loadGold();
        assertTrue(cases.size() == 50, "정답 세트는 50개여야 한다: " + cases.size());
        Map<Long, String> names = new HashMap<>();
        for (SkillDto skill : SkillCatalog.current().skills()) {
            names.put(skill.getId(), skill.getSkillName());
        }

        Map<String, SkillMatcher> matchers = new LinkedHashMap<>();
        matchers.put("정확 일치만", new ExactMatcher());
        matchers.put("이름·별칭·편집거리", new FuzzyNameMatcher());
        matchers.put("하이브리드(+임베딩)", new EmbeddingMatcher());

        Map<String, Map<String, Score>> byCategory = new LinkedHashMap<>();
        Map<String, Score> overall = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();
        for (Map.Entry<String, SkillMatcher> entry : matchers.entrySet()) {
            Map<String, int[]> counts = new LinkedHashMap<>();
            int[] all = new int[4];
            for (Case c : cases) {
                SkillMatcher.MatchResult result = entry.getValue().match(c.input());
                String got = result.skillId() == null ? "-" : names.get(result.skillId());
                boolean ok = Objects.equals(got, c.expected());
                int[] cat = counts.computeIfAbsent(c.category(), k -> new int[4]);
                for (int[] bucket : new int[][]{cat, all}) {
                    bucket[1]++;
                    if (ok) {
                        bucket[0]++;
                    } else if (!"-".equals(got)) {
                        bucket[2]++; // 엉뚱한 기술로 연결(또는 매칭되면 안 되는데 연결)
                    } else {
                        bucket[3]++; // 못 찾음
                    }
                }
                if (!ok) {
                    misses.add(entry.getKey() + " | " + c.category() + " | " + c.input() + " → " + got + " (정답 " + c.expected() + ")");
                }
            }
            Map<String, Score> catScores = new LinkedHashMap<>();
            counts.forEach((k, v) -> catScores.put(k, new Score(v[0], v[1], v[2], v[3])));
            byCategory.put(entry.getKey(), catScores);
            overall.put(entry.getKey(), new Score(all[0], all[1], all[2], all[3]));
        }

        writeReport(cases, byCategory, overall, misses);

        Score exact = overall.get("정확 일치만");
        Score hybrid = overall.get("하이브리드(+임베딩)");
        assertTrue(hybrid.accuracy() > exact.accuracy(),
                "하이브리드 " + hybrid.accuracy() + " ≤ 정확 일치 " + exact.accuracy());
        // 이름·별칭·편집거리 단계만으로도 이 정도는 나와야 한다 — 매칭 규칙을 고치다 정확도가 크게 떨어지면 여기서 걸린다
        assertTrue(overall.get("이름·별칭·편집거리").accuracy() >= 0.8, "이름·별칭·편집거리 정확도 저하: " + overall.get("이름·별칭·편집거리"));
    }

    private List<Case> loadGold() throws Exception {
        List<Case> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/skill-match-gold.tsv")), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] cols = line.split("\t");
                cases.add(new Case(cols[0].trim(), cols[1].trim(), cols[2].trim()));
            }
        }
        return cases;
    }

    private void writeReport(List<Case> cases, Map<String, Map<String, Score>> byCategory, Map<String, Score> overall,
                             List<String> misses) throws Exception {
        String dir = AppConfig.get(LocalEmbedder.MODEL_DIR_KEY);
        boolean embedding = dir != null && Files.isRegularFile(Path.of(dir, "model.onnx")) && EmbeddingMatcher.embedder() != null;
        Path out = Path.of("target", "skill-match-accuracy.txt");
        Files.createDirectories(out.getParent());
        try (PrintStream ps = new PrintStream(Files.newOutputStream(out), true, StandardCharsets.UTF_8)) {
            ps.println("기술명 매칭 정확도 — 정답 세트 " + cases.size() + "개, 임베딩 모델 " + (embedding ? "있음" : "없음(하이브리드 = 편집거리까지)"));
            ps.println();
            List<String> categories = new ArrayList<>(byCategory.values().iterator().next().keySet());
            ps.print("| 방식 | 전체 |");
            categories.forEach(c -> ps.print(" " + c + " |"));
            ps.println(" 엉뚱한 연결 | 못 찾음 |");
            for (Map.Entry<String, Score> e : overall.entrySet()) {
                Score s = e.getValue();
                ps.printf("| %s | %d/%d (%.0f%%) |", e.getKey(), s.correct(), s.total(), s.accuracy() * 100);
                for (String c : categories) {
                    Score cs = byCategory.get(e.getKey()).get(c);
                    ps.printf(" %d/%d |", cs.correct(), cs.total());
                }
                ps.printf(" %d | %d |%n", s.falsePositives(), s.missed());
            }
            ps.println();
            ps.println("틀린 항목:");
            misses.forEach(m -> ps.println("  " + m));
        }
    }
}
