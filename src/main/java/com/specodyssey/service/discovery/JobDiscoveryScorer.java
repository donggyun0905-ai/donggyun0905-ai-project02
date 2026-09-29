package com.specodyssey.service.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 직무 발굴 추천 점수 계산기. 관련 요구사항: FR-34 · 38
 *
 * DB·HTTP를 모르는 순수 계산 클래스다. JobDiscoveryService가 DAO와 SkillMatcher로 재료를 만들어 넘기고,
 * 결과를 JOB_RECOMMENDATION에 저장한다. 그래서 DB 없이 단위 테스트로 검증할 수 있다.
 *
 * ── 점수 공식 ───────────────────────────────────────────────
 *   설문 점수(계열)  = (그 계열 문항 응답 평균 - 1) / 4                         → 0.0 ~ 1.0
 *   스펙 점수(직무)  = Σ(요구 기술 가중치 × 인정도) / Σ(요구 기술 가중치)         → 0.0 ~ 1.0
 *                     가중치: REQUIRED 2, PREFERRED 1
 *                     인정도: 보유 기술이 그 skill_id로 매칭된 점수(SkillMatcher score)를
 *                             0.50 이하 → 0, 0.85 이상 → 1, 그 사이는 직선으로 부분 인정
 *   스펙 비중       = 0.5 × min(1, 매칭된 보유 기술 수 / 5)   ← 스펙이 없으면 설문 100%
 *   최종 점수       = (1 - 스펙 비중) × 설문 점수 + 스펙 비중 × 스펙 점수
 *   요구 기술 데이터가 없는 직무는 스펙 점수 자리에 이 사용자의 평균 스펙 점수를 넣는다
 *   (0이면 부당하게 깎이고, 설문만 쓰면 데이터 없는 직무가 오히려 유리해진다).
 *
 * ── 후보 고르기 ─────────────────────────────────────────────
 *   · 최종 점수 내림차순, 같은 계열은 1개만 (발굴 = 서로 다른 방향을 보여주는 것)
 *   · 3개는 항상, 4·5번째는 1순위 점수의 80% 이상일 때만 → 3~5개 (FR-34)
 *
 * ExactMatcher는 score 1.0만 주므로 지금은 "이름이 같으면 인정"으로 동작하고,
 * EmbeddingMatcher로 교체되면 부분 인정이 자동으로 살아난다. 이 클래스는 고칠 필요가 없다.
 */
public class JobDiscoveryScorer {

    // ---- 튜닝 값 (팀 논의로 바꿔도 되는 숫자는 전부 여기) ----
    static final double SIM_FLOOR = 0.50;
    static final double SIM_FULL = 0.85;
    static final double MATCH_SHOW_THRESHOLD = 0.70;
    static final int WEIGHT_REQUIRED = 2;
    static final int WEIGHT_PREFERRED = 1;
    static final double SPEC_WEIGHT_MAX = 0.5;
    static final int SKILLS_FOR_FULL_SPEC_WEIGHT = 5;
    static final int MAX_PER_CATEGORY = 1;
    static final int MIN_RESULTS = 3;
    static final int MAX_RESULTS = 5;
    static final double EXTRA_RESULT_CUTOFF = 0.80;

    static final Map<String, String> CATEGORY_KO = Map.of(
            "BACKEND", "서버·로직",
            "FRONTEND", "화면·사용자 경험",
            "DATA", "데이터 정리·분석",
            "DEVOPS", "운영·자동화",
            "SECURITY", "보안",
            "PM", "기획·조율");

    // ================= 입력 / 출력 =================

    /** 설문 응답 한 건: 그 문항의 job_category_hint + 응답값(1~5). */
    public record SurveyAnswer(String category, int value) {
        public SurveyAnswer {
            if (value < 1 || value > 5) {
                throw new IllegalArgumentException("answer_value는 1~5여야 합니다: " + value);
            }
        }
    }

    /** 사용자가 가진 표준 스킬 하나: SkillMatcher 결과(skillId, score) + 화면에 보여줄 원문. */
    public record OwnedSkill(long skillId, String displayName, double score) {}

    /** JOB_REQUIRED_SKILL 한 줄. */
    public record RequiredSkill(long skillId, boolean required) {}

    /** JOB 한 줄 + 그 직무의 요구 기술. */
    public record JobCandidate(long jobId, String jobName, String category, List<RequiredSkill> skills) {
        public JobCandidate {
            skills = skills == null ? List.of() : skills;
        }
    }

