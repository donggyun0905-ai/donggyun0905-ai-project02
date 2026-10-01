package com.specodyssey.service;

import com.specodyssey.dao.TestFixtures;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 회원가입·내 프로필의 이름·나이·구분·학년 입력 검증과 저장 테스트.
 * 저장 테스트는 실제 DB에 회원을 만들고 끝나면 지운다.
 */
class PersonalInfoTest {

    @Test
    void 학생은_학년까지_받고_앞뒤_공백을_정리한다() {
        PersonalInfo info = PersonalInfo.of("  홍길동 ", " 23 ", "STUDENT", " 3학년 ");

        assertEquals("홍길동", info.getName());
        assertEquals(23, info.getAge());
        assertEquals("STUDENT", info.getCareerStatus());
        assertEquals("3학년", info.getGrade());
    }

    @Test
    void 학생이_아니면_학년을_받지_않는다() {
        assertNull(PersonalInfo.of("홍길동", "27", "JOB_SEEKER", "4학년").getGrade());
        assertNull(PersonalInfo.of("홍길동", "31", "EMPLOYED", null).getGrade());
    }

    @Test
    void 이름_나이_구분은_필수이고_학생은_학년도_필수다() {
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of(" ", "23", "STUDENT", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of(null, "23", "STUDENT", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("가".repeat(51), "23", "STUDENT", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", "", "STUDENT", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", null, "STUDENT", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", "스물셋", "STUDENT", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", "0", "STUDENT", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", "121", "STUDENT", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", "23", null, "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", "23", "RETIRED", "3학년"));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", "23", "STUDENT", " "));
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.of("홍길동", "23", "STUDENT", null));
    }

    @Test
    void 면접관은_이름만_받는다() {
        PersonalInfo info = PersonalInfo.nameOnly(" 김면접 ");

        assertEquals("김면접", info.getName());
        assertNull(info.getAge());
        assertNull(info.getCareerStatus());
        assertThrows(IllegalArgumentException.class, () -> PersonalInfo.nameOnly(""));
    }

    @Test
    void 가입과_프로필_수정에서_이름_나이_구분_학년이_저장된다() throws Exception {
        UserDao userDao = new UserDao();
        Long userId = new UserService().register("test_personal_" + System.nanoTime(), "password1234", null,
                PersonalInfo.of("홍길동", "23", "STUDENT", "3학년"), "컴퓨터공학과", null);
        try {
            UserDto saved = userDao.findById(userId);
            assertEquals("APPLICANT", saved.getUserType());
            assertEquals("홍길동", saved.getName());
            assertEquals(23, saved.getAge());
            assertEquals("STUDENT", saved.getCareerStatus());
            assertEquals("3학년", saved.getGrade());

            // 졸업해서 취준생이 되면 학년은 지워진다
            new ProfileService().updateBasicInfo(userId, PersonalInfo.of("홍길동", "24", "JOB_SEEKER", "3학년"),
                    null, "컴퓨터공학과", null, null, "UNSET");

            UserDto updated = userDao.findById(userId);
            assertEquals(24, updated.getAge());
            assertEquals("JOB_SEEKER", updated.getCareerStatus());
            assertNull(updated.getGrade());
            assertEquals("컴퓨터공학과", updated.getMajor());
        } finally {
            try (Connection conn = DBUtil.getConnection()) {
                TestFixtures.hardDelete(conn, "USERS", userId);
            }
        }
    }
}
