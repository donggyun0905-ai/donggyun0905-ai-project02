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
import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResumeService 통합테스트(이력서·자소서). 실제 DB와 업로드 폴더에 파일을 만들고 끝나면 지운다.
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
        userDao.updateCoverLetterDocument(userId, null);
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
        Long firstId = found.getId();
        assertTrue(hasData(firstId));

        // 새 파일로 바꾸면 이전 이력서는 서류 목록에서 사라지고 내용도 비워진다
        DocumentDto second = savedFile("이력서_최종.docx");
        service.replaceResume(userId, second);

        assertEquals("이력서_최종.docx", service.findResume(userId).getOriginalName());
        Long secondId = service.findResume(userId).getId();
        assertEquals(1, new DocumentDao().findByUserId(userId).size());
        assertFalse(hasData(firstId));
        assertTrue(hasData(secondId));

        service.removeResume(userId);

        assertNull(service.findResume(userId));
        assertNull(userDao.findById(userId).getResumeDocumentId());
        assertTrue(new DocumentDao().findByUserId(userId).isEmpty());
        assertFalse(hasData(secondId));
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
    void 자소서를_올리고_바꾸고_삭제하며_이력서와는_서로_영향이_없다() throws Exception {
        assertNull(service.findCoverLetter(userId));
        service.replaceResume(userId, savedFile("이력서.pdf"));

        DocumentDto first = savedFile("자소서.pdf");
        service.replaceCoverLetter(userId, first);

        assertEquals("자소서.pdf", service.findCoverLetter(userId).getOriginalName());
        assertNull(service.findCoverLetter(userId).getProjectId());
        assertEquals("이력서.pdf", service.findResume(userId).getOriginalName());

        // 새 파일로 바꾸면 이전 자소서만 서류 목록에서 사라지고(내용도 비움), 이력서는 그대로다
        Long firstId = service.findCoverLetter(userId).getId();
        DocumentDto second = savedFile("자소서_최종.hwp");
        service.replaceCoverLetter(userId, second);

        assertEquals("자소서_최종.hwp", service.findCoverLetter(userId).getOriginalName());
        Long secondId = service.findCoverLetter(userId).getId();
        assertEquals(2, new DocumentDao().findByUserId(userId).size());
        assertFalse(hasData(firstId));
        assertTrue(hasData(secondId));
        assertEquals("이력서.pdf", service.findResume(userId).getOriginalName());

        // 자소서를 지워도 이력서는 남는다
        service.removeCoverLetter(userId);

        assertNull(service.findCoverLetter(userId));
        assertNull(userDao.findById(userId).getCoverLetterDocumentId());
        assertFalse(hasData(secondId));
        assertEquals("이력서.pdf", service.findResume(userId).getOriginalName());

        service.removeResume(userId);
        assertTrue(new DocumentDao().findByUserId(userId).isEmpty());
    }

    @Test
    void 자소서_공개를_고른_링크로만_면접관이_자소서를_받을_수_있다() throws Exception {
        ShareLinkService shareLinkService = new ShareLinkService();
        ShareViewService shareViewService = new ShareViewService();
        // 이력서는 공개하지 않고 자소서만 공개한 링크 / 자소서를 공개하지 않은 링크
        ShareLinkDto withCoverLetter = shareLinkService.createLink(userId, "자소서 공개", 30, true, false, false, false, true);
        ShareLinkDto withoutCoverLetter = shareLinkService.createLink(userId, "자소서 비공개", 30, true, false, false, true, false);
        try {
            // 공개를 골랐어도 올린 자소서가 없으면 받을 것이 없다
            assertNull(shareViewService.loadCoverLetter(withCoverLetter.getToken()));
            ShareViewDto emptyView = shareViewService.loadView(withCoverLetter.getToken(), "127.0.0.1", userId);
            assertTrue(emptyView.isScopeCoverLetter());
            assertNull(emptyView.getCoverLetterFileName());

            service.replaceCoverLetter(userId, savedFile("자소서.pdf"));
            service.replaceResume(userId, savedFile("이력서.pdf"));

            assertEquals("자소서.pdf", shareViewService.loadCoverLetter(withCoverLetter.getToken()).getOriginalName());
            ShareViewDto view = shareViewService.loadView(withCoverLetter.getToken(), "127.0.0.1", userId);
            assertEquals("자소서.pdf", view.getCoverLetterFileName());
            // 자소서만 골랐으니 이력서는 안 나간다 — 두 종류의 공개 범위는 따로다
            assertFalse(view.isScopeResume());
            assertNull(shareViewService.loadResume(withCoverLetter.getToken()));

            // 자소서 공개를 고르지 않은 링크로는 파일도 파일 이름도 나가지 않는다 (이력서는 나간다)
            assertNull(shareViewService.loadCoverLetter(withoutCoverLetter.getToken()));
            ShareViewDto hiddenView = shareViewService.loadView(withoutCoverLetter.getToken(), "127.0.0.1", userId);
            assertFalse(hiddenView.isScopeCoverLetter());
            assertNull(hiddenView.getCoverLetterFileName());
            assertEquals("이력서.pdf", shareViewService.loadResume(withoutCoverLetter.getToken()).getOriginalName());

            // 없는 토큰, 공유를 멈춘 링크
            assertNull(shareViewService.loadCoverLetter("no_such_token"));
            assertNull(shareViewService.loadCoverLetter(null));
            shareLinkService.setActive(userId, withCoverLetter.getId(), false);
            assertNull(shareViewService.loadCoverLetter(withCoverLetter.getToken()));
        } finally {
            service.removeCoverLetter(userId);
            service.removeResume(userId);
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            }
        }
    }

    @Test
    void 공개_범위가_자소서_하나뿐인_링크도_만들_수_있고_범위가_하나도_없으면_거절한다() throws Exception {
        ShareLinkService shareLinkService = new ShareLinkService();
        ShareLinkDto onlyCoverLetter = shareLinkService.createLink(userId, null, 7, false, false, false, false, true);
        try {
            assertTrue(onlyCoverLetter.isScopeCoverLetter());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDeleteByColumn(conn, "SHARE_LINK", "user_id", userId);
            }
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> shareLinkService.createLink(userId, null, 7, false, false, false, false, false));
    }

    @Test
    void 이력서가_없을_때_삭제해도_문제없다() throws Exception {
        service.removeResume(userId);
        service.removeCoverLetter(userId);

        assertNull(service.findResume(userId));
        assertNull(service.findCoverLetter(userId));
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

    private static boolean hasData(Long documentId) throws Exception {
        return new DocumentDao().readFileData(documentId) != null;
    }

    private DocumentDto savedFile(String originalName) throws Exception {
        FileStorageUtil.SavedFile saved = FileStorageUtil.save(
                new ByteArrayInputStream("테스트 이력서".getBytes(StandardCharsets.UTF_8)), originalName);
        DocumentDto document = new DocumentDto();
        document.setOriginalName(originalName);
        document.setStoredName(saved.getStoredName());
        document.setFilePath(saved.getFilePath());
        document.setFileData(saved.getData());
        document.setFileSize(saved.getFileSize());
        document.setMimeType("application/octet-stream");
        document.setChecksum(saved.getChecksum());
        return document;
    }
}
