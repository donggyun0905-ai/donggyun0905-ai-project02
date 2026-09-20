package com.specodyssey.service;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.PasswordUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 회원가입 · 로그인 비즈니스 로직.
 * 관련 요구사항: FR-11 · 12 · 13 · 14
 */
public class UserService {

    private final UserDao userDao = new UserDao();

    public static class DuplicateLoginIdException extends Exception {
        public DuplicateLoginIdException(String message) {
            super(message);
        }
    }

    public static class InvalidCredentialException extends Exception {
        public InvalidCredentialException(String message) {
            super(message);
        }
    }

    // FR-11~14 회원가입
    public Long register(String loginId, String rawPassword, String email,
                          String major, String grade, String interestField)
            throws SQLException, DuplicateLoginIdException {
        if (userDao.existsByLoginId(loginId)) {
            throw new DuplicateLoginIdException("이미 사용 중인 아이디입니다.");
        }

        UserDto user = new UserDto();
        user.setUserType("APPLICANT");
        user.setLoginId(loginId);
        user.setPasswordHash(PasswordUtil.hash(rawPassword));
        user.setEmail(email);
        user.setMajor(major);
        user.setGrade(grade);
        user.setInterestField(interestField);
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());

        return userDao.insert(user);
    }

    // FR-12 로그인
    public UserDto login(String loginId, String rawPassword)
            throws SQLException, InvalidCredentialException {
        UserDto user = userDao.findByLoginId(loginId);
        if (user == null || !PasswordUtil.verify(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        userDao.updateLastLogin(user.getId());
        return user;
    }
}