    /** 추천 결과 한 건 → JOB_RECOMMENDATION 한 줄. */
    public static class Recommendation {
        public long jobId;
        public String jobName;
        public String category;
        public int rankOrder;
        public double totalScore;
        public double surveyScore;
        public Double specScore;              // 요구 기술 데이터가 없으면 null
        public final List<String> matchedSkills = new ArrayList<>();
        public String reason;                 // match_reason — LLM이 없거나 실패해도 쓸 수 있는 기본 문장

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d순위 %s total=%.3f survey=%.3f spec=%s matched=%s | %s",
                    rankOrder, jobName, totalScore, surveyScore,
                    specScore == null ? "없음" : String.format(Locale.ROOT, "%.3f", specScore),
                    matchedSkills, reason);
        }
    }

    // ================= 계산 =================

    public List<Recommendation> recommend(List<SurveyAnswer> answers, List<OwnedSkill> ownedSkills,
                                          List<JobCandidate> jobs) {
        Map<String, Double> surveyAvg = averageByCategory(answers);
        Map<Long, OwnedSkill> owned = bestPerSkill(ownedSkills);
        double specWeight = SPEC_WEIGHT_MAX * Math.min(1.0, (double) owned.size() / SKILLS_FOR_FULL_SPEC_WEIGHT);

        List<Recommendation> scored = new ArrayList<>();
        double specSum = 0;
        int specCount = 0;
        for (JobCandidate job : jobs) {
            Recommendation r = new Recommendation();
            r.jobId = job.jobId();
            r.jobName = job.jobName();
            r.category = job.category();
            Double avg = surveyAvg.get(job.category());
            r.surveyScore = avg == null ? 0.0 : (avg - 1.0) / 4.0;
            r.specScore = specScore(job, owned, r.matchedSkills);
            if (r.specScore != null) {
                specSum += r.specScore;
                specCount++;
            }
            r.reason = buildReason(job, avg, r.matchedSkills);
            scored.add(r);
        }

        double neutralSpec = specCount == 0 ? 0.0 : specSum / specCount;
        for (Recommendation r : scored) {
            double spec = r.specScore == null ? neutralSpec : r.specScore;
            r.totalScore = (1 - specWeight) * r.surveyScore + specWeight * spec;
        }

        scored.sort(Comparator.comparingDouble((Recommendation r) -> r.totalScore).reversed()
                .thenComparingLong(r -> r.jobId)); // 동점이면 id 순 — 같은 입력이면 항상 같은 결과
        return pick(scored);
    }

    static double credit(double score) {
        if (score <= SIM_FLOOR) {
            return 0;
        }
        if (score >= SIM_FULL) {
            return 1;
        }
        return (score - SIM_FLOOR) / (SIM_FULL - SIM_FLOOR);
    }

    private Map<String, Double> averageByCategory(List<SurveyAnswer> answers) {
        Map<String, int[]> acc = new HashMap<>(); // [합, 개수]
        for (SurveyAnswer a : answers) {
            int[] v = acc.computeIfAbsent(a.category(), k -> new int[2]);
            v[0] += a.value();
            v[1]++;
        }
        Map<String, Double> avg = new HashMap<>();
        acc.forEach((k, v) -> avg.put(k, (double) v[0] / v[1]));
        return avg;
    }

    /** 같은 표준 스킬로 매칭된 원문이 여러 개면(예: "Java", "java") 점수가 가장 높은 것만 남긴다. */
    private Map<Long, OwnedSkill> bestPerSkill(List<OwnedSkill> ownedSkills) {
        Map<Long, OwnedSkill> best = new HashMap<>();
        for (OwnedSkill s : ownedSkills) {
            best.merge(s.skillId(), s, (a, b) -> a.score() >= b.score() ? a : b);
        }
        return best;
    }

    private Double specScore(JobCandidate job, Map<Long, OwnedSkill> owned, List<String> matchedOut) {
        if (job.skills().isEmpty()) {
            return null;
        }
        double got = 0;
        double total = 0;
        for (RequiredSkill req : job.skills()) {
            int weight = req.required() ? WEIGHT_REQUIRED : WEIGHT_PREFERRED;
            total += weight;
            OwnedSkill mine = owned.get(req.skillId());
            if (mine == null) {
                continue;
            }
            got += weight * credit(mine.score());
            if (mine.score() >= MATCH_SHOW_THRESHOLD && !matchedOut.contains(mine.displayName())) {
                matchedOut.add(mine.displayName());
            }
        }
        return got / total;
    }

    private List<Recommendation> pick(List<Recommendation> sorted) {
        List<Recommendation> out = new ArrayList<>();
        Map<String, Integer> perCategory = new HashMap<>();
        double top = sorted.isEmpty() ? 0 : sorted.get(0).totalScore;
        for (Recommendation r : sorted) {
            if (out.size() >= MAX_RESULTS) {
                break;
            }
            if (perCategory.getOrDefault(r.category, 0) >= MAX_PER_CATEGORY) {
                continue;
            }
            if (out.size() >= MIN_RESULTS && r.totalScore < top * EXTRA_RESULT_CUTOFF) {
                break;
            }
            perCategory.merge(r.category, 1, Integer::sum);
            r.rankOrder = out.size() + 1;
            out.add(r);
        }
        return out;
    }

    private String buildReason(JobCandidate job, Double avg, List<String> matched) {
        String ko = CATEGORY_KO.getOrDefault(job.category(), job.category());
        StringBuilder sb = new StringBuilder();
        if (avg != null) {
            sb.append(String.format(Locale.ROOT, "설문에서 %s 관련 문항에 평균 %.1f점(5점 만점)으로 답했고", ko, avg));
        } else {
            sb.append("설문 응답과는 별개로");
        }
        if (!matched.isEmpty()) {
            List<String> shown = matched.size() > 3 ? matched.subList(0, 3) : matched;
            sb.append(", 보유 기술 ").append(String.join(", ", shown)).append("이(가) 이 직무 요구 기술과 겹칩니다.");
        } else if (job.skills().isEmpty()) {
            sb.append(", 이 직무의 요구 기술 데이터는 아직 수집 중이라 설문 기준으로 골랐습니다.");
        } else {
            sb.append(", 아직 겹치는 보유 기술이 적어 설문 응답을 중심으로 골랐습니다.");
        }
        return sb.toString();
    }
}
