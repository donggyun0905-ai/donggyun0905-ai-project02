package com.specodyssey.dao;

import com.specodyssey.dto.UserDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * USERS 테이블 DAO.
 * 관련 요구사항: FR-11 · 12 · 13 · 14 · 21 · 22
 */
public class UserDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, user_type, login_id, password_hash, name, age, career_status, email, " +
            "major, grade, interest_field, desired_job_id, desired_job_status, " +
            "resume_document_id, cover_letter_document_id, privacy_consent_at, recovery_code_hash, " +
            "profile_updated_at, last_login_at, withdraw_requested_at, created_at, updated_at, is_deleted";

    // FR-11~13 회원가입
    public Long insert(UserDto user) throws SQLException {
        String sql = "INSERT INTO USERS " +
                "(user_type, login_id, password_hash, name, age, career_status, email, major, grade, " +
                " interest_field, desired_job_id, desired_job_status, privacy_consent_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, user.getUserType());
            pstmt.setString(2, user.getLoginId());
            pstmt.setString(3, user.getPasswordHash());
            pstmt.setString(4, user.getName());
            setNullableInt(pstmt, 5, user.getAge());
            pstmt.setString(6, user.getCareerStatus());
            pstmt.setString(7, user.getEmail());
            pstmt.setString(8, user.getMajor());
            pstmt.setString(9, user.getGrade());
            pstmt.setString(10, user.getInterestField());
            setNullableLong(pstmt, 11, user.getDesiredJobId());
            pstmt.setString(12, user.getDesiredJobStatus());
            pstmt.setTimestamp(13, toTimestamp(user.getPrivacyConsentAt()));

            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return null;
        }
    }

    // SpecScoreScheduler의 일 1회 전체 스냅샷 배치용
    // 관리자 회원 검색(2026-10-06) — 아이디·이름·이메일 부분 일치. 탈퇴(is_deleted)해도 보여야
    // 유예 기간 중인 사람을 찾을 수 있어서 삭제 여부로 거르지 않는다.
    public List<UserDto> searchForAdmin(String keyword, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USERS " +
                "WHERE login_id LIKE ? OR name LIKE ? OR email LIKE ? " +
                "ORDER BY id DESC LIMIT ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String like = "%" + keyword + "%";
            pstmt.setString(1, like);
            pstmt.setString(2, like);
            pstmt.setString(3, like);
            pstmt.setInt(4, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<UserDto> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
                return users;
            }
        }
    }

    // 관리자가 탈퇴한 계정도 볼 수 있어야 하므로 findById(is_deleted=FALSE 조건)와 분리했다.
    public UserDto findByIdForAdmin(Long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USERS WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<UserDto> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USERS WHERE is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<UserDto> users = new ArrayList<>();
            while (rs.next()) {
                users.add(mapRow(rs));
            }
            return users;
        }
    }

    // FR-14 아이디 중복 확인
    public boolean existsByLoginId(String loginId) throws SQLException {
        String sql = "SELECT 1 FROM USERS WHERE login_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loginId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // FR-12 로그인
    public UserDto findByLoginId(String loginId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USERS WHERE login_id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loginId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 사이드 위젯 전용 — 세션 사본은 희망 직무를 바꿔도 늦게 갱신될 수 있어 매 요청 DB에서 이 값만 가볍게 읽는다
    public Long findDesiredJobId(Long userId) throws SQLException {
        String sql = "SELECT desired_job_id FROM USERS WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getObject("desired_job_id", Long.class) : null;
            }
        }
    }

    // 세션 기반 본인 프로필 조회
    public UserDto findById(Long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USERS WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // FR-21 · 22 프로필 수정 (기본정보 + 희망 직무)
    public void updateProfile(UserDto user) throws SQLException {
        String sql = "UPDATE USERS SET " +
                "name = ?, age = ?, career_status = ?, email = ?, major = ?, grade = ?, interest_field = ?, " +
                "desired_job_id = ?, desired_job_status = ?, profile_updated_at = ? " +
                "WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getName());
            setNullableInt(pstmt, 2, user.getAge());
            pstmt.setString(3, user.getCareerStatus());
            pstmt.setString(4, user.getEmail());
            pstmt.setString(5, user.getMajor());
            pstmt.setString(6, user.getGrade());
            pstmt.setString(7, user.getInterestField());
            setNullableLong(pstmt, 8, user.getDesiredJobId());
            pstmt.setString(9, user.getDesiredJobStatus());
            pstmt.setTimestamp(10, toTimestamp(user.getProfileUpdatedAt()));
            pstmt.setLong(11, user.getId());

            pstmt.executeUpdate();
        }
    }

    // FR-39: 직무 발굴에서 추천 후보를 선택하면 그 직무를 희망 직무로 확정한다.
    // 다른 기본정보 필드(email·major 등)는 손대지 않기 위해 updateProfile과 분리했다.
    public void updateDesiredJob(Connection conn, Long userId, Long desiredJobId, String desiredJobStatus)
            throws SQLException {
        String sql = "UPDATE USERS SET desired_job_id = ?, desired_job_status = ? WHERE id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setNullableLong(pstmt, 1, desiredJobId);
            pstmt.setString(2, desiredJobStatus);
            pstmt.setLong(3, userId);
            pstmt.executeUpdate();
        }
    }

    // 서류 보관함(DOCUMENTS)의 파일 하나를 이력서로 지정한다. documentId가 null이면 지정을 푼다.
    // 본인이 올린, 지워지지 않은 파일만 지정할 수 있다 — 조건에 안 맞으면 0행 갱신이라 false를 돌려준다.
    // 다른 기본정보 필드는 손대지 않기 위해 updateProfile과 분리했다.
    public boolean updateResumeDocument(Long userId, Long documentId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return updateResumeDocument(conn, userId, documentId);
        }
    }

    // 이력서 교체처럼 서류 저장과 한 트랜잭션으로 묶을 때 쓴다
    public boolean updateResumeDocument(Connection conn, Long userId, Long documentId) throws SQLException {
        return updateProfileDocument(conn, "resume_document_id", userId, documentId);
    }

    // 자소서도 이력서와 같은 방식이다 — 본인이 올린, 지워지지 않은 파일만 지정할 수 있다
    public boolean updateCoverLetterDocument(Long userId, Long documentId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return updateCoverLetterDocument(conn, userId, documentId);
        }
    }

    public boolean updateCoverLetterDocument(Connection conn, Long userId, Long documentId) throws SQLException {
        return updateProfileDocument(conn, "cover_letter_document_id", userId, documentId);
    }

    // column은 호출부가 코드에서 고정한 값만 넘긴다(사용자 입력이 아님)
    private boolean updateProfileDocument(Connection conn, String column, Long userId, Long documentId)
            throws SQLException {
        String sql = "UPDATE USERS SET " + column + " = ? WHERE id = ? AND is_deleted = FALSE " +
                "AND (? IS NULL OR EXISTS (SELECT 1 FROM DOCUMENTS d " +
                "WHERE d.id = ? AND d.user_id = ? AND d.is_deleted = FALSE))";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setNullableLong(pstmt, 1, documentId);
            pstmt.setLong(2, userId);
            setNullableLong(pstmt, 3, documentId);
            setNullableLong(pstmt, 4, documentId);
            pstmt.setLong(5, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    // 비밀번호 변경 — 새 해시만 바꾼다. 본인(id) 한 행만 대상이고 탈퇴한 계정은 건드리지 않는다.
    public boolean updatePasswordHash(Long userId, String passwordHash) throws SQLException {
        String sql = "UPDATE USERS SET password_hash = ? WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, passwordHash);
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    // 복구 코드(해시)를 새로 정한다 — 발급·재발급. 이전 코드는 이 순간부터 쓸 수 없다.
    public boolean updateRecoveryCodeHash(Long userId, String recoveryCodeHash) throws SQLException {
        String sql = "UPDATE USERS SET recovery_code_hash = ? WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recoveryCodeHash);
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    // 복구 코드로 비밀번호를 찾을 때 — 새 비밀번호와 새 복구 코드를 한 번에 바꾼다(쓴 코드는 바로 못 쓰게).
    public boolean resetPasswordAndRecoveryCode(Long userId, String passwordHash, String recoveryCodeHash)
            throws SQLException {
        String sql = "UPDATE USERS SET password_hash = ?, recovery_code_hash = ? WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, passwordHash);
            pstmt.setString(2, recoveryCodeHash);
            pstmt.setLong(3, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    // FR-12 로그인 성공 시 마지막 접속 시각 갱신
    public void updateLastLogin(Long id) throws SQLException {
        String sql = "UPDATE USERS SET last_login_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    // FR-37 재분석 트리거 기준 — 스펙/프로젝트/스킬 변경 시 같은 트랜잭션에서 호출
    public void touchProfileUpdatedAt(Connection conn, Long userId) throws SQLException {
        String sql = "UPDATE USERS SET profile_updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        }
    }

    // 탈퇴한 계정이 아직 아이디를 쥐고 있으면(UNIQUE) 가입이 막히므로 비운다. 대상은 유예가 없는 예전 탈퇴 계정
    // (withdraw_requested_at IS NULL)과 유예(graceCutoff 이전 신청)가 끝났는데 정리 배치가 아직 안 돈 계정뿐이다 —
    // 유예 중인 계정의 아이디는 탈퇴 취소를 위해 잡아 둔다. 비운 계정 수를 돌려준다.
    public int releaseDeletedLoginId(String loginId, java.time.LocalDateTime graceCutoff) throws SQLException {
        String sql = "UPDATE USERS SET login_id = LEFT(CONCAT('del_', id, '_', login_id), 50) " +
                "WHERE login_id = ? AND is_deleted = TRUE " +
                "AND (withdraw_requested_at IS NULL OR withdraw_requested_at < ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loginId);
            pstmt.setTimestamp(2, toTimestamp(graceCutoff));
            return pstmt.executeUpdate();
        }
    }

    // FR-13 회원 탈퇴 — 논리 삭제 + 탈퇴 신청 시각 기록. 유예 기간 동안은 아이디를 바꾸지 않고 잡아 둔다(탈퇴 취소용).
    // 아이디 비우기·개인정보 정리는 유예가 끝난 뒤 purgeExpiredWithdrawals가 한다. 탈퇴 계정은 일반 로그인으로 찾을 수 없다.
    public void softDelete(Long id) throws SQLException {
        String sql = "UPDATE USERS SET is_deleted = TRUE, withdraw_requested_at = ? WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, toTimestamp(java.time.LocalDateTime.now()));
            pstmt.setLong(2, id);
            pstmt.executeUpdate();
        }
    }

    // 유예 중인 탈퇴 계정 조회 — 로그인 시 탈퇴 취소를 제안하거나, 복구 코드로 비밀번호를 찾을 때,
    // 가입 아이디 중복 확인에서 "누가 이 아이디를 언제까지 잡고 있는지" 알려 줄 때 쓴다.
    public UserDto findPendingWithdrawalByLoginId(String loginId, java.time.LocalDateTime graceCutoff)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USERS " +
                "WHERE login_id = ? AND is_deleted = TRUE AND withdraw_requested_at >= ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loginId);
            pstmt.setTimestamp(2, toTimestamp(graceCutoff));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 탈퇴 취소 — 유예 중인 계정만 되살린다. 아이디는 그동안 바꾸지 않았으므로 그대로 쓸 수 있다.
    public boolean cancelWithdrawal(Long id, java.time.LocalDateTime graceCutoff) throws SQLException {
        String sql = "UPDATE USERS SET is_deleted = FALSE, withdraw_requested_at = NULL " +
                "WHERE id = ? AND is_deleted = TRUE AND withdraw_requested_at >= ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.setTimestamp(2, toTimestamp(graceCutoff));
            return pstmt.executeUpdate() > 0;
        }
    }

    // 유예 중인 탈퇴 계정의 비밀번호 찾기 — 계정은 탈퇴 상태 그대로 두고 비밀번호·복구 코드만 바꾼다(다음 로그인에서 탈퇴 취소).
    public boolean resetPasswordAndRecoveryCodeOfPendingWithdrawal(Long userId, String passwordHash,
            String recoveryCodeHash, java.time.LocalDateTime graceCutoff) throws SQLException {
        String sql = "UPDATE USERS SET password_hash = ?, recovery_code_hash = ? " +
                "WHERE id = ? AND is_deleted = TRUE AND withdraw_requested_at >= ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, passwordHash);
            pstmt.setString(2, recoveryCodeHash);
            pstmt.setLong(3, userId);
            pstmt.setTimestamp(4, toTimestamp(graceCutoff));
            return pstmt.executeUpdate() > 0;
        }
    }

    // 유예가 끝난 탈퇴 계정 정리 — 아이디를 del_<id>_로 비우고(id가 앞에 있어 잘려도 겹치지 않는다) 개인정보를 지운다.
    // 행 자체는 논리 삭제 원칙대로 남긴다. 이미 비운 행(del_로 시작)은 건너뛰어 여러 번 돌려도 안전하다. 정리한 수를 돌려준다.
    public int purgeExpiredWithdrawals(java.time.LocalDateTime graceCutoff) throws SQLException {
        String sql = "UPDATE USERS SET login_id = LEFT(CONCAT('del_', id, '_', login_id), 50), " +
                "name = NULL, age = NULL, career_status = NULL, email = NULL, recovery_code_hash = NULL " +
                "WHERE is_deleted = TRUE AND withdraw_requested_at < ? AND login_id NOT LIKE 'del\\_%'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, toTimestamp(graceCutoff));
            return pstmt.executeUpdate();
        }
    }

    private UserDto mapRow(ResultSet rs) throws SQLException {
        UserDto user = new UserDto();
        user.setId(rs.getLong("id"));
        user.setUserType(rs.getString("user_type"));
        user.setLoginId(rs.getString("login_id"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setName(rs.getString("name"));
        user.setAge(rs.getObject("age", Integer.class));
        user.setCareerStatus(rs.getString("career_status"));
        user.setEmail(rs.getString("email"));
        user.setMajor(rs.getString("major"));
        user.setGrade(rs.getString("grade"));
        user.setInterestField(rs.getString("interest_field"));
        user.setDesiredJobId(rs.getObject("desired_job_id", Long.class));
        user.setDesiredJobStatus(rs.getString("desired_job_status"));
        user.setResumeDocumentId(rs.getObject("resume_document_id", Long.class));
        user.setCoverLetterDocumentId(rs.getObject("cover_letter_document_id", Long.class));
        user.setPrivacyConsentAt(toLocalDateTime(rs.getTimestamp("privacy_consent_at")));
        user.setRecoveryCodeHash(rs.getString("recovery_code_hash"));
        user.setProfileUpdatedAt(toLocalDateTime(rs.getTimestamp("profile_updated_at")));
        user.setLastLoginAt(toLocalDateTime(rs.getTimestamp("last_login_at")));
        user.setWithdrawRequestedAt(toLocalDateTime(rs.getTimestamp("withdraw_requested_at")));
        user.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        user.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        user.setDeleted(rs.getBoolean("is_deleted"));
        return user;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private void setNullableInt(PreparedStatement pstmt, int index, Integer value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.INTEGER);
        } else {
            pstmt.setInt(index, value);
        }
    }

    private Timestamp toTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
