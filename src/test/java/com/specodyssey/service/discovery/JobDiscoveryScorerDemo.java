package com.specodyssey.service.discovery;

import com.specodyssey.service.discovery.JobDiscoveryScorer.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DB 없이 점수 공식을 확인하는 데모. 화면설계서 7번의 예시 사용자(Spring Boot·MySQL·Docker, API 서버 프로젝트)로
 * 돌렸을 때 백엔드 → 데이터 엔지니어 → DevOps 순서가 나오는지 본다.
 */
public class JobDiscoveryScorerDemo {

    public static void main(String[] args) {
        // 12문항 응답 (survey_seed.sql 순서 그대로)
        List<SurveyAnswer> answers = Arrays.asList(
                new SurveyAnswer("FRONTEND", 2), new SurveyAnswer("DATA", 4),
                new SurveyAnswer("BACKEND", 5),  new SurveyAnswer("SECURITY", 2),
                new SurveyAnswer("PM", 2),       new SurveyAnswer("DEVOPS", 4),
                new SurveyAnswer("FRONTEND", 2), new SurveyAnswer("BACKEND", 4),
                new SurveyAnswer("DATA", 4),     new SurveyAnswer("SECURITY", 1),
                new SurveyAnswer("DEVOPS", 3),   new SurveyAnswer("PM", 2));

        List<String> skills = JobDiscoveryScorer.collectUserSkills(
                Arrays.asList("Java", "Spring Boot", "MySQL", "Git"),
                Arrays.asList("Spring Boot, MySQL, Docker"));

        List<JobCandidate> jobs = Arrays.asList(
                job(1, "백엔드 개발자", "BACKEND", "Java", "Spring", "MySQL", "REST API", "~Docker"),
                job(2, "서버 개발자", "BACKEND", "Java", "Linux", "Network", "~Kafka"),
                job(3, "데이터 엔지니어", "DATA", "SQL", "Python", "Airflow", "~Docker", "~Spark"),
                job(4, "데이터 분석가", "DATA", "SQL", "Python", "Excel", "~Tableau"),
                job(5, "DevOps 엔지니어", "DEVOPS", "Linux", "Docker", "CI/CD", "~Kubernetes", "~AWS"),
                job(6, "프론트엔드 개발자", "FRONTEND", "JavaScript", "React", "HTML/CSS", "~TypeScript"),
                job(7, "보안 엔지니어", "SECURITY", "Linux", "Network", "Python"),
                job(8, "서비스 기획자(PM)", "PM", "~SQL", "~Jira"),
                job(9, "클라우드 엔지니어", "DEVOPS")); // 요구 기술 미수집 직무

        System.out.println("보유 기술: " + skills);
        System.out.println("\n[1] 임베딩 전 (EXACT_MATCH)");
        new JobDiscoveryScorer(JobDiscoveryScorer.EXACT_MATCH)
                .recommend(answers, skills, jobs).forEach(System.out::println);

        System.out.println("\n[2] 임베딩 흉내 (의미가 가까운 쌍에 유사도 부여)");
        new JobDiscoveryScorer(fakeEmbedding())
                .recommend(answers, skills, jobs).forEach(System.out::println);

        System.out.println("\n[3] 스펙이 하나도 없는 신입 → 설문 100%");
        new JobDiscoveryScorer(JobDiscoveryScorer.EXACT_MATCH)
                .recommend(answers, Arrays.asList(), jobs).forEach(System.out::println);
    }

    /** "~" 로 시작하면 PREFERRED(우대), 아니면 REQUIRED(필수). */
    static JobCandidate job(long id, String name, String cat, String... skills) {
        RequiredSkill[] arr = new RequiredSkill[skills.length];
        for (int i = 0; i < skills.length; i++) {
            boolean pref = skills[i].startsWith("~");
            arr[i] = new RequiredSkill(pref ? skills[i].substring(1) : skills[i], !pref);
        }
        return new JobCandidate(id, name, cat, Arrays.asList(arr));
    }

    static SkillSimilarity fakeEmbedding() {
        Map<String, Double> m = new HashMap<>();
        m.put("springboot|spring", 0.93);
        m.put("mysql|sql", 0.88);
        m.put("springboot|restapi", 0.72);
        m.put("docker|cicd", 0.66);
        m.put("docker|kubernetes", 0.74);
        m.put("docker|linux", 0.58);
        m.put("java|javascript", 0.40); // 이름만 비슷하고 의미는 다름 → 인정 안 됨
        return (a, b) -> {
            String na = JobDiscoveryScorer.normalize(a), nb = JobDiscoveryScorer.normalize(b);
            if (na.equals(nb)) return 1.0;
            return m.getOrDefault(na + "|" + nb, 0.0);
        };
    }
}
