package com.specodyssey.service;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.PasswordUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 회원 관리(2026-10-06). 기존 프로필 수정(UserDao.updateProfile, FR-21·22)을 그대로 쓴다 —
 * 본인이 고치나 관리자가 고치나 바뀌는 컬럼은 같다. 비밀번호 재설정·탈퇴 처리/취소만 관리자 전용으로 더한다.
 */
public class AdminUserService {

    private static final int SEARCH_LIMIT = 50;

    private final UserDao userDao = new UserDao();

    public List<UserDto> search(String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return userDao.searchForAdmin(keyword.trim(), SEARCH_LIMIT);
    }

    public UserDto find(Long userId) throws SQLException {
        return userDao.findByIdIncludingDeleted(userId);
    }

    // FR-21·22와 같은 컬럼만 바꾼다 — 로그인 아이디·비밀번호·user_type은 여기서 안 건드린다.
    public void updateProfile(Long userId, String name, Integer age, String careerStatus, String email,
            String major, String grade, String interestField, Long desiredJobId, String desiredJobStatus)
            throws SQLException {
        UserDto user = userDao.findByIdIncludingDeleted(userId);
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
        user.setName(name);
        user.setAge(age);
        user.setCareerStatus(careerStatus);
        user.setEmail(email);
        user.setMajor(major);
        user.setGrade(grade);
        user.setInterestField(interestField);
        user.setDesiredJobId(desiredJobId);
        user.setDesiredJobStatus(desiredJobStatus);
        user.setProfileUpdatedAt(LocalDateTime.now());
        userDao.updateProfile(user);
    }

    // 본인 확인(로그인 비밀번호) 없이 관리자가 새 비밀번호를 바로 심어준다 — 계정 찾기 지원용.
    public void resetPassword(Long userId, String newPassword) throws SQLException {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");
        }
        userDao.updatePasswordHash(userId, PasswordUtil.hash(newPassword));
    }

    public void softDelete(Long userId) throws SQLException {
        userDao.softDelete(userId);
    }

    // 탈퇴 유예(FR-13, UserService.WITHDRAWAL_GRACE_DAYS와 같은 기준) 중인 계정을 관리자가 먼저
    // 복구해줄 때 — 사용자가 직접 로그인해도 같은 일이 일어난다.
    public boolean cancelWithdrawal(Long userId) throws SQLException {
        LocalDateTime graceCutoff = LocalDateTime.now().minusDays(UserService.WITHDRAWAL_GRACE_DAYS);
        return userDao.cancelWithdrawal(userId, graceCutoff);
    }
}
