package com.specodyssey.service;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 프로필(기본정보 + 스펙 + 프로젝트 + 기술스택) 조회 · 수정.
 * 관련 요구사항: FR-21 · 22 · 23 · 24 · 25 · 37
 * 스펙/프로젝트/스킬 자식 테이블이 바뀔 때마다 USERS.profile_updated_at을
 * 같은 트랜잭션에서 갱신한다 — FR-37 재분석 트리거 판단 기준이기 때문이다.
 */
public class ProfileService {

    public static class DuplicateSkillException extends Exception {
        public DuplicateSkillException(String message) {
            super(message);
        }
    }

    private final UserDao userDao = new UserDao();
    private final UserSpecDao userSpecDao = new UserSpecDao();
    private final UserProjectDao userProjectDao = new UserProjectDao();
    private final UserSkillDao userSkillDao = new UserSkillDao();

    public List<UserSpecDto> getSpecs(Long userId) throws SQLException {
        return userSpecDao.findByUserId(userId);
    }

    public List<UserProjectDto> getProjects(Long userId) throws SQLException {
        return userProjectDao.findByUserId(userId);
    }

    public List<UserSkillDto> getSkills(Long userId) throws SQLException {
        return userSkillDao.findByUserId(userId);
    }

    // FR-21 · 22 기본정보 수정 (email은 기존 값을 보존하고 넘어온 값으로만 덮어씀)
    public void updateBasicInfo(Long userId, String email, String major, String grade,
                                 String interestField, Long desiredJobId, String desiredJobStatus)
            throws SQLException {
        UserDto user = userDao.findById(userId);
        if (user == null) {
            return;
        }
        user.setEmail(email);
        user.setMajor(major);
        user.setGrade(grade);
        user.setInterestField(interestField);
        user.setDesiredJobId(desiredJobId);
        user.setDesiredJobStatus(desiredJobStatus);
        user.setProfileUpdatedAt(LocalDateTime.now());
        userDao.updateProfile(user);
    }

    public Long addSpec(Long userId, UserSpecDto spec) throws SQLException {
        spec.setUserId(userId);
        return runInTransaction(userId, conn -> userSpecDao.insert(conn, spec));
    }

    public void updateSpec(Long userId, UserSpecDto spec) throws SQLException {
        runInTransaction(userId, conn -> {
            userSpecDao.update(conn, spec, userId);
            return null;
        });
    }

    public void deleteSpec(Long userId, Long specId) throws SQLException {
        runInTransaction(userId, conn -> {
            userSpecDao.delete(conn, specId, userId);
            return null;
        });
    }

    public Long addProject(Long userId, UserProjectDto project) throws SQLException {
        project.setUserId(userId);
        return runInTransaction(userId, conn -> userProjectDao.insert(conn, project));
    }

    public void updateProject(Long userId, UserProjectDto project) throws SQLException {
        runInTransaction(userId, conn -> {
            userProjectDao.update(conn, project, userId);
            return null;
        });
    }

    public void deleteProject(Long userId, Long projectId) throws SQLException {
        runInTransaction(userId, conn -> {
            userProjectDao.delete(conn, projectId, userId);
            return null;
        });
    }

    public Long addSkill(Long userId, UserSkillDto skill) throws SQLException, DuplicateSkillException {
        skill.setUserId(userId);
        if (userSkillDao.existsActiveRawInput(userId, skill.getRawInput())) {
            throw new DuplicateSkillException("이미 등록된 기술입니다: " + skill.getRawInput());
        }
        return runInTransaction(userId, conn -> userSkillDao.insert(conn, skill));
    }

    public void updateSkill(Long userId, UserSkillDto skill) throws SQLException, DuplicateSkillException {
        if (userSkillDao.existsActiveRawInput(userId, skill.getRawInput(), skill.getId())) {
            throw new DuplicateSkillException("이미 등록된 기술입니다: " + skill.getRawInput());
        }
        runInTransaction(userId, conn -> {
            userSkillDao.update(conn, skill, userId);
            return null;
        });
    }

    public void deleteSkill(Long userId, Long userSkillId) throws SQLException {
        runInTransaction(userId, conn -> {
            userSkillDao.delete(conn, userSkillId, userId);
            return null;
        });
    }

    private <T> T runInTransaction(Long userId, SqlFunction<T> action) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.apply(conn);
                userDao.touchProfileUpdatedAt(conn, userId);
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection conn) throws SQLException;
    }
}
