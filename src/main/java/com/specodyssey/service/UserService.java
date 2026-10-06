package com.specodyssey.service;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.PasswordUtil;
import com.specodyssey.util.RecoveryCode;

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
    // 탈퇴 유예 기간(팀 확인 2026-10-03) — 이 기간에는 아이디를 잡아 두고, 다시 로그인하면 탈퇴를 취소할 수 있다
    public static final int WITHDRAWAL_GRACE_DAYS = 30;

    private final UserDao userDao = new UserDao();

    /** 비밀번호는 맞지만 유예 중인 탈퇴 계정 — 화면에서 탈퇴 취소를 제안한다. */
    public static class PendingWithdrawalException extends Exception {
        private final Long userId;
        private final LocalDateTime purgeAt;
        private final boolean recoveryCodeRequired;

        public PendingWithdrawalException(Long userId, LocalDateTime purgeAt, boolean recoveryCodeRequired) {
            super("탈퇴 신청한 계정입니다.");
            this.userId = userId;
            this.purgeAt = purgeAt;
            this.recoveryCodeRequired = recoveryCodeRequired;
        }

        public Long getUserId() {
            return userId;
        }

        /** 이 시각이 지나면 탈퇴가 확정돼 복구할 수 없다. */
        public LocalDateTime getPurgeAt() {
            return purgeAt;
        }

        /** 복구 코드를 받아 둔 계정이면 탈퇴 취소에도 그 코드를 받는다(복구 코드가 없는 옛 계정은 못 받는다). */
        public boolean isRecoveryCodeRequired() {
            return recoveryCodeRequired;
        }
    }

    private static LocalDateTime graceCutoff() {
        return LocalDateTime.now().minusDays(WITHDRAWAL_GRACE_DAYS);
    }

    /** FR-14 아이디 실시간 확인 결과. available이 false면 message를 입력칸 아래에 그대로 보여 준다. */
    public record LoginIdCheck(boolean available, String message) {
    }

    /**
     * FR-14 아이디를 쓸 수 있는지 — 가입 화면이 입력칸을 벗어날 때마다 물어본다.
     * 가입할 때(newUser)와 같은 기준으로 판단해서 두 곳의 안내 문구가 어긋나지 않게 한다.
     */
    public LoginIdCheck checkLoginId(String loginId) throws SQLException {
        String trimmed = loginId == null ? "" : loginId.trim();
        if (trimmed.isEmpty() || trimmed.length() > LOGIN_ID_MAX_LENGTH) {
            return new LoginIdCheck(false, "아이디는 1~" + LOGIN_ID_MAX_LENGTH + "자로 입력해주세요.");
        }
        if (userDao.existsByLoginId(trimmed)) {
            return new LoginIdCheck(false, "이미 사용 중인 아이디입니다.");
        }
        UserDto pending = userDao.findPendingWithdrawalByLoginId(trimmed, graceCutoff());
        if (pending != null) {
            return new LoginIdCheck(false, pendingWithdrawalMessage(pending));
        }
        return new LoginIdCheck(true, "사용할 수 있는 아이디입니다.");
    }

    // 유예가 끝나면 아이디가 자동으로 풀리므로, 막혔다는 말만 하지 않고 언제 쓸 수 있는지 같이 알려 준다
    private static String pendingWithdrawalMessage(UserDto pending) {
        LocalDateTime freeAt = pending.getWithdrawRequestedAt().plusDays(WITHDRAWAL_GRACE_DAYS);
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), freeAt);
        return "탈퇴 유예 중인 계정이 쓰고 있는 아이디입니다. " + Math.max(1, days) + "일 뒤에 쓸 수 있어요.";
    }

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

    /** 되살리려는 사이에 유예 기간이 끝났다 — 복구 코드를 다시 받아도 소용없으므로 재시도 화면을 띄우지 않는다. */
    public static class WithdrawalGraceExpiredException extends Exception {
        public WithdrawalGraceExpiredException(String message) {
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

        LocalDateTime cutoff = graceCutoff();
        if (userDao.existsByLoginId(trimmedLoginId)) {
            throw new DuplicateLoginIdException("이미 사용 중인 아이디입니다.");
        }
        // 유예 중인 탈퇴 계정의 아이디도 "사용 중" — 그 사람이 탈퇴를 취소하면 원래 아이디로 돌아와야 한다.
        // 그냥 "사용 중"이라고만 하면 왜 막혔는지 알 수 없어서, 언제 풀리는지까지 알려 준다.
        UserDto pendingHolder = userDao.findPendingWithdrawalByLoginId(trimmedLoginId, cutoff);
        if (pendingHolder != null) {
            throw new DuplicateLoginIdException(pendingWithdrawalMessage(pendingHolder));
        }
        // 유예가 없는 예전 탈퇴 계정이나 유예가 끝난 계정이 아이디를 쥐고 있으면(UNIQUE) 가입이 막히므로 여기서 비워 준다
        userDao.releaseDeletedLoginId(trimmedLoginId, cutoff);

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
            throws SQLException, InvalidCredentialException, PendingWithdrawalException {
        UserDto user = userDao.findByLoginId(loginId);
        if (user == null) {
            // 탈퇴 유예 중인 계정이면, 비밀번호가 맞을 때만 탈퇴 취소를 제안한다(틀리면 일반 실패와 구분하지 않는다)
            UserDto pending = loginId == null ? null : userDao.findPendingWithdrawalByLoginId(loginId, graceCutoff());
            if (pending != null && rawPassword != null && PasswordUtil.verify(rawPassword, pending.getPasswordHash())) {
                throw new PendingWithdrawalException(pending.getId(),
                        pending.getWithdrawRequestedAt().plusDays(WITHDRAWAL_GRACE_DAYS),
                        pending.getRecoveryCodeHash() != null);
            }
            throw new InvalidCredentialException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (!PasswordUtil.verify(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        userDao.updateLastLogin(user.getId());
        return user;
    }

    // FR-13 회원 탈퇴 — 비밀번호 재확인 후 논리 삭제. 유예 기간 동안은 다시 로그인해 취소할 수 있다.
    public void withdraw(Long userId, String rawPassword) throws SQLException, InvalidCredentialException {
        UserDto user = userDao.findById(userId);
        if (user == null || !PasswordUtil.verify(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialException("비밀번호가 올바르지 않습니다.");
        }
        userDao.softDelete(userId);
    }

    /**
     * 탈퇴 취소 — 로그인에서 비밀번호를 확인한(PendingWithdrawalException) 계정만 호출한다.
     *
     * 비밀번호만으로 되살리면 비밀번호가 새어 나간 계정을 남이 되살려 기록을 다 볼 수 있어서, 복구 코드를
     * 받아 둔 계정은 그 코드까지 맞아야 되살린다(사용자 요청, 2026-10-06). 복구 코드를 발급받기 전에
     * 만들어진 계정(recovery_code_hash가 비어 있음)은 받을 코드가 없으므로 비밀번호 확인만으로 되살린다 —
     * 그렇지 않으면 그 계정은 유예가 끝날 때까지 복구할 길이 아예 없다.
     *
     * @param recoveryCode 복구 코드를 받아 둔 계정이면 필수, 아니면 무시한다
     * @return 되살린 계정(로그인 처리에 쓴다)
     * @throws InvalidCredentialException 복구 코드가 틀린 경우
     * @throws WithdrawalGraceExpiredException 그사이 유예 기간이 끝났거나 이미 취소된 경우
     */
    public UserDto cancelWithdrawal(Long userId, String recoveryCode)
            throws SQLException, InvalidCredentialException, WithdrawalGraceExpiredException {
        UserDto pending = userDao.findByIdIncludingDeleted(userId);
        String stored = pending == null ? null : pending.getRecoveryCodeHash();
        if (stored != null) {
            // 코드가 틀렸을 때도 맞을 때와 같은 횟수의 PBKDF2를 돌린다(응답 시간으로 짐작하지 못하게)
            boolean matches = PasswordUtil.verify(RecoveryCode.forHash(recoveryCode), stored);
            if (!RecoveryCode.looksValid(recoveryCode) || !matches) {
                throw new InvalidCredentialException("복구 코드가 올바르지 않습니다.");
            }
        }
        if (!userDao.cancelWithdrawal(userId, graceCutoff())) {
            throw new WithdrawalGraceExpiredException("탈퇴를 취소할 수 있는 기간이 지났습니다. 다시 가입해주세요.");
        }
        userDao.updateLastLogin(userId);
        return userDao.findById(userId);
    }

    /** WithdrawalPurgeScheduler 전용 — 유예가 끝난 탈퇴 계정의 아이디를 비우고 개인정보를 지운다. */
    public int purgeExpiredWithdrawals() throws SQLException {
        return userDao.purgeExpiredWithdrawals(graceCutoff());
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

    // 복구 코드가 맞지 않을 때도 해시 계산 시간이 같도록, 없는 계정·코드 없는 계정에 대신 비교하는 값
    private static final String DUMMY_HASH = PasswordUtil.hash("recovery-code-dummy");

    /**
     * 복구 코드를 새로 발급한다(가입 직후, 또는 로그인한 사용자가 프로필에서 재발급). 이전 코드는 바로 못 쓰게 된다.
     * @return 화면에 한 번만 보여 줄 복구 코드 원문 — 서버에는 해시만 남는다
     */
    public String issueRecoveryCode(Long userId) throws SQLException {
        String code = RecoveryCode.generate();
        if (!userDao.updateRecoveryCodeHash(userId, PasswordUtil.hash(RecoveryCode.forHash(code)))) {
            throw new IllegalStateException("복구 코드를 저장할 계정을 찾을 수 없습니다: " + userId);
        }
        return code;
    }

    /** 프로필에서 재발급 — 현재 비밀번호를 다시 확인한 뒤에만 새 코드를 만든다. */
    public String reissueRecoveryCode(Long userId, String currentPassword)
            throws SQLException, InvalidCredentialException {
        UserDto user = userDao.findById(userId);
        if (user == null || currentPassword == null || !PasswordUtil.verify(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialException("현재 비밀번호가 올바르지 않습니다.");
        }
        return issueRecoveryCode(userId);
    }

    /**
     * 비밀번호 찾기 — 아이디 + 복구 코드가 맞으면 새 비밀번호로 바꾸고, 쓴 복구 코드는 없애고 새 코드를 만들어 돌려준다.
     * 아이디가 없거나 복구 코드가 없는 계정이거나 코드가 틀린 경우를 구분하지 않고 같은 메시지로 거절한다(계정 존재 여부 노출 방지).
     * @return 새 복구 코드 원문(화면에 한 번만)
     */
    public String resetPasswordWithRecoveryCode(String loginId, String recoveryCode, String newPassword)
            throws SQLException, InvalidCredentialException, InvalidInputException {
        requirePasswordRule(newPassword);
        UserDto user = loginId == null ? null : userDao.findByLoginId(loginId.trim());
        // 탈퇴 유예 중인 계정도 비밀번호를 찾을 수 있어야 로그인해서 탈퇴를 취소할 수 있다
        boolean pendingWithdrawal = false;
        if (user == null && loginId != null) {
            user = userDao.findPendingWithdrawalByLoginId(loginId.trim(), graceCutoff());
            pendingWithdrawal = user != null;
        }
        String stored = user == null ? null : user.getRecoveryCodeHash();
        boolean formatOk = RecoveryCode.looksValid(recoveryCode);
        // 어떤 경우든 같은 횟수의 PBKDF2를 돌려 응답 시간으로 계정 상태를 짐작하지 못하게 한다
        boolean matches = PasswordUtil.verify(RecoveryCode.forHash(recoveryCode), stored == null ? DUMMY_HASH : stored);
        if (user == null || stored == null || !formatOk || !matches) {
            throw new InvalidCredentialException("아이디 또는 복구 코드가 올바르지 않습니다.");
        }
        String newCode = RecoveryCode.generate();
        String newPasswordHash = PasswordUtil.hash(newPassword);
        String newCodeHash = PasswordUtil.hash(RecoveryCode.forHash(newCode));
        if (pendingWithdrawal) {
            userDao.resetPasswordAndRecoveryCodeOfPendingWithdrawal(user.getId(), newPasswordHash, newCodeHash,
                    graceCutoff());
        } else {
            userDao.resetPasswordAndRecoveryCode(user.getId(), newPasswordHash, newCodeHash);
        }
        return newCode;
    }
}
