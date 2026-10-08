package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 데스크톱 캐릭터 연습장이 읽는 loadWithVersion (2026-10-08, 실제 DB).
 * 노트 내용이 DB(file_data)로 옮겨 간 뒤 디스크 경로로 읽다가 NullPointerException이 나 캐릭터 연습장이 열리지 않았다.
 */
class NoteServiceVersionTest {

    private final NoteService service = new NoteService();
    private final UserDao userDao = new UserDao();
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId("note_version_" + System.nanoTime());
        user.setPasswordHash("dummy_hash");
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        userId = userDao.insert(user);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            TestFixtures.hardDeleteByColumn(conn, "DOCUMENTS", "user_id", userId);
            TestFixtures.hardDelete(conn, "USERS", userId);
        }
    }

    @Test
    @DisplayName("저장한 노트를 내용·버전과 함께 다시 읽는다 — 캐릭터 연습장과 웹 연습장이 같은 내용")
    void 저장한_노트를_버전과_함께_읽는다() throws Exception {
        service.save(userId, "한글 노트\n둘째 줄");
        NoteService.Note note = service.loadWithVersion(userId);
        assertEquals("한글 노트\n둘째 줄", note.text());
        assertEquals(service.load(userId), note.text(), "웹 연습장과 같은 내용이어야 한다");
        assertFalse(note.version().isEmpty());
    }

    @Test
    @DisplayName("다시 저장하면 버전이 바뀐다 — 웹과 동시에 고쳤는지 이것으로 안다")
    void 다시_저장하면_버전이_바뀐다() throws Exception {
        service.save(userId, "처음");
        String first = service.loadWithVersion(userId).version();
        service.save(userId, "두 번째");
        NoteService.Note second = service.loadWithVersion(userId);
        assertEquals("두 번째", second.text());
        assertNotEquals(first, second.version());
    }

    @Test
    @DisplayName("노트가 없으면 빈 내용")
    void 노트가_없으면_빈_내용() throws Exception {
        assertEquals("", service.loadWithVersion(userId).text());
    }
}
