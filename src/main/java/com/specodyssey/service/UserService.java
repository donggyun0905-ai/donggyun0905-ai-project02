package com.specodyssey.service;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.PasswordUtil;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;

/**
 * 회원가입 · 로그인 비즈니스 로직.
 * 관련 요구사항: FR-11 · 12 · 13 · 14
 */
public class UserService {

    private static final int LOGIN_ID_MAX_LENGTH = 50; // USERS.login_id VARCHAR(50)
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 100; // 과도하게 긴 입력으로 PBKDF2 반복 비용을 늘리는 것을 방지

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

    public static class InvalidInputException extends Exception {
        public InvalidInputException(String message) {
            super(message);
        }
    }

    // FR-11~14 회원가입
    // 이름·나이·구분(학생/취준생/직장인)은 필수, 학년은 학생일 때만 받는다 — PersonalInfo가 검증한다.
    public Long register(String loginId, String rawPassword, String email,
                          PersonalInfo personalInfo, String major, String interestField)
            throws SQLException, DuplicateLoginIdException, InvalidInputException {
        UserDto user = newUser("APPLICANT", loginId, rawPassword, email);
        user.setName(personalInfo.getName());
        user.setAge(personalInfo.getAge());
        user.setCareerStatus(personalInfo.getCareerStatus());
        user.setGrade(personalInfo.getGrade());
        user.setMajor(major);
        user.setInterestField(interestField);
        return insert(user);
    }

    // 면접관 가입 — 이름만 받고 나이·전공·학년 같은 지원자 항목은 받지 않는다. 회사명은 면접관의 비교 목록에 저장한다.
    public Long registerInterviewer(String loginId, String rawPassword, String email,
                                    PersonalInfo personalInfo, String companyName)
            throws SQLException, DuplicateLoginIdException, InvalidInputException {
        String company;
        try {
            company = InterviewerService.normalizeCompanyName(companyName);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(e.getMessage());
        }
        UserDto user = newUser("INTERVIEWER", loginId, rawPassword, email);
        user.setName(personalInfo.getName());
        Long userId = insert(user);
        new InterviewerService().getOrCreateSession(userId, company);
        return userId;
    }

    private UserDto newUser(String userType, String loginId, String rawPassword, String email)
            throws SQLException, DuplicateLoginIdException, InvalidInputException {
        String trimmedLoginId = loginId == null ? "" : loginId.trim();
        if (trimmedLoginId.isEmpty() || trimmedLoginId.length() > LOGIN_ID_MAX_LENGTH) {
            throw new InvalidInputException("아이디는 1~" + LOGIN_ID_MAX_LENGTH + "자로 입력해주세요.");
        }
        requirePasswordRule(rawPassword);

        if (userDao.existsByLoginId(trimmedLoginId)) {
            throw new DuplicateLoginIdException("이미 사용 중인 아이디입니다.");
        }

        UserDto user = new UserDto();
        user.setUserType(userType);
        user.setLoginId(trimmedLoginId);
        user.setPasswordHash(PasswordUtil.hash(rawPassword));
        user.setEmail(email);
        user.setDesiredJobStatus("UNSET");
        user.setPrivacyConsentAt(LocalDateTime.now());
        return user;
    }

    // 가입과 비밀번호 변경이 같은 규칙을 쓴다
    private static void requirePasswordRule(String rawPassword) throws InvalidInputException {
        if (rawPassword == null || rawPassword.length() < PASSWORD_MIN_LENGTH
                || rawPassword.length() > PASSWORD_MAX_LENGTH) {
            throw new InvalidInputException(
                    "비밀번호는 " + PASSWORD_MIN_LENGTH + "~" + PASSWORD_MAX_LENGTH + "자로 입력해주세요.");
        }
    }

    private Long insert(UserDto user) throws SQLException, DuplicateLoginIdException {
        try {
            return userDao.insert(user);
        } catch (SQLIntegrityConstraintViolationException e) {
            // existsByLoginId 확인 이후 동시 요청으로 먼저 가입된 경우(TOCTOU) — UNIQUE(login_id) 위반을 여기서 최종 방어
            throw new DuplicateLoginIdException("이미 사용 중인 아이디입니다.");
        }
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

    // FR-13 회원 탈퇴 — 비밀번호 재확인 후 논리 삭제
    public void withdraw(Long userId, String rawPassword) throws SQLException, InvalidCredentialException {
        UserDto user = userDao.findById(userId);
        if (user == null || !PasswordUtil.verify(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialException("비밀번호가 올바르지 않습니다.");
        }
        userDao.softDelete(userId);
    }

    /**
     * 비밀번호 변경 — 현재 비밀번호를 다시 확인하고, 새 비밀번호는 가입 때와 같은 규칙을 지켜야 하며 현재와 달라야 한다.
     * @throws InvalidCredentialException 현재 비밀번호가 틀림
     * @throws InvalidInputException 새 비밀번호가 규칙에 맞지 않거나 현재와 같음
     */
    public void changePassword(Long userId, String currentPassword, String newPassword)
            throws SQLException, InvalidCredentialException, InvalidInputException {
        UserDto user = userDao.findById(userId);
        if (user == null || currentPassword == null || !PasswordUtil.verify(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialException("현재 비밀번호가 올바르지 않습니다.");
        }
        requirePasswordRule(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new InvalidInputException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        userDao.updatePasswordHash(userId, PasswordUtil.hash(newPassword));
    }
}
