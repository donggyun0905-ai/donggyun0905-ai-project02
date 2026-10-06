package com.specodyssey.service.discovery;

import com.specodyssey.service.discovery.JobDiscoveryScorer.JobCandidate;
import com.specodyssey.service.discovery.JobDiscoveryScorer.OwnedSkill;
import com.specodyssey.service.discovery.JobDiscoveryScorer.Recommendation;
import com.specodyssey.service.discovery.JobDiscoveryScorer.RequiredSkill;
import com.specodyssey.service.discovery.JobDiscoveryScorer.SurveyAnswer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DB 없이 도는 순수 단위 테스트. 화면설계서 7번 예시 사용자(백엔드 성향 + Spring Boot·MySQL·Docker)로
 * 백엔드 → 데이터 엔지니어 → DevOps가 나오는지와 경계 규칙을 확인한다.
 */
class JobDiscoveryScorerTest {

    // 스킬 id (테스트 전용 가짜 값)
    static final long JAVA = 1, SPRING_BOOT = 2, MYSQL = 3, DOCKER = 4, PYTHON = 5, SQL = 6, AIRFLOW = 7,
            LINUX = 8, CICD = 9, K8S = 10, JS = 11, REACT = 12, NETWORK = 13, JIRA = 14;

    private final JobDiscoveryScorer scorer = new JobDiscoveryScorer();

    private static List<SurveyAnswer> backendLeaningAnswers() {
        return List.of(
                new SurveyAnswer("FRONTEND", 2), new SurveyAnswer("DATA", 4),
                new SurveyAnswer("BACKEND", 5), new SurveyAnswer("SECURITY", 2),
                new SurveyAnswer("PM", 2), new SurveyAnswer("DEVOPS", 4),
                new SurveyAnswer("FRONTEND", 2), new SurveyAnswer("BACKEND", 4),
                new SurveyAnswer("DATA", 4), new SurveyAnswer("SECURITY", 1),
                new SurveyAnswer("DEVOPS", 3), new SurveyAnswer("PM", 2));
    }

    private static List<OwnedSkill> backendSkills() {
        return List.of(new OwnedSkill(JAVA, "Java", 1.0), new OwnedSkill(SPRING_BOOT, "Spring Boot", 1.0),
                new OwnedSkill(MYSQL, "MySQL", 1.0), new OwnedSkill(DOCKER, "Docker", 1.0));
    }

    private static JobCandidate job(long id, String name, String cat, long... requiredThenPreferredNegative) {
        List<RequiredSkill> skills = new ArrayList<>();
        for (long s : requiredThenPreferredNegative) {
            skills.add(new RequiredSkill(Math.abs(s), s > 0)); // 음수 = PREFERRED
        }
        return new JobCandidate(id, name, cat, skills);
    }

    private static List<JobCandidate> jobs() {
        return List.of(
                job(1, "백엔드 개발자", "BACKEND", JAVA, SPRING_BOOT, MYSQL, -DOCKER),
                job(2, "서버 개발자", "BACKEND", JAVA, LINUX, NETWORK),
                job(3, "데이터 엔지니어", "DATA", SQL, PYTHON, AIRFLOW, -DOCKER, -MYSQL),
                job(4, "DevOps 엔지니어", "DEVOPS", LINUX, DOCKER, CICD, -K8S),
                job(5, "프론트엔드 개발자", "FRONTEND", JS, REACT),
                job(6, "보안 엔지니어", "SECURITY", LINUX, NETWORK, PYTHON),
                job(7, "IT 기획자", "PM", -SQL, -JIRA),
                job(8, "클라우드 엔지니어", "DEVOPS")); // 요구 기술 미수집
    }

    @Test
    void 화면설계_예시_사용자는_백엔드_데이터_데브옵스_순() {
        List<Recommendation> result = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs());

