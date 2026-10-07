package com.specodyssey.service;

import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.ShareViewDto.TimelineItem;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 면접관 화면의 "보유 스펙" — 네 종류(자격증 · 어학 · 수상 · 경험)가 모두 보이는지 실제 DB로 확인한다.
 *
 * USER_SPECS.spec_type은 스키마가 자유 값이고 프로필 폼은 CERT · LANGUAGE · AWARD · EXPERIENCE 네 개를
 * 받는다. 전에는 이 네 가지가 "이력 타임라인"에 프로젝트와 섞여 시간순으로만 들어가서, 면접관이
 * "자격증이 몇 개인지"를 훑을 수 없었다(2026-10-07). 종류별 묶음을 더했으니 네 종류가 다 나오는지,
 * 순서가 고정인지, 날짜 표기가 종류에 맞는지를 여기서 고정한다.
 */
class ShareViewAllSpecTypesTest {

    private static final UserDao userDao = new UserDao();
    private static final UserSpecDao userSpecDao = new UserSpecDao();
    private static final ShareLinkService shareLinkService = new ShareLinkService();
    private final ShareViewService service = new ShareViewService();

    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_allspec_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setMajor("컴퓨터공학과");
        user.setGrade("4학년");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        // 프로필 폼이 받는 네 종류를 전부 넣는다. 일부러 뒤섞어 넣어 "넣은 순서"가 아니라
        // 정해진 순서(자격증 → 어학 → 수상 → 경험)로 묶이는지 확인한다.
        spec("AWARD", "교내 캡스톤 경진대회 대상", "컴퓨터공학부", null, LocalDate.of(2026, 4, 10), null);
        spec("EXPERIENCE", "백엔드 개발 인턴", "스펙오디세이", null,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 28));
        spec("CERT", "정보처리기사", "한국산업인력공단", null, LocalDate.of(2026, 6, 1), null);
        spec("LANGUAGE", "TOEIC", "ETS", "900", LocalDate.of(2026, 3, 15), null);
        spec("CERT", "SQLD", "한국데이터산업진흥원", null, LocalDate.of(2026, 7, 20), null);
        // 진행 중인 경력 — 종료일이 없으면 "진행 중"으로 보여야 한다
        spec("EXPERIENCE", "오픈소스 컨트리뷰션 활동", null, null, LocalDate.of(2026, 9, 1), null);
    }

    private static void spec(String type, String title, String issuer, String score,
                             LocalDate acquired, LocalDate end) throws Exception {
        UserSpecDto dto = new UserSpecDto();
        dto.setUserId(userId);
        dto.setSpecType(type);
        dto.setTitle(title);
        dto.setIssuer(issuer);
        dto.setScore(score);
        dto.setAcquiredDate(acquired);
        dto.setEndDate(end);
        userSpecDao.insert(dto);
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (ShareLinkDto link : new ShareLinkDao().findByUserId(userId)) {
                TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK_VIEW_LOG", "share_link_id", link.getId());
            }
            TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "USER_SPECS", "user_id", userId);
            // 공유 링크 열람은 알림을 남긴다 — NOTIFICATION이 USERS를 RESTRICT로 잡는다
            TestFixtures.hardDeleteByColumn(conn, "NOTIFICATION", "user_id", userId);
            // SpecScoreScheduler는 웹앱이 뜨는 순간 전체 사용자에게 스냅샷을 남긴다 — USERS 바로 앞에서 지운다
            TestFixtures.hardDeleteByColumn(conn, "SPEC_SCORE_HISTORY", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    private ShareViewDto openAsInterviewer() throws Exception {
        ShareLinkDto link = shareLinkService.createLink(userId, "보유 스펙 확인", 30, true, true, false);
        ShareViewDto view = service.loadView(link.getToken(), "127.0.0.1", null);
        assertNotNull(view, "유효한 링크인데 화면을 못 만들면 안 된다");
        return view;
    }

    @Test
    void 자격증_어학_수상_경험이_모두_정해진_순서로_보인다() throws Exception {
        Map<String, List<TimelineItem>> groups = openAsInterviewer().getSpecGroups();

        assertEquals(List.of("자격증", "어학", "수상", "경험"), List.copyOf(groups.keySet()),
                "넣은 순서가 아니라 화면 순서로 묶여야 한다");
        assertEquals(2, groups.get("자격증").size());
        assertEquals(1, groups.get("어학").size());
        assertEquals(1, groups.get("수상").size());
        assertEquals(2, groups.get("경험").size());
    }

    @Test
    void 상장과_경력도_제목과_발급처가_그대로_보인다() throws Exception {
        Map<String, List<TimelineItem>> groups = openAsInterviewer().getSpecGroups();

        TimelineItem award = groups.get("수상").get(0);
        assertEquals("교내 캡스톤 경진대회 대상", award.getTitle());
        assertEquals("컴퓨터공학부", award.getDetail(), "발급처(수여 기관)가 보여야 한다");
        assertEquals("2026-04-10", award.getDateText());

        List<String> careers = groups.get("경험").stream().map(TimelineItem::getTitle).collect(Collectors.toList());
        assertTrue(careers.contains("백엔드 개발 인턴"), "실제: " + careers);
        assertTrue(careers.contains("오픈소스 컨트리뷰션 활동"), "실제: " + careers);
    }

    @Test
    void 경력은_기간으로_자격증은_취득일로_보여준다() throws Exception {
        Map<String, List<TimelineItem>> groups = openAsInterviewer().getSpecGroups();

        TimelineItem intern = groups.get("경험").stream()
                .filter(i -> "백엔드 개발 인턴".equals(i.getTitle())).findFirst().orElseThrow();
        assertEquals("2026-01-05 ~ 2026-02-28", intern.getDateText(), "경력은 시작~종료 기간으로");

        TimelineItem ongoing = groups.get("경험").stream()
                .filter(i -> "오픈소스 컨트리뷰션 활동".equals(i.getTitle())).findFirst().orElseThrow();
        assertTrue(ongoing.getDateText().contains("진행 중"), "종료일이 없으면 진행 중: " + ongoing.getDateText());

        assertEquals("2026-06-01", groups.get("자격증").stream()
                .filter(i -> "정보처리기사".equals(i.getTitle())).findFirst().orElseThrow().getDateText());
    }

    @Test
    void 어학은_점수까지_보여준다() throws Exception {
        TimelineItem toeic = openAsInterviewer().getSpecGroups().get("어학").get(0);

        assertEquals("TOEIC", toeic.getTitle());
        assertTrue(toeic.getDetail().contains("900"), "점수가 빠지면 어학 스펙이 의미가 없다: " + toeic.getDetail());
    }

    @Test
    void 타임라인에도_같은_스펙이_그대로_있다() throws Exception {
        ShareViewDto view = openAsInterviewer();

        List<String> timelineTitles = view.getTimeline().stream()
                .map(TimelineItem::getTitle).collect(Collectors.toList());
        for (List<TimelineItem> group : view.getSpecGroups().values()) {
            for (TimelineItem spec : group) {
                assertTrue(timelineTitles.contains(spec.getTitle()),
                        "보유 스펙 카드는 타임라인과 같은 데이터를 다르게 보여주는 것이다: " + spec.getTitle());
            }
        }
        assertEquals(6, view.getTimeline().size(), "스펙 6건이 모두 타임라인에도 있어야 한다");
    }

    @Test
    void 기본_이력을_공개하지_않은_링크에는_보유_스펙도_없다() throws Exception {
        // scope_basic = false — 보유 스펙은 타임라인과 같은 데이터라 공개 범위도 같아야 한다
        ShareLinkDto skillsOnly = shareLinkService.createLink(userId, "기술만", 30, false, true, false);

        ShareViewDto view = service.loadView(skillsOnly.getToken(), "127.0.0.1", null);

        assertNotNull(view);
        assertTrue(view.getSpecGroups().isEmpty(), "기본 이력을 끈 링크에서 스펙이 새면 안 된다");
        assertTrue(view.getTimeline().isEmpty());
    }
}
