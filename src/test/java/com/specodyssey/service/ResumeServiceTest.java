package com.specodyssey.service;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.ShareViewDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.FileStorageUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResumeService 통합테스트. 실제 DB와 업로드 폴더에 파일을 만들고 끝나면 지운다.
 */
class ResumeServiceTest {

    private static final UserDao userDao = new UserDao();
    private final ResumeService service = new ResumeService();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_resume_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterAll
    static void tearDown() throws Exception {
        // USERS와 DOCUMENTS가 서로를 참조하므로 지정을 먼저 풀고 서류, 회원 순으로 지운다
        userDao.updateResumeDocument(userId, null);
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "DOCUMENTS", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void 이력서를_올리고_바꾸고_삭제한다() throws Exception {
        assertNull(service.findResume(userId));

        DocumentDto first = savedFile("이력서.pdf");
        service.replaceResume(userId, first);

        DocumentDto found = service.findResume(userId);
        assertNotNull(found);
        assertEquals("이력서.pdf", found.getOriginalName());
        assertNull(found.getProjectId());
        assertTrue(Files.exists(Paths.get(first.getFilePath())));

        // 새 파일로 바꾸면 이전 이력서는 서류 목록과 디스크에서 사라진다
        DocumentDto second = savedFile("이력서_최종.docx");
        service.replaceResume(userId, second);

        assertEquals("이력서_최종.docx", service.findResume(userId).getOriginalName());
        assertEquals(1, new DocumentDao().findByUserId(userId).size());
        assertFalse(Files.exists(Paths.get(first.getFilePath())));
        assertTrue(Files.exists(Paths.get(second.getFilePath())));

        service.removeResume(userId);

        assertNull(service.findResume(userId));
        assertNull(userDao.findById(userId).getResumeDocumentId());
        assertTrue(new DocumentDao().findByUserId(userId).isEmpty());
        assertFalse(Files.exists(Paths.get(second.getFilePath())));
    }

    @Test
    void 이력서_공개를_고른_링크로만_면접관이_이력서를_받을_수_있다() throws Exception {
        ShareLinkService shareLinkService = new ShareLinkService();
        ShareViewService shareViewService = new ShareViewService();
        ShareLinkDto withResume = shareLinkService.createLink(userId, "이력서 공개", 30, true, false, false, true);
        ShareLinkDto withoutResume = shareLinkService.createLink(userId, "이력서 비공개", 30, true, false, false);
        try {
            // 공개를 골랐어도 올린 이력서가 없으면 받을 것이 없다
            assertNull(shareViewService.loadResume(withResume.getToken()));
            ShareViewDto emptyView = shareViewService.loadView(withResume.getToken(), "127.0.0.1", userId);
            assertTrue(emptyView.isScopeResume());
            assertNull(emptyView.getResumeFileName());

            service.replaceResume(userId, savedFile("이력서.pdf"));

            assertEquals("이력서.pdf", shareViewService.loadResume(withResume.getToken()).getOriginalName());
            assertEquals("이력서.pdf",
                    shareViewService.loadView(withResume.getToken(), "127.0.0.1", userId).getResumeFileName());

            // 이력서 공개를 고르지 않은 링크로는 파일도 파일 이름도 나가지 않는다
            assertNull(shareViewService.loadResume(withoutResume.getToken()));
            ShareViewDto hiddenView = shareViewService.loadView(withoutResume.getToken(), "127.0.0.1", userId);
            assertFalse(hiddenView.isScopeResume());
            assertNull(hiddenView.getResumeFileName());

            // 없는 토큰, 공유를 멈춘 링크
            assertNull(shareViewService.loadResume("no_such_token"));
            assertNull(shareViewService.loadResume(null));
            shareLinkService.setActive(userId, withResume.getId(), false);
            assertNull(shareViewService.loadResume(withResume.getToken()));
        } finally {
            service.removeResume(userId);
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            }
        }
    }

    @Test
    void 이력서가_없을_때_삭제해도_문제없다() throws Exception {
        service.removeResume(userId);

        assertNull(service.findResume(userId));
    }

    @Test
    void 이력서로_올릴_수_있는_파일_형식만_허용한다() {
        assertTrue(ResumeService.isAllowedFile("이력서.pdf"));
        assertTrue(ResumeService.isAllowedFile("resume.DOCX"));
        assertTrue(ResumeService.isAllowedFile("이력서.hwp"));
        assertFalse(ResumeService.isAllowedFile("virus.exe"));
        assertFalse(ResumeService.isAllowedFile("photo.png"));
        assertFalse(ResumeService.isAllowedFile("확장자없음"));
        assertFalse(ResumeService.isAllowedFile(null));
    }

    private DocumentDto savedFile(String originalName) throws Exception {
        FileStorageUtil.SavedFile saved = FileStorageUtil.save(
                new ByteArrayInputStream("테스트 이력서".getBytes(StandardCharsets.UTF_8)), originalName);
        DocumentDto document = new DocumentDto();
        document.setOriginalName(originalName);
        document.setStoredName(saved.getStoredName());
        document.setFilePath(saved.getFilePath());
        document.setFileSize(saved.getFileSize());
        document.setMimeType("application/octet-stream");
        document.setChecksum(saved.getChecksum());
        return document;
    }
}