        assertEquals(List.of("백엔드 개발자", "데이터 엔지니어", "DevOps 엔지니어"),
                result.stream().map(r -> r.jobName).toList());
        assertEquals(1, result.get(0).rankOrder);
        assertTrue(result.get(0).reason.contains("Java"));
    }

    @Test
    void 같은_계열은_하나만_나온다() {
        List<Recommendation> result = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs());
        Set<String> categories = new HashSet<>();
        for (Recommendation r : result) {
            assertTrue(categories.add(r.category), "계열 중복: " + r.category);
        }
    }

    @Test
    void 후보는_3개에서_5개() {
        // 모든 문항 5점 → 6개 계열 동점. 최대 5개에서 잘려야 한다
        List<SurveyAnswer> allFive = new ArrayList<>();
        for (String c : List.of("BACKEND", "FRONTEND", "DATA", "DEVOPS", "SECURITY", "PM")) {
            allFive.add(new SurveyAnswer(c, 5));
        }
        int n = scorer.recommend(allFive, List.of(), jobs()).size();
        assertTrue(n >= 3 && n <= 5, "후보 수: " + n);

        int m = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs()).size();
        assertTrue(m >= 3 && m <= 5, "후보 수: " + m);
    }

    @Test
    void 스펙이_없으면_설문만으로_정한다() {
        List<Recommendation> result = scorer.recommend(backendLeaningAnswers(), List.of(), jobs());
        Recommendation top = result.get(0);
        assertEquals("BACKEND", top.category);
        assertEquals(top.surveyScore, top.totalScore, 1e-9);
    }

    @Test
    void 요구기술_데이터가_없는_직무가_부당하게_앞서지_않는다() {
        // DEVOPS 설문은 3.5점으로 중간인데, 데이터 없는 클라우드 엔지니어가 DevOps 엔지니어를 밀어내면 안 된다
        List<Recommendation> result = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs());
        assertTrue(result.stream().noneMatch(r -> r.jobName.equals("클라우드 엔지니어")));
    }

    @Test
    void 같은_입력이면_항상_같은_결과() {
        List<String> a = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs())
                .stream().map(r -> r.jobName).toList();
        List<String> b = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs())
                .stream().map(r -> r.jobName).toList();
        assertEquals(a, b);
    }

    @Test
    void 매칭점수_부분인정_경계() {
        assertEquals(0.0, JobDiscoveryScorer.credit(0.50), 1e-9);
        assertEquals(1.0, JobDiscoveryScorer.credit(0.85), 1e-9);
        assertEquals(0.5, JobDiscoveryScorer.credit(0.675), 1e-9);
    }

    @Test
    void 응답값_범위_밖이면_거부() {
        assertThrows(IllegalArgumentException.class, () -> new SurveyAnswer("DATA", 0));
        assertThrows(IllegalArgumentException.class, () -> new SurveyAnswer("DATA", 6));
    }

    // ================= FR-38 ② 전공 (2026-10-06 youngjun 추가) =================

    // 계열마다 같은 점수로 답한 설문 — 전공이 순위를 가르는지 보려고 설문 차이를 없앤다
    private static List<SurveyAnswer> flatAnswers() {
        List<SurveyAnswer> answers = new ArrayList<>();
        for (String c : List.of("BACKEND", "FRONTEND", "DATA", "DEVOPS", "SECURITY", "PM")) {
            answers.add(new SurveyAnswer(c, 3));
        }
        return answers;
    }

    private static java.util.Map<String, Double> dataLeaningMajor() {
        return java.util.Map.of("DATA", 1.0, "PM", 0.45, "BACKEND", 0.3, "DEVOPS", 0.1, "FRONTEND", 0.05, "SECURITY", 0.0);
    }

    @Test
    void 설문이_비슷하면_전공과_가까운_계열이_1순위로_올라온다() {
        List<Recommendation> recs = scorer.recommend(flatAnswers(), List.of(), jobs(), "통계학과", dataLeaningMajor());

        assertEquals("DATA", recs.get(0).category);
        assertEquals("통계학과", recs.get(0).major);
        assertEquals(1.0, recs.get(0).majorScore);
        assertTrue(recs.get(0).reason.contains("전공(통계학과)도 이 분야와 가깝습니다"), recs.get(0).reason);
        Recommendation notClose = recs.stream().filter(r -> !"DATA".equals(r.category)).findFirst().orElseThrow();
        assertFalse(notClose.reason.contains("전공("), "가깝지 않은 분야엔 전공 문장을 붙이지 않는다");
    }

    @Test
    void 전공이_없거나_점수가_비면_기존_계산과_결과가_같다() {
        List<Recommendation> before = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs());
        List<Recommendation> noMajor = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs(), null, dataLeaningMajor());
        List<Recommendation> noScores = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs(), "국어국문학과", java.util.Map.of());

        for (List<Recommendation> other : List.of(noMajor, noScores)) {
            assertEquals(before.size(), other.size());
            for (int i = 0; i < before.size(); i++) {
                assertEquals(before.get(i).jobId, other.get(i).jobId);
                assertEquals(before.get(i).totalScore, other.get(i).totalScore, 1e-12);
                assertNull(other.get(i).majorScore);
            }
        }
    }

    @Test
    void 전공은_설문을_뒤집을_만큼_크지_않다() {
        // 백엔드 성향이 뚜렷한 사용자는 데이터 쪽 전공이어도 1순위가 그대로다 (전공 비중 15%)
        List<Recommendation> recs = scorer.recommend(backendLeaningAnswers(), backendSkills(), jobs(), "통계학과", dataLeaningMajor());

        assertEquals("백엔드 개발자", recs.get(0).jobName);
    }
}
