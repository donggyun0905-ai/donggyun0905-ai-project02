package com.specodyssey.service.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 직무 발굴 추천 점수 계산기 (FR-34 · 38)  — 담당: C. 직무 발굴
 *
 * DB·HTTP 를 전혀 모르는 순수 계산 클래스다. 서비스(JobDiscoveryService)가 DAO 로 재료를 읽어서 넘기고,
 * 결과를 JobRecommendationDao.insert 로 저장한다. 그래서 DB 없이 main/단위테스트로 바로 돌려볼 수 있다.
 *
 * ── 점수 공식 ───────────────────────────────────────────────
 *   설문 점수(계열)  = (그 계열 문항 응답 평균 - 1) / 4                       → 0.0 ~ 1.0
 *   스펙 점수(직무)  = Σ(요구 기술 가중치 × 인정도) / Σ(요구 기술 가중치)       → 0.0 ~ 1.0
 *                     가중치: REQUIRED 2, PREFERRED 1
 *                     인정도: 보유 기술 중 가장 비슷한 것과의 유사도를
 *                             0.50 이하 → 0,  0.85 이상 → 1,  그 사이는 직선으로 부분 인정
 *   스펙 비중       = 0.5 × min(1, 보유 기술 수 / 5)   ← 스펙이 없는 신입은 설문 100%
 *   최종 점수       = (1 - 스펙 비중) × 설문 점수 + 스펙 비중 × 스펙 점수
 *   (요구 기술 데이터가 아직 없는 직무는 스펙 점수 자리에 이 사용자의 평균 스펙 점수를 넣음)
 *
 * ── 후보 고르기 ─────────────────────────────────────────────
 *   · 최종 점수 내림차순. 같은 계열은 1개만 (백엔드 개발자·서버 개발자가 같이 뽑히지 않게)
 *   · 최소 3개는 항상 보여주고, 4·5번째는 1순위 점수의 80% 이상일 때만 추가 → 3~5개
 *
 * ── 유사도 ─────────────────────────────────────────────────
 *   SkillSimilarity 인터페이스로 뺐다. E 파트(임베딩)가 준비되기 전에는 EXACT_MATCH 로 돌리고,
 *   준비되면 임베딩 코사인 유사도 구현체로 갈아끼우기만 하면 된다.
 */
public class JobDiscoveryScorer {

    // ---- 튜닝 값 (팀 논의로 바꿔도 되는 숫자는 전부 여기) ----
    static final double SIM_FLOOR = 0.50;              // 이 이하 유사도는 "관련 없음"
    static final double SIM_FULL = 0.85;               // 이 이상이면 "같은 기술"로 완전 인정
    static final double MATCH_SHOW_THRESHOLD = 0.70;   // 추천 이유에 "겹치는 기술"로 적을 기준
    static final int WEIGHT_REQUIRED = 2;
    static final int WEIGHT_PREFERRED = 1;
    static final double SPEC_WEIGHT_MAX = 0.5;
    static final int SKILLS_FOR_FULL_SPEC_WEIGHT = 5;
    static final int MAX_PER_CATEGORY = 1;             // 발굴 = 서로 다른 방향을 보여주는 것
    static final int MIN_RESULTS = 3;
    static final int MAX_RESULTS = 5;
    static final double EXTRA_RESULT_CUTOFF = 0.80;

    static final Map<String, String> CATEGORY_KO = new HashMap<>();
    static {
        CATEGORY_KO.put("BACKEND", "서버·로직");
        CATEGORY_KO.put("FRONTEND", "화면·사용자 경험");
        CATEGORY_KO.put("DATA", "데이터 정리·분석");
        CATEGORY_KO.put("DEVOPS", "운영·자동화");
        CATEGORY_KO.put("SECURITY", "보안");
        CATEGORY_KO.put("PM", "기획·조율");
    }

    /** 보유 기술 원문 ↔ 요구 기술명 유사도 (0.0 ~ 1.0). */
    public interface SkillSimilarity {
        double similarity(String userSkill, String requiredSkill);
    }

    /** 임베딩 전 임시 구현: 대소문자·공백·점·하이픈 무시하고 같으면 1, 아니면 0. */
    public static final SkillSimilarity EXACT_MATCH =
            (a, b) -> normalize(a).equals(normalize(b)) ? 1.0 : 0.0;

