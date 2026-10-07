package com.specodyssey.service;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.ProjectDocumentItemDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.ProjectDocumentItemDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.util.TransactionUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentService(서류 보관함) 통합테스트. 실제 DB와 업로드 폴더에 파일을 만들고 끝나면 지운다.
 */
class DocumentServiceTest {

    private static final UserDao userDao = new UserDao();
    private static final UserProjectDao projectDao = new UserProjectDao();
    private final DocumentService service = new DocumentService();
    private final DocumentDao documentDao = new DocumentDao();
    private final ProjectDocumentItemDao itemDao = new ProjectDocumentItemDao();
    private static Long userId;
    private static Long otherUserId;
    private static Long projectId;
    private static Long otherProjectId;

    @BeforeAll
    static void setUp() throws Exception {
        userId = newUser("test_doc_user_");
        otherUserId = newUser("test_doc_other_");
        projectId = newProject(userId, "내 프로젝트");
        otherProjectId = newProject(otherUserId, "남의 프로젝트");
    }

    @AfterAll
    static void tearDown() throws Exception {
        for (Long id : new Long[] {userId, otherUserId}) {
            userDao.updateResumeDocument(id, null);
            userDao.updateCoverLetterDocument(id, null);
        }
        try (Connection conn = DBUtil.getConnection()) {
            for (Long pid : new Long[] {projectId, otherProjectId}) {
                TestFixtures.hardDeleteByColumn(conn, "PROJECT_DOCUMENT_ITEM", "project_id", pid);
            }
            for (Long id : new Long[] {userId, otherUserId}) {
                TestFixtures.hardDeleteByColumn(conn, "DOCUMENTS", "user_id", id);
                TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", id);
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    @Test
    void 올리고_연결을_바꾸고_삭제하면_파일_내용도_지워진다() throws Exception {
        DocumentDto file = savedFile("설계.pdf");
        Long id = service.upload(userId, file, projectId);

        DocumentService.DocumentView view = findView(userId, id);
        assertEquals("설계.pdf", view.getOriginalName());
        assertEquals("내 프로젝트", view.getProjectTitle());
        assertEquals("프로젝트 서류", view.getPurpose());
        assertTrue(documentDao.findById(id).isStoredInDb(), "내용은 DB에 저장된다");
        assertEquals("테스트 서류", new String(documentDao.readFileData(id), StandardCharsets.UTF_8));

        assertTrue(service.changeProject(userId, id, null));
        assertNull(findView(userId, id).getProjectTitle());
        assertEquals("", findView(userId, id).getPurpose());

        assertTrue(service.delete(userId, id));
        assertNull(documentDao.findById(id));
        assertNull(documentDao.readFileData(id), "삭제하면 내용도 비운다");
        assertTrue(service.listViews(userId).stream().noneMatch(v -> v.getId().equals(id)));
    }

    @Test
    void 이력서로_지정한_서류를_지우면_지정도_풀린다() throws Exception {
        DocumentDto file = savedFile("이력서.pdf");
        Long id = service.upload(userId, file, null);
        userDao.updateResumeDocument(userId, id);
        assertEquals("이력서", findView(userId, id).getPurpose());

        assertTrue(service.delete(userId, id));

        assertNull(userDao.findById(userId).getResumeDocumentId());
        assertNull(new ResumeService().findResume(userId));
    }

    @Test
    void 자소서로_지정한_서류를_지워도_이력서_지정은_그대로다() throws Exception {
        Long resumeId = service.upload(userId, savedFile("이력서.pdf"), null);
        Long coverId = service.upload(userId, savedFile("자소서.pdf"), null);
        userDao.updateResumeDocument(userId, resumeId);
        userDao.updateCoverLetterDocument(userId, coverId);

        assertTrue(service.delete(userId, coverId));

        assertNull(userDao.findById(userId).getCoverLetterDocumentId());
        assertEquals(resumeId, userDao.findById(userId).getResumeDocumentId());
        assertTrue(service.delete(userId, resumeId));
    }

    @Test
    void 프로젝트_문서_체크리스트가_가리키던_서류를_지우면_그_줄도_빠진다() throws Exception {
        Long docId = service.upload(userId, savedFile("README.md"), projectId);
        ProjectDocumentItemDto item = new ProjectDocumentItemDto();
        item.setProjectId(projectId);
        item.setDocType("README");
        item.setStatus("SUBMITTED");
        item.setDocumentId(docId);
        TransactionUtil.runInTransaction(conn -> {
            itemDao.upsert(conn, item);
            return null;
        });
        assertEquals(1, itemDao.findByProjectId(projectId).stream().filter(i -> "README".equals(i.getDocType())).count());

        assertTrue(service.delete(userId, docId));

        assertTrue(itemDao.findByProjectId(projectId).stream().noneMatch(i -> "README".equals(i.getDocType())));
        // 다시 제출하면 같은 줄이 되살아난다(UNIQUE(project_id, doc_type)와 부딪히지 않는다)
        Long again = service.upload(userId, savedFile("README2.md"), projectId);
        item.setDocumentId(again);
        TransactionUtil.runInTransaction(conn -> {
            itemDao.upsert(conn, item);
            return null;
        });
        List<ProjectDocumentItemDto> items = itemDao.findByProjectId(projectId);
        assertEquals(1, items.stream().filter(i -> "README".equals(i.getDocType())).count());
        assertEquals(again, items.stream().filter(i -> "README".equals(i.getDocType())).findFirst().get().getDocumentId());
        service.delete(userId, again);
    }

    @Test
    void 남의_서류는_삭제하거나_연결을_바꿀_수_없다() throws Exception {
        Long mine = service.upload(userId, savedFile("내것.txt"), null);
        try {
            assertFalse(service.delete(otherUserId, mine));
            assertNotNull(documentDao.findById(mine));
            assertFalse(service.changeProject(otherUserId, mine, null));
        } finally {
            service.delete(userId, mine);
        }
    }

    @Test
    void 내_프로젝트가_아니면_올리거나_연결할_수_없다() throws Exception {
        DocumentDto file = savedFile("연결시도.txt");
        assertThrows(IllegalArgumentException.class, () -> service.upload(userId, file, otherProjectId));
        FileStorageUtil.deleteQuietly(file.getFilePath());

        Long mine = service.upload(userId, savedFile("내것2.txt"), null);
        try {
            assertThrows(IllegalArgumentException.class, () -> service.changeProject(userId, mine, otherProjectId));
        } finally {
            service.delete(userId, mine);
        }
    }

    @Test
    void 올릴_수_있는_형식만_허용한다() {
        assertTrue(DocumentService.isAllowedFile("설계.PDF"));
        assertTrue(DocumentService.isAllowedFile("README.md"));
        assertTrue(DocumentService.isAllowedFile("화면.png"));
        assertFalse(DocumentService.isAllowedFile("virus.exe"));
        assertFalse(DocumentService.isAllowedFile("script.sh"));
        assertFalse(DocumentService.isAllowedFile("확장자없음"));
        assertFalse(DocumentService.isAllowedFile(null));
    }

    @Test
    void 파일_크기를_읽기_좋게_표시한다() {
        assertEquals("500 B", DocumentService.sizeLabel(500L));
        assertEquals("4 KB", DocumentService.sizeLabel(4096L));
        assertEquals("1.2 MB", DocumentService.sizeLabel(1_258_291L));
        assertEquals("", DocumentService.sizeLabel(null));
    }

    private DocumentService.DocumentView findView(Long uid, Long documentId) throws Exception {
        return service.listViews(uid).stream().filter(v -> v.getId().equals(documentId)).findFirst().orElseThrow();
    }

    private static Long newUser(String prefix) throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(prefix + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        return userDao.insert(user);
    }

    private static Long newProject(Long uid, String title) throws Exception {
        UserProjectDto project = new UserProjectDto();
        project.setUserId(uid);
        project.setTitle(title);
        project.setDescription("테스트");
        return projectDao.insert(project);
    }

    private DocumentDto savedFile(String originalName) throws Exception {
        FileStorageUtil.SavedFile saved = FileStorageUtil.save(
                new ByteArrayInputStream("테스트 서류".getBytes(StandardCharsets.UTF_8)), originalName);
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
