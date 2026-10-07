package com.specodyssey.service;

import com.specodyssey.dao.ProjectLinkDao;
import com.specodyssey.dao.ProjectTechNoteDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.dto.ProjectTechNoteDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.TransactionUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 프로필에서 프로젝트를 "전부" 입력하는 경로 (2026-10-07 사용자 요청).
 *
 * 프로필 칸은 회고·기술 활용 설명서·제출 서류를 받지 못해서, 프로필에서 적은 프로젝트는 면접관에게
 * 거의 빈 칸으로 보였다. 이제 로드맵 PROJECT 단계와 같은 ProjectSubmissionService를 쓴다.
 * 두 경로의 유일한 차이는 필수 서류(README·실행 화면 캡처)를 요구하는지다 — 그 차이를 여기서 고정한다.
 */
class ProfileProjectFullEntryTest {

    private static final UserDao userDao = new UserDao();
    private final ProjectSubmissionService service = new ProjectSubmissionService();
    private final UserProjectDao projectDao = new UserProjectDao();
    private final ProjectTechNoteDao noteDao = new ProjectTechNoteDao();
    private final ProjectLinkDao linkDao = new ProjectLinkDao();

    private static Long userId;
    private static long skillId;
    private static String skillName;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_proffull_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);

        skillName = "테스트기술_proffull_" + System.nanoTime();
        try (Connection conn = DBUtil.getConnection()) {
            skillId = TestFixtures.insertSkill(conn, skillName);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            // DAO의 findByUserId는 is_deleted = FALSE로 걸러서 논리 삭제된 행을 놓친다 — 컬럼으로 직접 찾는다
            for (Long projectId : TestFixtures.findIdsByColumn(conn, "USER_PROJECTS", "user_id", userId)) {
                TestFixtures.hardDeleteByColumn(conn, "PROJECT_TECH_NOTE", "project_id", projectId);
                TestFixtures.hardDeleteByColumn(conn, "PROJECT_LINK", "project_id", projectId);
                TestFixtures.hardDeleteByColumn(conn, "DOCUMENTS", "project_id", projectId);
            }
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "PROJECT_TECH_NOTE", "skill_id", skillId);
            TestFixtures.hardDelete(conn, "SKILL", skillId);
            // SpecScoreScheduler는 웹앱이 뜨는 순간 전체 사용자에게 스냅샷을 남긴다 — USERS 바로 앞에서 지운다
            TestFixtures.hardDeleteByColumn(conn, "SPEC_SCORE_HISTORY", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    private ProjectSubmission submission(String title) {
        UserProjectDto project = new UserProjectDto();
        project.setTitle(title);
        project.setDescription("설명");
        project.setTechStack(skillName);
        return new ProjectSubmission(project);
    }

    @Test
    void 프로필에서는_서류가_없어도_저장된다() throws Exception {
        ProjectSubmission submission = submission("서류 없는 프로젝트");
        submission.getProject().setRetrospective("프로필에서 적은 회고");

        assertDoesNotThrow(() -> service.validate(submission, null, false),
                "프로필은 예전에 한 프로젝트를 적어 두는 자리라 README·캡처를 요구하면 아무것도 못 적는다");

        Long projectId = TransactionUtil.runInTransaction(conn ->
                service.save(conn, userId, null, null, null, submission));

        UserProjectDto saved = projectDao.findById(projectId, userId);
        assertEquals("프로필에서 적은 회고", saved.getRetrospective());
    }

    @Test
    void 로드맵_단계는_여전히_필수_서류를_요구한다() {
        ProjectSubmission submission = submission("서류 없는 제출");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.validate(submission, null));
        assertTrue(e.getMessage().contains("README"), "실제 메시지: " + e.getMessage());
    }

    @Test
    void 회고와_기술_활용_설명서와_링크가_함께_저장된다() throws Exception {
        ProjectSubmission submission = submission("전부 채운 프로젝트");
        submission.getProject().setRetrospective("무엇을 배웠는지");
        submission.getProject().setMyRole("백엔드 API");
        submission.getProject().setTeamSize(3);
        submission.getTechNotes().add(new ProjectSubmission.TechNote(skillName, "쿼리를 직접 튜닝했다", true));
        submission.setLinks(List.of(new ProjectLinkDto("블로그", "https://blog.example.com/x")));

        service.validate(submission, null, false);
        Long projectId = TransactionUtil.runInTransaction(conn ->
                service.save(conn, userId, null, null, null, submission));

        UserProjectDto saved = projectDao.findById(projectId, userId);
        assertEquals("무엇을 배웠는지", saved.getRetrospective());
        assertEquals("백엔드 API", saved.getMyRole());
        assertEquals(3, saved.getTeamSize());

        List<ProjectTechNoteDto> notes = noteDao.findByProjectId(projectId);
        assertEquals(1, notes.size(), "기술 활용 설명서가 저장돼야 면접관 화면에 보인다");
        assertEquals("쿼리를 직접 튜닝했다", notes.get(0).getDescription());
        assertEquals(1, linkDao.findByProjectId(projectId).size());
        assertTrue(submission.getSkippedTechNotes().isEmpty(), "아는 기술이라 건너뛰지 않아야 한다");
    }

    @Test
    void 모르는_기술_이름은_건너뛰고_프로젝트는_저장된다() throws Exception {
        ProjectSubmission submission = submission("모르는 기술");
        submission.getTechNotes().add(new ProjectSubmission.TechNote("없는기술_" + System.nanoTime(), "설명", false));

        service.validate(submission, null, false);
        Long projectId = TransactionUtil.runInTransaction(conn ->
                service.save(conn, userId, null, null, null, submission));

        assertTrue(noteDao.findByProjectId(projectId).isEmpty());
        assertEquals(1, submission.getSkippedTechNotes().size(), "화면에서 무엇을 뺐는지 알려 줘야 한다");
    }
}
