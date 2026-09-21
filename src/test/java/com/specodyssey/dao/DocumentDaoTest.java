package com.specodyssey.dao;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentDaoTest {

    private static final UserDao userDao = new UserDao();
    private final DocumentDao dao = new DocumentDao();
    private static Long userId;

    @BeforeAll
    static void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("test_doc_user_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    void insert_findByUserId_update_delete() throws Exception {
        DocumentDto document = new DocumentDto();
        document.setUserId(userId);
        document.setOriginalName("이력서.pdf");
        document.setStoredName("stored_" + System.nanoTime() + ".pdf");
        document.setFilePath("/uploads/test/stored.pdf");
        document.setFileSize(1024L);
        document.setMimeType("application/pdf");
        document.setChecksum("abc123");

        Long id;
        try (Connection conn = DBUtil.getConnection()) {
            id = dao.insert(conn, document);
        }
        try {
            assertNotNull(id);
            List<DocumentDto> docs = dao.findByUserId(userId);
            assertEquals(1, docs.size());
            assertEquals("이력서.pdf", docs.get(0).getOriginalName());

            DocumentDto toUpdate = docs.get(0);
            toUpdate.setOriginalName("이력서_최종.pdf");
            try (Connection conn = DBUtil.getConnection()) {
                dao.update(conn, toUpdate, userId);
            }
            assertEquals("이력서_최종.pdf", dao.findByUserId(userId).get(0).getOriginalName());

            try (Connection conn = DBUtil.getConnection()) {
                dao.delete(conn, id, userId);
            }
            assertTrue(dao.findByUserId(userId).isEmpty());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "DOCUMENTS", id);
            }
        }
    }
}