    private final SkillSimilarity similarity;

    public JobDiscoveryScorer(SkillSimilarity similarity) {
        this.similarity = similarity;
    }

    // ================= 입력 / 출력 =================

    /** USER_SURVEY_ANSWER 한 줄 + 그 문항의 job_category_hint. */
    public static class SurveyAnswer {
        final String category;
        final int value; // 1~5
        public SurveyAnswer(String category, int value) {
            if (value < 1 || value > 5) throw new IllegalArgumentException("answer_value 는 1~5: " + value);
            this.category = category;
            this.value = value;
        }
    }

    /** JOB_REQUIRED_SKILL 한 줄 (SKILL.skill_name 과 importance). */
    public static class RequiredSkill {
        final String skillName;
        final boolean required; // REQUIRED = true, PREFERRED = false
        public RequiredSkill(String skillName, boolean required) {
            this.skillName = skillName;
            this.required = required;
        }
    }

    /** JOB 한 줄 + 그 직무의 요구 기술 목록. */
    public static class JobCandidate {
        final long jobId;
        final String jobName;
        final String category;
        final List<RequiredSkill> skills;
        public JobCandidate(long jobId, String jobName, String category, List<RequiredSkill> skills) {
            this.jobId = jobId;
            this.jobName = jobName;
            this.category = category;
            this.skills = skills == null ? new ArrayList<>() : skills;
        }
    }

    /** 추천 결과 한 건 → JOB_RECOMMENDATION 한 줄이 된다. */
    public static class Recommendation {
        public long jobId;
        public String jobName;
        public String category;
        public int rankOrder;            // → rank_order
        public double totalScore;
        public double surveyScore;
        public Double specScore;         // 요구 기술 데이터가 없는 직무면 null
        public List<String> matchedSkills = new ArrayList<>();
        public String fallbackReason;    // LLM 실패/미연동 시 match_reason 에 그대로 저장 (FR-111)

        @Override public String toString() {
            return String.format(Locale.ROOT, "%d순위 %-10s total=%.3f survey=%.3f spec=%s matched=%s%n      이유: %s",
                    rankOrder, jobName, totalScore, surveyScore,
                    specScore == null ? "없음" : String.format(Locale.ROOT, "%.3f", specScore),
                    matchedSkills, fallbackReason);
        }
    }

    // ================= 메인 계산 =================

    public List<Recommendation> recommend(List<SurveyAnswer> answers,
                                          List<String> userSkills,
                                          List<JobCandidate> jobs) {
        Map<String, Double> surveyAvg = averageByCategory(answers);
        double specWeight = SPEC_WEIGHT_MAX
                * Math.min(1.0, (double) userSkills.size() / SKILLS_FOR_FULL_SPEC_WEIGHT);

        List<Recommendation> scored = new ArrayList<>();
        double specSum = 0;
        int specCount = 0;
        for (JobCandidate job : jobs) {
            Recommendation r = new Recommendation();
            r.jobId = job.jobId;
            r.jobName = job.jobName;
            r.category = job.category;

            Double avg = surveyAvg.get(job.category);
            r.surveyScore = avg == null ? 0.0 : (avg - 1.0) / 4.0;
            r.specScore = specScore(job, userSkills, r.matchedSkills);
            if (r.specScore != null) { specSum += r.specScore; specCount++; }
            r.fallbackReason = buildReason(job, avg, r.matchedSkills);
            scored.add(r);
        }

        // 요구 기술이 아직 수집 안 된 직무는 "이 사용자의 평균 스펙 점수"로 채운다.
        // 0으로 두면 부당하게 깎이고, 설문만으로 매기면 오히려 데이터 없는 직무가 유리해진다.
        double neutralSpec = specCount == 0 ? 0.0 : specSum / specCount;
        for (Recommendation r : scored) {
            double spec = r.specScore == null ? neutralSpec : r.specScore;
            r.totalScore = (1 - specWeight) * r.surveyScore + specWeight * spec;
        }

        scored.sort(Comparator.comparingDouble((Recommendation r) -> r.totalScore).reversed()
                .thenComparingLong(r -> r.jobId)); // 동점이면 id 순 (결과가 매번 같도록)
        return pick(scored);
    }

