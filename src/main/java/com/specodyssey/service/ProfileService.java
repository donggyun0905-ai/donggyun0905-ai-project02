package com.specodyssey.service;

import com.specodyssey.dao.JobAliasDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserProjectDao;
import com.specodyssey.dao.UserSkillDao;
import com.specodyssey.dao.UserSpecDao;
import com.specodyssey.dto.JobAliasDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.dto.UserSkillDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.util.EditDistanceUtil;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final JobDao jobDao = new JobDao();
    private final JobAliasDao jobAliasDao = new JobAliasDao();

    // 검색창에 입력한 텍스트로 찾아낸 직무. exact=false면 정확히 일치하는 건 없어서 오타·표기
    // 차이를 감안해 편집 거리 기준으로 가장 가까운 직무를 대신 찾은 것 — 이 경우 바로 저장하지
    // 않고 화면에서 "이 직무 맞나요?" 확인부터 받는다(사용자 요청, 2026-09-29: 희망 직무는
    // 격차분석·로드맵을 좌우하는 값이라 잘못 자동교정되면 위험하다). aliasNames는 그 확인 화면에서
    // "이 직무가 뭘 하는 직무인지" 판단할 근거로 보여주는 별칭 목록(JOB에 설명 컬럼이 없어서 대신 씀).
    public static final class JobMatch {
        private final JobDto job;
        private final boolean exact;
        private final List<String> aliasNames;

        public JobMatch(JobDto job, boolean exact, List<String> aliasNames) {
            this.job = job;
            this.exact = exact;
            this.aliasNames = aliasNames;
        }

        public JobDto getJob() {
            return job;
        }

        public boolean isExact() {
            return exact;
        }

        public List<String> getAliasNames() {
            return aliasNames;
        }
    }

    // 희망 직무 검색창에 입력한 텍스트를 실제 JOB으로 풀어낸다. 사용자가 정식 명칭을 몰라도
    // ("데이터 엔지니어"를 "데이터 프로그래머"로 알고 있는 경우 등) JOB_ALIAS에 등록된 별칭이면
    // 찾을 수 있게 정식 명칭 → 별칭 순으로 조회한다 (팀 시드: sql/04_seed_skills.sql, 54개 별칭).
    // 그마저도 정확히 일치하는 게 없으면(오타, 띄어쓰기 차이 등) 편집 거리로 가장 가까운 걸
    // 대신 골라준다 — 완전한 임베딩 매칭 전까지의 가벼운 보완책 (사용자 요청, 2026-09-29).
    public JobMatch resolveJobQuery(String query) throws SQLException {
        if (query == null || query.isBlank()) {
            return null;
        }
        String trimmed = query.trim();
        JobDto byName = jobDao.findByName(trimmed);
        if (byName != null) {
            return new JobMatch(byName, true, List.of());
        }
        JobAliasDto alias = jobAliasDao.findByAliasName(trimmed);
        if (alias != null) {
            JobDto job = jobDao.findById(alias.getJobId());
            if (job != null) {
                return new JobMatch(job, true, List.of());
            }
        }
        return fuzzyMatchJob(trimmed);
    }

    private JobMatch fuzzyMatchJob(String query) throws SQLException {
        String normalizedQuery = normalizeForMatch(query);
        List<JobAliasDto> allAliases = jobAliasDao.findAll();
        JobDto bestJob = null;
        int bestDistance = Integer.MAX_VALUE;

        for (JobDto job : jobDao.findAll()) {
            int distance = EditDistanceUtil.distance(normalizedQuery, normalizeForMatch(job.getJobName()));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestJob = job;
            }
        }
        for (JobAliasDto alias : allAliases) {
            int distance = EditDistanceUtil.distance(normalizedQuery, normalizeForMatch(alias.getAliasName()));
            if (distance < bestDistance) {
                JobDto job = jobDao.findById(alias.getJobId());
                if (job != null) {
                    bestDistance = distance;
                    bestJob = job;
                }
            }
        }

        if (bestJob == null) {
            return null;
        }
        // 입력 길이의 40%보다 많이 다르면 아예 다른 직무일 가능성이 커서 자동 교정하지 않는다.
        int threshold = Math.max(1, (int) Math.ceil(normalizedQuery.length() * 0.4));
        if (bestDistance > threshold) {
            return null;
        }
        // JOB에 설명 컬럼이 없어서, "이 직무 맞나요?" 확인 화면에서 무슨 일 하는 직무인지 감을
        // 잡을 수 있게 그 직무에 등록된 별칭들을 같이 보여준다.
        Long bestJobId = bestJob.getId();
        List<String> aliasNames = new ArrayList<>();
        for (JobAliasDto alias : allAliases) {
            if (bestJobId.equals(alias.getJobId())) {
                aliasNames.add(alias.getAliasName());
            }
        }
        return new JobMatch(bestJob, false, aliasNames);
    }

    private String normalizeForMatch(String s) {
        return s == null ? "" : s.trim().toLowerCase().replace(" ", "");
    }

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

    // FR-37: 트랜잭션 커밋 전에 profile_updated_at을 같이 갱신 — 재분석 트리거 판단 기준이기 때문이다.
    // 트랜잭션 자체(커넥션·커밋·롤백)는 공용 TransactionUtil이 책임진다.
    private <T> T runInTransaction(Long userId, TransactionUtil.SqlFunction<T> action) throws SQLException {
        return TransactionUtil.runInTransaction(conn -> {
            T result = action.apply(conn);
            userDao.touchProfileUpdatedAt(conn, userId);
            return result;
        });
    }
}
