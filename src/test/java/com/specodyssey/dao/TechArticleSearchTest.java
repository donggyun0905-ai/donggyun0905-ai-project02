package com.specodyssey.dao;

import com.specodyssey.dto.TechArticleDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 스펙 아카이브 글 검색 — FULLTEXT(ngram) + 한 글자 LIKE 폴백 (2026-10-08).
 *
 * 기본 FULLTEXT 파서는 공백으로 단어를 자르기 때문에 한국어에서 "프링"으로는 "스프링 부트"를 못 찾는다.
 * ngram 파서를 써야 부분 일치가 된다 — 그게 실제로 동작하는지, 그리고 ngram 토큰(2글자)보다 짧은
 * 검색어가 LIKE로 떨어지는지를 실제 DB로 확인한다.
 */
class TechArticleSearchTest {

    private static final TechArticleDao dao = new TechArticleDao();
    private static final UserDao userDao = new UserDao();

    private static Long userId;
    private static String tag; // 다른 사람 글과 섞이지 않게 글마다 붙이는 표식

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_ftsearch_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
        tag = "검색표식" + (System.nanoTime() % 1_000_000);

        write("스프링 부트로 만든 " + tag, "의존성 주입과 자동 설정을 정리했습니다.");
        write("쿠버네티스 입문 " + tag, "파드와 디플로이먼트 개념을 정리했습니다.");
        write("파이썬 데이터 분석 " + tag, "판다스로 전처리하는 방법입니다.");
    }

    private static void write(String title, String content) throws Exception {
        TechArticleDto article = new TechArticleDto();
        article.setUserId(userId);
        article.setSourceType("ARCHIVE_TIP");
        article.setTitle(title);
        article.setContent(content);
        article.setStatus("PUBLISHED");
        article.setPublishedAt(LocalDateTime.now());
        try (Connection conn = DBUtil.getConnection()) {
            dao.insert(conn, article);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "TECH_ARTICLE", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    private List<String> titles(String keyword) throws Exception {
        return dao.searchArchive(keyword, TechArticleDao.Sort.LATEST, 0, 50).stream()
                .map(TechArticleDto::getTitle)
                .filter(t -> t.contains(tag))
                .collect(Collectors.toList());
    }

    @Test
    void 제목의_낱말로_찾는다() throws Exception {
        List<String> found = titles("스프링 " + tag);

        assertEquals(1, found.size(), "실제: " + found);
        assertTrue(found.get(0).startsWith("스프링 부트"));
    }

    @Test
    void 본문에만_있는_말로도_찾는다() throws Exception {
        List<String> found = titles("판다스 " + tag);

        assertEquals(1, found.size(), "본문도 검색 대상이다. 실제: " + found);
        assertTrue(found.get(0).startsWith("파이썬"));
    }

    @Test
    void 낱말_중간_글자로도_찾는다_ngram() throws Exception {
        // 기본 FULLTEXT 파서로는 "프링"이 토큰이 아니라 한 건도 안 걸린다 — ngram이라 걸린다
        List<String> found = titles("프링 " + tag);

        assertFalse(found.isEmpty(), "ngram 파서가 아니면 이 검색이 0건이 된다");
        assertTrue(found.stream().anyMatch(t -> t.startsWith("스프링 부트")), "실제: " + found);
    }

    @Test
    void 낱말이_여러_개면_모두_들어_있는_글만_찾는다() throws Exception {
        // 제목에 "쿠버네티스", 본문에 "파드" — 둘 다 든 글은 하나다
        assertEquals(1, titles("쿠버네티스 파드 " + tag).size(), "제목과 본문에 걸쳐 AND가 걸린다");
        // "파드"는 쿠버네티스 글에만, "판다스"는 파이썬 글에만 있다 — 함께 든 글은 없다
        assertTrue(titles("파드 판다스 " + tag).isEmpty(), "AND 조건이라 한 건도 안 나와야 한다");
    }

    @Test
    void 한_글자_검색도_결과를_준다_LIKE_폴백() throws Exception {
        // ngram 토큰이 2글자라 한 글자는 인덱스에 없다 — 그때는 LIKE로 떨어진다
        assertNull(TechArticleDao.toBooleanQuery("파"), "한 글자는 FULLTEXT 검색식을 만들 수 없다");

        List<TechArticleDto> found = dao.searchArchive("파", TechArticleDao.Sort.LATEST, 0, 100);

        assertTrue(found.stream().anyMatch(a -> a.getTitle().contains(tag) && a.getTitle().startsWith("파이썬")),
                "한 글자만 검색이 안 되면 더 이상하다");
    }

    @Test
    void 검색어와_건수가_맞는다() throws Exception {
        int total = dao.countSearchArchive("스프링 " + tag);

        assertEquals(1, total, "목록과 쪽 수 계산이 같은 조건을 봐야 한다");
        assertEquals(0, dao.countSearchArchive("없는낱말" + tag));
    }

    // ---------------------------------------------------------------- 검색식 만들기

    @Test
    void 모든_낱말이_들어_있어야_하도록_검색식을_만든다() {
        assertEquals("+\"스프링\" +\"부트\"", TechArticleDao.toBooleanQuery("스프링 부트"));
        assertEquals("+\"스프링\"", TechArticleDao.toBooleanQuery("  스프링  "));
    }

    @Test
    void 입력에_든_연산자는_지운다() {
        // 안 지우면 "C++"의 +가 연산자로 해석돼 구문 오류나 엉뚱한 결과가 된다
        assertEquals("+\"스프링\"", TechArticleDao.toBooleanQuery("*스프링*"));
        assertEquals("+\"자바\"", TechArticleDao.toBooleanQuery("\"자바\""));
        // #는 BOOLEAN MODE 연산자가 아니라 지우지 않는다 — "C#"은 실제 기술명이다
        assertEquals("+\"C#\"", TechArticleDao.toBooleanQuery("C#"));
        assertNull(TechArticleDao.toBooleanQuery("+++"), "연산자만 입력하면 검색식이 없다");
        // "C++"는 연산자를 지우면 한 글자 "C"가 되어 ngram 인덱스로는 못 찾는다 → LIKE 폴백이 받는다
        assertNull(TechArticleDao.toBooleanQuery("C++"));
    }

    @Test
    void 빈_검색어는_검색식이_없다() {
        assertNull(TechArticleDao.toBooleanQuery(null));
        assertNull(TechArticleDao.toBooleanQuery(""));
        assertNull(TechArticleDao.toBooleanQuery("   "));
    }

    @Test
    void 한_글자_낱말은_검색식에서_빠지고_나머지는_남는다() {
        assertEquals("+\"스프링\"", TechArticleDao.toBooleanQuery("스프링 가"),
                "한 글자 낱말 때문에 전체 검색이 0건이 되면 안 된다");
    }
}
