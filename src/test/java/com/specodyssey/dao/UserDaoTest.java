package com.specodyssey.dao;

import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserDaoTest {

    private final UserDao userDao = new UserDao();

    @Test
    void insert_findByLoginId_updateProfile_softDelete() throws Exception {
        String loginId = "test_user_" + System.nanoTime();
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(loginId);
        user.setPasswordHash("dummy_hash");
        user.setMajor("컴퓨터공학");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());

        Long id = userDao.insert(user);
        assertNotNull(id);
        try {
            assertTrue(userDao.existsByLoginId(loginId));

            UserDto found = userDao.findByLoginId(loginId);
            assertNotNull(found);
            assertEquals("컴퓨터공학", found.getMajor());

            found.setEmail("a@b.com");
            found.setMajor("소프트웨어공학");
            found.setDesiredJobStatus("UNSET");
            found.setProfileUpdatedAt(LocalDateTime.now());
            userDao.updateProfile(found);

            UserDto updated = userDao.findById(id);
            assertEquals("소프트웨어공학", updated.getMajor());
            assertEquals("a@b.com", updated.getEmail());

            userDao.updateLastLogin(id);
            assertNotNull(userDao.findById(id).getLastLoginAt());

            try (Connection conn = DBUtil.getConnection()) {
                userDao.touchProfileUpdatedAt(conn, id);
            }

            userDao.softDelete(id);
            assertNull(userDao.findById(id));
            assertFalse(userDao.existsByLoginId(loginId));
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "USERS", id);
            }
        }
    }

    @Test
    void 본인이_올린_서류만_이력서로_지정할_수_있다() throws Exception {
        Long ownerId = userDao.insert(newUser("test_resume_owner_"));
        Long otherId = userDao.insert(newUser("test_resume_other_"));
        DocumentDao documentDao = new DocumentDao();
        Long ownDocId;
        Long otherDocId;
        try (Connection conn = DBUtil.getConnection()) {
            ownDocId = documentDao.insert(conn, newDocument(ownerId));
            otherDocId = documentDao.insert(conn, newDocument(otherId));
        }
        try {
            assertNull(userDao.findById(ownerId).getResumeDocumentId());

            assertTrue(userDao.updateResumeDocument(ownerId, ownDocId));
            assertEquals(ownDocId, userDao.findById(ownerId).getResumeDocumentId());

            // 남의 서류는 지정되지 않고 기존 값이 그대로 남는다
            assertFalse(userDao.updateResumeDocument(ownerId, otherDocId));
            assertEquals(ownDocId, userDao.findById(ownerId).getResumeDocumentId());

            // 프로필을 저장해도 이력서 지정은 유지된다
            UserDto owner = userDao.findById(ownerId);
            owner.setMajor("소프트웨어공학");
            userDao.updateProfile(owner);
            assertEquals(ownDocId, userDao.findById(ownerId).getResumeDocumentId());

            // 지운 서류는 지정할 수 없다
            try (Connection conn = DBUtil.getConnection()) {
                documentDao.delete(conn, otherDocId, otherId);
            }
            assertFalse(userDao.updateResumeDocument(otherId, otherDocId));

            // null을 넘기면 지정을 푼다
            assertTrue(userDao.updateResumeDocument(ownerId, null));
            assertNull(userDao.findById(ownerId).getResumeDocumentId());
        } finally {
            // USERS와 DOCUMENTS가 서로를 참조하므로 지정을 먼저 풀고 서류, 회원 순으로 지운다
            userDao.updateResumeDocument(ownerId, null);
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "DOCUMENTS", ownDocId);
                TestFixtures.hardDelete(conn, "DOCUMENTS", otherDocId);
                TestFixtures.hardDelete(conn, "USERS", ownerId);
                TestFixtures.hardDelete(conn, "USERS", otherId);
            }
        }
    }

    private UserDto newUser(String loginIdPrefix) {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(loginIdPrefix + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        return user;
    }

    private DocumentDto newDocument(Long userId) {
        DocumentDto document = new DocumentDto();
        document.setUserId(userId);
        document.setOriginalName("이력서.pdf");
        document.setStoredName("stored_" + System.nanoTime() + ".pdf");
        document.setFilePath("/uploads/test/stored.pdf");
        document.setFileSize(1024L);
        document.setMimeType("application/pdf");
        return document;
    }
}
