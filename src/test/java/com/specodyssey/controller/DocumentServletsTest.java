package com.specodyssey.controller;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.service.DocumentService;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 서류 보관함 화면(올리기·삭제·연결)과 내려받기 서블릿 — 요청 → 세션 메시지·이동·응답 헤더까지. */
class DocumentServletsTest {

    private static final UserDao userDao = new UserDao();
    private static final DocumentDao documentDao = new DocumentDao();
    private static final DocumentService documentService = new DocumentService();
    private static UserDto owner;
    private static UserDto other;
    private static Long projectId;

    private final DocumentsManageServlet manage = new DocumentsManageServlet();
    private final DocumentDownloadServlet download = new DocumentDownloadServlet();

    @BeforeAll
    static void setUp() throws Exception {
        owner = newUser("test_docsv_owner_");
        other = newUser("test_docsv_other_");
        UserProjectDto project = new UserProjectDto();
        project.setUserId(owner.getId());
        project.setTitle("내 프로젝트");
        projectId = new UserProjectDao().insert(project);
    }

    @AfterAll
    static void tearDown() throws Exception {
        for (DocumentDto d : documentDao.findByUserId(owner.getId())) {
            FileStorageUtil.deleteQuietly(d.getFilePath());
        }
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "DOCUMENTS", "user_id", owner.getId());
            TestFixtures.hardDeleteByColumn(conn, "USER_PROJECTS", "user_id", owner.getId());
            TestFixtures.hardDelete(conn, "USERS", owner.getId());
            TestFixtures.hardDelete(conn, "USERS", other.getId());
        }
    }

    private static UserDto newUser(String prefix) throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(prefix + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        user.setId(userDao.insert(user));
        return user;
    }

    private FakeWeb.Request postAs(UserDto user, String action) {
        FakeWeb.Request req = FakeWeb.request().post("/documents").param("action", action).loggedIn(user);
        return req;
    }

    private Object message(FakeWeb.Request req, String key) {
        return req.session.attributes.get(key);
    }

    @Test
    void 허용된_파일을_올리면_저장되고_MIME은_확장자로_정해진다() throws Exception {
        FakeWeb.Request req = postAs(owner, "upload").param("projectId", String.valueOf(projectId))
                .file("file", "메모.txt", "안녕".getBytes(StandardCharsets.UTF_8));
        FakeWeb.Response resp = FakeWeb.response();

        manage.doPost(req.http(), resp.http());

        assertEquals("/documents", resp.redirect);
        assertEquals("서류를 올렸습니다.", message(req, "documentsMessage"));
        DocumentDto saved = documentDao.findByUserId(owner.getId()).stream()
                .filter(d -> "메모.txt".equals(d.getOriginalName())).findFirst().orElseThrow();
        assertEquals("text/plain; charset=UTF-8", saved.getMimeType());
        assertEquals(projectId, saved.getProjectId());
        assertTrue(Files.exists(Paths.get(saved.getFilePath())));
    }

    @Test
    void 허용되지_않은_형식과_빈_요청은_올라가지_않고_이유를_알려준다() throws Exception {
        long before = documentDao.findByUserId(owner.getId()).size();

        FakeWeb.Request html = postAs(owner, "upload").file("file", "evil.html", "<script>1</script>".getBytes());
        manage.doPost(html.http(), FakeWeb.response().http());
        assertTrue(((String) message(html, "documentsError")).contains("올릴 수 없는 파일 형식"));

        FakeWeb.Request none = postAs(owner, "upload");
        manage.doPost(none.http(), FakeWeb.response().http());
        assertEquals("올릴 파일을 선택해주세요.", message(none, "documentsError"));

        assertEquals(before, documentDao.findByUserId(owner.getId()).size());
    }

    @Test
    void 남의_프로젝트에는_올릴_수_없고_파일도_남지_않는다() throws Exception {
        long before = documentDao.findByUserId(owner.getId()).size();
        FakeWeb.Request req = postAs(other, "upload").param("projectId", String.valueOf(projectId))
                .file("file", "남의것.txt", "x".getBytes());

        manage.doPost(req.http(), FakeWeb.response().http());

        assertNotNull(message(req, "documentsError"));
        assertEquals(before, documentDao.findByUserId(owner.getId()).size());
        assertTrue(documentDao.findByUserId(other.getId()).isEmpty());
    }

    private DocumentDto saveDocument(UserDto user, String name, String storedMime) throws Exception {
        FileStorageUtil.SavedFile saved = FileStorageUtil.save(
                new ByteArrayInputStream("본문".getBytes(StandardCharsets.UTF_8)), name);
        DocumentDto d = new DocumentDto();
        d.setOriginalName(name);
        d.setStoredName(saved.getStoredName());
        d.setFilePath(saved.getFilePath());
        d.setFileSize(saved.getFileSize());
        d.setMimeType(storedMime);
        d.setChecksum(saved.getChecksum());
        d.setId(documentService.upload(user.getId(), d, null));
        return d;
    }

    @Test
    void 삭제는_본인_서류만_되고_디스크_파일도_지워진다() throws Exception {
        DocumentDto mine = saveDocument(owner, "지울것.txt", "text/plain");

        FakeWeb.Request byOther = postAs(other, "delete").param("documentId", String.valueOf(mine.getId()));
        manage.doPost(byOther.http(), FakeWeb.response().http());
        assertEquals("삭제할 서류를 찾을 수 없습니다.", message(byOther, "documentsError"));
        assertNotNull(documentDao.findById(mine.getId()));

        FakeWeb.Request byOwner = postAs(owner, "delete").param("documentId", String.valueOf(mine.getId()));
        manage.doPost(byOwner.http(), FakeWeb.response().http());
        assertEquals("서류를 삭제했습니다.", message(byOwner, "documentsMessage"));
        assertNull(documentDao.findById(mine.getId()));
        assertFalse(Files.exists(Paths.get(mine.getFilePath())));

        FakeWeb.Request bad = postAs(owner, "delete").param("documentId", "abc");
        manage.doPost(bad.http(), FakeWeb.response().http());
        assertEquals("잘못된 요청입니다.", message(bad, "documentsError"));
    }

    @Test
    void 목록_화면은_서류와_프로젝트를_싣고_세션_메시지는_한_번만_보여준다() throws Exception {
        saveDocument(owner, "목록용.txt", "text/plain");
        FakeWeb.Request req = FakeWeb.request().loggedIn(owner);
        req.servletPath = "/documents";
        req.session.attributes.put("documentsMessage", "방금 한 일");
        FakeWeb.Response resp = FakeWeb.response();

        manage.doGet(req.http(), resp.http());

        assertEquals(List.of("/WEB-INF/views/documents-manage.jsp"), req.forwards);
        assertEquals("방금 한 일", req.attributes.get("documentsMessage"));
        assertNull(req.session.attributes.get("documentsMessage"), "한 번 보여주면 지워진다");
        assertNotNull(req.attributes.get("documents"));
        assertNotNull(req.attributes.get("projects"));
    }

    @Test
    void 내려받기는_본인만_되고_MIME은_저장값이_아니라_확장자로_정한다() throws Exception {
        // 예전에 브라우저가 text/html이라고 보내 DB에 그대로 남은 서류를 가정한다
        DocumentDto doc = saveDocument(owner, "안내.md", "text/html");

        FakeWeb.Request req = FakeWeb.request().loggedIn(owner);
        req.servletPath = "/documents";
        req.pathInfo = "/" + doc.getId();
        FakeWeb.Response resp = FakeWeb.response();
        download.doGet(req.http(), resp.http());

        assertEquals("text/plain; charset=UTF-8", resp.contentType);
        assertEquals("본문", resp.body.toString(StandardCharsets.UTF_8));
        assertTrue(resp.headers.get("Content-Disposition").startsWith("attachment;"));

        FakeWeb.Request stranger = FakeWeb.request().loggedIn(other);
        stranger.servletPath = "/documents";
        stranger.pathInfo = "/" + doc.getId();
        FakeWeb.Response denied = FakeWeb.response();
        download.doGet(stranger.http(), denied.http());
        assertEquals(404, denied.errorStatus);
        assertEquals(0, denied.body.size());
    }

    @Test
    void 내려받기_주소가_이상하면_400이다() throws Exception {
        for (String pathInfo : new String[] {null, "/", "/abc"}) {
            FakeWeb.Request req = FakeWeb.request().loggedIn(owner);
            req.pathInfo = pathInfo;
            FakeWeb.Response resp = FakeWeb.response();
            download.doGet(req.http(), resp.http());
            assertEquals(400, resp.errorStatus, String.valueOf(pathInfo));
        }
    }

    @Test
    void 디스크에_파일이_없는_서류를_받으면_500이_아니라_404다() throws Exception {
        // 파일이 옮겨졌거나 지워졌거나, [TEST] 통과 버튼으로 만든(파일 없는) 서류를 가정한다
        DocumentDto doc = saveDocument(owner, "사라질것.txt", "text/plain");
        FileStorageUtil.deleteQuietly(doc.getFilePath());

        for (Long id : new Long[] {doc.getId()}) {
            FakeWeb.Request req = FakeWeb.request().loggedIn(owner);
            req.servletPath = "/documents";
            req.pathInfo = "/" + id;
            FakeWeb.Response resp = FakeWeb.response();
            download.doGet(req.http(), resp.http());
            assertEquals(404, resp.errorStatus);
            assertEquals(0, resp.body.size());
        }
        // 경로가 비어 있는 서류(파일 없이 만든 것)도 마찬가지
        DocumentDto empty = saveDocument(owner, "빈경로.txt", "text/plain");
        try (java.sql.Connection conn = com.specodyssey.util.DBUtil.getConnection();
             java.sql.PreparedStatement p = conn.prepareStatement("UPDATE DOCUMENTS SET file_path = '' WHERE id = ?")) {
            p.setLong(1, empty.getId());
            p.executeUpdate();
        }
        FakeWeb.Request req = FakeWeb.request().loggedIn(owner);
        req.servletPath = "/documents";
        req.pathInfo = "/" + empty.getId();
        FakeWeb.Response resp = FakeWeb.response();
        download.doGet(req.http(), resp.http());
        assertEquals(404, resp.errorStatus);
    }
}

