package com.specodyssey.service;

import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FuzzyNameMatcher 통합테스트 — 실제 SKILL 테이블에 대고 검증한다(TD-1 임베딩 전 중간 단계, 2026-09-30).
 */
class FuzzyNameMatcherTest {

    private final FuzzyNameMatcher matcher = new FuzzyNameMatcher();
    private final SkillDao skillDao = new SkillDao();
    private Long skillId;
    private String skillName;

    @BeforeEach
    void setUp() throws Exception {
        skillName = "리액트_" + System.nanoTime();
        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, skillName);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "SKILL", skillId);
        }
    }

    @Test
    void 정확히_같은_이름은_score_1점으로_매칭된다() throws Exception {
        SkillMatcher.MatchResult result = matcher.match(skillName);

        assertEquals(skillId, result.skillId());
        assertEquals(1.0, result.score());
    }

    @Test
    void 오타가_한_글자면_그래도_매칭되고_점수는_1보다_낮다() throws Exception {
        // 이름 끝 글자 하나만 다르게(오타 흉내) — 길이의 40% 문턱 안에 들어온다.
        String typo = skillName.substring(0, skillName.length() - 1) + "X";

        SkillMatcher.MatchResult result = matcher.match(typo);

        assertEquals(skillId, result.skillId());
        assertTrue(result.score() < 1.0 && result.score() > 0.5);
    }

    @Test
    void 완전히_다른_문자열은_매칭되지_않는다() throws Exception {
        SkillMatcher.MatchResult result = matcher.match("전혀 상관없는 임의의 긴 문자열 완전히 다름");

        assertNull(result.skillId());
        assertEquals(0.0, result.score());
    }

    @Test
    void 빈_문자열이나_null은_매칭되지_않는다() throws Exception {
        assertNull(matcher.match("").skillId());
        assertNull(matcher.match(null).skillId());
        assertNull(matcher.match("   ").skillId());
    }

    // SKILL_ALIAS 사전(2026-09-30, "이름 일치라도") — 정확 일치하는 별칭은 score 1.0으로 취급한다.
    @Test
    void 등록된_별칭과_정확히_일치하면_score_1점으로_매칭된다() throws Exception {
        String aliasName = "리엑트별칭_" + System.nanoTime();
        Long aliasId;
        try (Connection conn = DBUtil.getConnection()) {
            aliasId = TestFixtures.insertSkillAlias(conn, skillId, aliasName);
        }
        try {
            SkillMatcher.MatchResult result = matcher.match(aliasName);

            assertEquals(skillId, result.skillId());
            assertEquals(1.0, result.score());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SKILL_ALIAS", aliasId);
            }
        }
    }

    @Test
    void 별칭에_오타가_있어도_퍼지_매칭으로_잡힌다() throws Exception {
        String aliasName = "리엑트별칭투_" + System.nanoTime();
        Long aliasId;
        try (Connection conn = DBUtil.getConnection()) {
            aliasId = TestFixtures.insertSkillAlias(conn, skillId, aliasName);
        }
        try {
            String typoAlias = aliasName.substring(0, aliasName.length() - 1) + "Z";

            SkillMatcher.MatchResult result = matcher.match(typoAlias);

            assertEquals(skillId, result.skillId());
            assertTrue(result.score() < 1.0 && result.score() > 0.5);
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "SKILL_ALIAS", aliasId);
            }
        }
    }

    // 실제 시드 데이터(sql/10_seed_skill_alias.sql, sql/11_seed_skill_alias_english.sql) 검증 —
    // 한글 표기뿐 아니라 영어 줄임말/접두사 생략 표현도 정확한 표준 스킬로 잡히는지 예시를
    // 잔뜩 모아서 확인한다(2026-09-30, "영어도 올바르게 추출되게").
    @ParameterizedTest
    @CsvSource({
            "파이썬, Python",
            "파이선, Python",
            "자바스크립트, JavaScript",
            "JS, JavaScript",
            "TS, TypeScript",
            "스프링부트, Spring Boot",
            "쿠버네티스, Kubernetes",
            "K8s, Kubernetes",
            "몽고, MongoDB",
            "리액트, React",
            "깃허브, GitHub",
            "Postgres, PostgreSQL",
            "Mongo, MongoDB",
            "Node, Node.js",
            "Redshift, Amazon Redshift",
            "BigQuery, Google BigQuery",
            "Google Cloud, Google Cloud Platform",
            "Azure, Microsoft Azure",
            "Oracle, Oracle Database",
            "MSSQL, MS SQL Server",
            "RoR, Ruby on Rails",
            "ASP.NET, ASP.NET Core",
            "Elastic, Elasticsearch",
            "Jupyter, Jupyter Notebook",
            "Hugging Face, Hugging Face Transformers",
            "Spark, Apache Spark",
            "Kafka, Apache Kafka",
            "Airflow, Apache Airflow",
            "Flink, Apache Flink",
            "NiFi, Apache NiFi",
            "JMeter, Apache JMeter",
            "Microservices, Microservices Architecture",
            "Agile, Agile/Scrum",
            "Scrum, Agile/Scrum",
            "CICD, CI/CD",
    })
    void 실제_시드된_별칭_예시들이_올바른_표준_스킬로_매칭된다(String alias, String expectedCanonicalName) throws Exception {
        SkillDto expected = skillDao.findByName(expectedCanonicalName);
        assertEquals(expected.getId(), matcher.match(alias).skillId(),
                () -> "'" + alias + "' 은(는) '" + expectedCanonicalName + "'로 매칭돼야 한다");
    }

    // ================= 2026-10-02 정확도 개선 =================

    // 단어 단위·앞부분 일치·오타(순서 바뀜 포함)로 맞게 연결돼야 하는 입력
    @ParameterizedTest
    @CsvSource({
            "자바 백엔드, Java",
            "자바 웹개발, Java",
            "Java Spring, Java",
            "Vanilla JS, JavaScript",
            "JavaScript ES6, JavaScript",
            "Java 17, Java",
            "Java8, Java",
            "JavaEE, Java",
            "Reactjs, React",
            "React.js, React",
            "Pyhton, Python",
            "Djnago, Django",
            "Typescirpt, TypeScript",
            "Kubernets, Kubernetes",
            "Javscript, JavaScript",
            "Dockr, Docker",
            "Jenkin, Jenkins",
            // 기술 하나를 띄어 쓴 입력 — 단어로 쪼개지 않고 하나의 이름으로 본다
            "Java Script, JavaScript",
            "Next JS, Next.js",
            "Vue JS, Vue.js",
            "Node JS, Node.js",
            "Spring boot 3, Spring Boot",
            "Node.js 18, Node.js",
            // 이름의 앞부분만 쓴 입력 (그 앞부분으로 시작하는 스킬이 하나뿐일 때)
            "Tailwind, Tailwind CSS",
            "GitHub Action, GitHub Actions",
    })
    void 표기가_달라도_맞는_스킬로_연결된다(String input, String expectedCanonicalName) throws Exception {
        SkillDto expected = skillDao.findByName(expectedCanonicalName);
        assertEquals(expected.getId(), matcher.match(input).skillId(),
                () -> "'" + input + "' 은(는) '" + expectedCanonicalName + "'로 매칭돼야 한다");
    }

    // 예전 규칙(40% 편집거리)에서 다른 기술로 잘못 가던 입력 — 잘못 연결하느니 못 찾는 게 낫다
    @ParameterizedTest
    @CsvSource({
            "JSP, JavaScript",
            "Jsp, JavaScript",
            "ES6, CSS3",
            "React.js, Next.js",
            "Java Spring, JavaScript",
            "자바 백엔드, JavaScript",
            "Java Script, Java",
            "Next JS, JavaScript",
            "Node JS, JavaScript",
            "Spring boot 3, Spring",
            "REST, Rust",
            "SCSS, CSS3",
    })
    void 다른_기술로_잘못_연결되지_않는다(String input, String wrongCanonicalName) throws Exception {
        SkillDto wrong = skillDao.findByName(wrongCanonicalName);
        assertTrue(matcher.matchAll(input).stream().noneMatch(r -> r.skillId().equals(wrong.getId())),
                () -> "'" + input + "' 이(가) '" + wrongCanonicalName + "'로 연결되면 안 된다");
    }

    @Test
    void 원문에_기술이_여러_개면_matchAll이_입력_순서대로_모두_돌려준다() throws Exception {
        Long java = skillDao.findByName("Java").getId();
        Long spring = skillDao.findByName("Spring").getId();

        var all = matcher.matchAll("Java Spring");

        assertEquals(2, all.size());
        assertEquals(java, all.get(0).skillId());
        assertEquals(spring, all.get(1).skillId());
        assertEquals(java, matcher.match("Java Spring").skillId(), "match()는 앞 단어 하나");
    }

    @Test
    void 짧은_입력은_오타를_허용하지_않는다() throws Exception {
        assertNull(matcher.match("ES6").skillId());
        assertNull(matcher.match("JSP").skillId());
    }

    @Test
    void 쉼표로_나눈_목록은_조각마다_한_이름으로_본다() throws Exception {
        Long nodeJs = skillDao.findByName("Node.js").getId();
        Long springBoot = skillDao.findByName("Spring Boot").getId();

        var all = matcher.matchAll("Node JS, Spring boot 3");

        assertEquals(2, all.size(), () -> "결과: " + all);
        assertEquals(nodeJs, all.get(0).skillId());
        assertEquals(springBoot, all.get(1).skillId());
    }

    @Test
    void 앞부분으로_시작하는_스킬이_여럿이면_고르지_않는다() throws Exception {
        // "Apache"로 시작하는 스킬이 여러 개(Kafka·Spark·Airflow …)라 하나로 정할 수 없다
        assertNull(matcher.match("Apache").skillId());
    }
}
