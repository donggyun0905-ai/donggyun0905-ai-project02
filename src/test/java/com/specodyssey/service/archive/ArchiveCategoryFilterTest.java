package com.specodyssey.service.archive;

import com.specodyssey.dao.TechArticleDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.TechArticleDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 스펙 아카이브를 기술 유형(SKILL.category)별로 거른다 (2026-10-14).
 *
 * 글이 무슨 기술에 대한 것인지는 TECH_ARTICLE.skill_id 에 있다. 전문가 단계 글은 그 단계의
 * 기술로 자동으로 정해지고, 자유 글은 쓸 때 고를 수 있다(안 골라도 된다 — "기타"로 모인다).
 */
class ArchiveCategoryFilterTest {

    private static final String CAT = "테스트유형_아카이브";

    private final UserDao userDao = new UserDao();
    private final TechArticleDao articleDao = new TechArticleDao();
    private final SpecArchiveService service = new SpecArchiveService();

    private Long userId;
    private Long skillId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_cat_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, "테스트기술_유형_" + System.nanoTime());
            // insertSkill 이 정하는 분야 대신 이 테스트 전용 유형으로 바꾼다 — 공유 DB 의 실제 글과 섞이지 않게
            try (var p = conn.prepareStatement("UPDATE SKILL SET category = ? WHERE id = ?")) {
                p.setString(1, CAT);
                p.setLong(2, skillId);
                p.executeUpdate();
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "TECH_ARTICLE", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
            TestFixtures.hardDelete(conn, "SKILL", skillId);
        }
    }

    @Test
    @DisplayName("기술을 고른 글은 그 유형으로만 걸러진다")
    void 유형으로_거른다() throws Exception {
        Long withSkill = service.create(userId, skillId, "기술 고른 글", "본문", Map.of());
        Long without = service.create(userId, null, "안 고른 글", "본문", Map.of());

        List<TechArticleDto> inCategory = service.list(TechArticleDao.Sort.LATEST, CAT, 1).posts();
        assertEquals(1, inCategory.size());
        assertEquals(withSkill, inCategory.get(0).getId());
        assertEquals(CAT, inCategory.get(0).getSkillCategory(), "목록이 유형을 함께 들고 온다");
        assertTrue(inCategory.get(0).getSkillName().startsWith("테스트기술_유형_"));

        assertEquals(1, service.list(TechArticleDao.Sort.LATEST, CAT, 1).totalCount(),
                "건수도 같은 조건으로 센다 — 안 그러면 쪽 번호가 어긋난다");

        // 기술을 안 고른 글은 "기타" 로 모인다. 공유 DB 의 다른 글도 거기 섞이므로 포함 여부만 본다.
        List<Long> noneIds = service.list(TechArticleDao.Sort.LATEST, TechArticleDao.CATEGORY_NONE, 1)
                .posts().stream().map(TechArticleDto::getId).toList();
        assertTrue(noneIds.contains(without));
        assertFalse(noneIds.contains(withSkill));
    }

    @Test
    @DisplayName("검색도 유형 안에서만 찾는다")
    void 검색에도_유형이_걸린다() throws Exception {
        service.create(userId, skillId, "트랜잭션 정리", "본문", Map.of());
        Long other = service.create(userId, null, "트랜잭션 정리", "본문", Map.of());

        List<Long> found = service.search("트랜잭션", TechArticleDao.Sort.LATEST, CAT, 1)
                .posts().stream().map(TechArticleDto::getId).toList();
        assertFalse(found.contains(other), "유형 밖의 글은 검색에도 안 나온다");
        assertEquals(1, service.search("트랜잭션", TechArticleDao.Sort.LATEST, CAT, 1).totalCount());
    }

    @Test
    @DisplayName("유형 목록은 글이 있는 유형만 준다")
    void 유형_목록은_글이_있는_것만() throws Exception {
        assertTrue(service.categories().stream().noneMatch(c -> CAT.equals(c.name())),
                "글이 없으면 목록에 안 나온다");

        service.create(userId, skillId, "글", "본문", Map.of());
        SpecArchiveService.CategoryCount mine = service.categories().stream()
                .filter(c -> CAT.equals(c.name())).findFirst().orElse(null);
        assertEquals(1, mine == null ? 0 : mine.count());
    }

    @Test
    @DisplayName("유형을 안 고르면 전체가 나온다 — 거르개가 글을 숨기지 않는다")
    void 유형이_없으면_전체다() throws Exception {
        Long a = service.create(userId, skillId, "하나", "본문", Map.of());
        Long b = service.create(userId, null, "둘", "본문", Map.of());

        List<Long> all = service.list(TechArticleDao.Sort.LATEST, null, 1)
                .posts().stream().map(TechArticleDto::getId).toList();
        assertTrue(all.contains(a) && all.contains(b));
    }

    @Test
    @DisplayName("없는 유형을 넣으면 빈 목록 — 터지지 않는다")
    void 없는_유형은_빈_목록() throws Exception {
        service.create(userId, skillId, "글", "본문", Map.of());
        assertEquals(0, service.list(TechArticleDao.Sort.LATEST, "그런유형없음", 1).totalCount());
        assertTrue(service.list(TechArticleDao.Sort.LATEST, "그런유형없음", 1).posts().isEmpty());
    }
}