    /** 보유 기술 원문 + 프로젝트 tech_stack(쉼표 구분)을 합쳐 중복 없이 모은다. */
    public static List<String> collectUserSkills(List<String> rawSkills, List<String> projectTechStacks) {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> all = new ArrayList<>();
        if (rawSkills != null) all.addAll(rawSkills);
        if (projectTechStacks != null) {
            for (String stack : projectTechStacks) {
                if (stack == null) continue;
                for (String s : stack.split("[,/]")) all.add(s);
            }
        }
        for (String s : all) {
            if (s == null || s.trim().isEmpty()) continue;
            seen.putIfAbsent(normalize(s), s.trim());
        }
        return new ArrayList<>(seen.values());
    }

    // ================= 내부 =================

    private Map<String, Double> averageByCategory(List<SurveyAnswer> answers) {
        Map<String, int[]> acc = new HashMap<>(); // [합, 개수]
        for (SurveyAnswer a : answers) {
            int[] v = acc.computeIfAbsent(a.category, k -> new int[2]);
            v[0] += a.value;
            v[1]++;
        }
        Map<String, Double> avg = new HashMap<>();
        acc.forEach((k, v) -> avg.put(k, (double) v[0] / v[1]));
        return avg;
    }

    private Double specScore(JobCandidate job, List<String> userSkills, List<String> matchedOut) {
        if (job.skills.isEmpty()) return null;
        double got = 0, total = 0;
        for (RequiredSkill req : job.skills) {
            int weight = req.required ? WEIGHT_REQUIRED : WEIGHT_PREFERRED;
            total += weight;

            double best = 0;
            String bestUserSkill = null;
            for (String mine : userSkills) {
                double s = similarity.similarity(mine, req.skillName);
                if (s > best) { best = s; bestUserSkill = mine; }
            }
            got += weight * credit(best);
            if (best >= MATCH_SHOW_THRESHOLD && !matchedOut.contains(bestUserSkill)) {
                matchedOut.add(bestUserSkill);
            }
        }
        return got / total;
    }

    static double credit(double sim) {
        if (sim <= SIM_FLOOR) return 0;
        if (sim >= SIM_FULL) return 1;
        return (sim - SIM_FLOOR) / (SIM_FULL - SIM_FLOOR);
    }

    private List<Recommendation> pick(List<Recommendation> sorted) {
        List<Recommendation> out = new ArrayList<>();
        Map<String, Integer> perCategory = new HashMap<>();
        double top = sorted.isEmpty() ? 0 : sorted.get(0).totalScore;

        for (Recommendation r : sorted) {
            if (out.size() >= MAX_RESULTS) break;
            if (perCategory.getOrDefault(r.category, 0) >= MAX_PER_CATEGORY) continue;
            if (out.size() >= MIN_RESULTS && r.totalScore < top * EXTRA_RESULT_CUTOFF) break;
            perCategory.merge(r.category, 1, Integer::sum);
            r.rankOrder = out.size() + 1;
            out.add(r);
        }
        return out;
    }

    private String buildReason(JobCandidate job, Double avg, List<String> matched) {
        String ko = CATEGORY_KO.getOrDefault(job.category, job.category);
        StringBuilder sb = new StringBuilder();
        if (avg != null) {
            sb.append(String.format(Locale.ROOT, "설문에서 %s 관련 문항에 평균 %.1f점(5점 만점)으로 답했고", ko, avg));
        } else {
            sb.append("설문 응답과는 별개로");
        }
        if (!matched.isEmpty()) {
            List<String> shown = matched.size() > 3 ? matched.subList(0, 3) : matched;
            sb.append(", 보유 기술 ").append(String.join(", ", shown))
              .append("이(가) 이 직무 요구 기술과 겹칩니다.");
        } else if (job.skills.isEmpty()) {
            sb.append(", 이 직무의 요구 기술 데이터는 아직 수집 중이라 설문 기준으로 골랐습니다.");
        } else {
            sb.append(", 아직 겹치는 보유 기술이 적어 설문 응답을 중심으로 골랐습니다.");
        }
        return sb.toString();
    }

    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\s.\\-_]", "");
    }
}
