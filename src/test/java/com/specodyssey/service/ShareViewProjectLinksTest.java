package com.specodyssey.service;

import com.specodyssey.dao.ProjectLinkDao;
import com.specodyssey.dao.ShareLinkDao;
import com.specodyssey.dao.ShareLinkViewLogDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareViewDto;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 면접관 공유 화면의 프로젝트 항목에 저장소·배포·기타 링크가 실리는지, 위험한 주소는 빠지는지. */
class ShareViewProjectLinksTest {

    private static Long userId;
    private static Long projectId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_shareplink_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = new UserDao().insert(user);

        UserProjectDto project = new UserProjectDto();
        project.setUserId(userId);
        project.setTitle("링크 많은 프로젝트");
        project.setRepoUrl("https://github.com/a/b");
        project.setDeployUrl("https://app.example.com");
        projectId = new UserProjectDao().insert(project);
        TransactionUtil.runInTransaction(conn -> {
            // 서비스를 거치지 않고 위험한 주소를 직접 넣어, 화면에 내보낼 때 한 번 더 거르는지 본다
            new ProjectLinkDao().replaceForProject(conn, projectId, List.of(
                    new ProjectLinkDto("블로그 글", "https://blog.example.com/p"),
                    new ProjectLinkDto("나쁜 링크", "javascript:alert(document.cookie)"),
                    new ProjectLinkDto(null, "https://youtu.be/abc")));
            return null;
        });
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (ShareLinkDto link : new ShareLinkDao().findByUserId(userId)) {
                TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK_VIEW_LOG", "share_link_id", link.getId());
            }
            TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            TestFixtures.hardDeleteByColumn(conn, "PROJECT_LINK", "project_id", projectId);
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 타임라인의_프로젝트에_저장소_배포_기타_링크가_순서대로_실리고_위험한_주소는_빠진다() throws Exception {
        ShareLinkDto link = new ShareLinkService().createLink(userId, "링크 확인", 30, true, false, false);

        ShareViewDto view = new ShareViewService().loadView(link.getToken(), "127.0.0.1", userId);

        assertNotNull(view);
        ShareViewDto.TimelineItem item = view.getTimeline().stream()
                .filter(t -> "프로젝트".equals(t.getTypeLabel())).findFirst().orElseThrow();
        List<String> shown = item.getLinks().stream().map(l -> l.getLabel() + "=" + l.getUrl()).collect(Collectors.toList());
        assertEquals(List.of(
                "코드 저장소=https://github.com/a/b",
                "배포 주소=https://app.example.com",
                "블로그 글=https://blog.example.com/p",
                "youtu.be=https://youtu.be/abc"), shown);
        assertTrue(shown.stream().noneMatch(s -> s.contains("javascript")));
    }

    @Test
    void 링크가_하나도_없는_프로젝트는_빈_목록이다() {
        UserProjectDto bare = new UserProjectDto();
        bare.setTitle("링크 없음");
        assertTrue(ShareViewService.projectLinks(bare, null).isEmpty());
        bare.setRepoUrl("not a url");
        assertTrue(ShareViewService.projectLinks(bare, List.of()).isEmpty());
    }
}
