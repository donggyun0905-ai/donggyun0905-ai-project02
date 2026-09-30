package com.specodyssey.dao;

import com.specodyssey.dto.DailyMissionViewDto;
import com.specodyssey.dto.ProblemDto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 일일 미션(외부 링크 추천 문제) 배정·조회·제출 전용 DAO.
 * 관련 요구사항: FR-51 일일 미션, FR-52 난이도 조정, FR-53 완료 체크, TD-3 코테 문제 소스
 * 기존 ProblemDao/UserDailyMissionDao는 건드리지 않고, 미션 화면에 필요한 조회·저장만 여기에 모았다.
 */
public class MissionDao {

    // FR-52 사용자의 현재 등급이 감당하는 문제 레벨 범위 [min, max]. 점수 요약이 없으면 null.
    public int[] findUserLevelRange(Connection conn, Long userId) throws SQLException {
        String sql = "SELECT t.problem_level_min, t.problem_level_max FROM USER_SCORE_SUMMARY s " +
                "JOIN LEVEL_TIER t ON t.id = s.current_tier_id " +
                "WHERE s.user_id = ? AND s.is_deleted = FALSE AND t.is_deleted = FALSE";
        return queryRange(conn, sql, userId);
    }

    // 등급이 아직 없는 사용자의 기본값 — 가장 낮은 등급의 범위
    public int[] findLowestTierLevelRange(Connection conn) throws SQLException {
        String sql = "SELECT problem_level_min, problem_level_max FROM LEVEL_TIER " +
                "WHERE is_deleted = FALSE ORDER BY min_score LIMIT 1";
        return queryRange(conn, sql, null);
    }

    private int[] queryRange(Connection conn, String sql, Long userId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (userId != null) {
                pstmt.setLong(1, userId);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? new int[]{rs.getInt(1), rs.getInt(2)} : null;
            }
        }
    }

    /** 목표 직무 — 대표(없으면 최신) 활성 로드맵의 격차 분석 직무. 없으면 프로필의 희망 직무. */
    public static class TargetJob {
        private final String jobName;
        private final String jobCategory;

        TargetJob(String jobName, String jobCategory) {
            this.jobName = jobName;
            this.jobCategory = jobCategory;
        }

        public String getJobName() {
            return jobName;
        }

        public String getJobCategory() {
            return jobCategory;
        }
    }

    // 본인 것만 — 항상 세션의 userId로 부른다. 목표 직무가 없으면 null.
    public TargetJob findTargetJob(Connection conn, Long userId) throws SQLException {
        String roadmapSql = "SELECT j.job_name, j.job_category FROM ROADMAP r " +
                "JOIN GAP_ANALYSIS g ON g.id = r.gap_analysis_id " +
                "JOIN JOB j ON j.id = g.job_id " +
                "WHERE r.user_id = ? AND r.is_active = TRUE AND r.is_deleted = FALSE " +
                "AND g.is_deleted = FALSE AND j.is_deleted = FALSE " +
                "ORDER BY r.is_primary DESC, r.created_at DESC, r.id DESC LIMIT 1";
        TargetJob job = queryTargetJob(conn, roadmapSql, userId);
        if (job != null) {
            return job;
        }
        String desiredSql = "SELECT j.job_name, j.job_category FROM USERS u " +
                "JOIN JOB j ON j.id = u.desired_job_id " +
                "WHERE u.id = ? AND u.is_deleted = FALSE AND j.is_deleted = FALSE";
        return queryTargetJob(conn, desiredSql, userId);
    }

    private TargetJob queryTargetJob(Connection conn, String sql, Long userId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? new TargetJob(rs.getString("job_name"), rs.getString("job_category")) : null;
            }
        }
    }

    // 지금까지 이 사용자에게 배정한 적 없는 외부 문제를 무작위로 — 같은 문제가 반복 배정되지 않게 한다
    // category가 null이면 유형을 가리지 않는다
    public List<ProblemDto> findAssignableProblems(Connection conn, Long userId, String category,
                                                   int minLevel, int maxLevel, int limit) throws SQLException {
        String sql = "SELECT p.id, p.title, p.difficulty_level, p.external_url FROM PROBLEM p " +
                "WHERE p.source_type = 'EXTERNAL_LINK' AND p.is_deleted = FALSE " +
                "AND (? IS NULL OR p.category = ?) " +
                "AND p.difficulty_level BETWEEN ? AND ? " +
                "AND p.id NOT IN (SELECT m.problem_id FROM USER_DAILY_MISSION m " +
                "                 WHERE m.user_id = ? AND m.is_deleted = FALSE) " +
                "ORDER BY RAND() LIMIT ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, category);
            pstmt.setString(2, category);
            pstmt.setInt(3, minLevel);
            pstmt.setInt(4, maxLevel);
            pstmt.setLong(5, userId);
            pstmt.setInt(6, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ProblemDto> problems = new ArrayList<>();
                while (rs.next()) {
                    ProblemDto problem = new ProblemDto();
                    problem.setId(rs.getLong("id"));
                    problem.setTitle(rs.getString("title"));
                    problem.setDifficultyLevel(rs.getObject("difficulty_level", Integer.class));
                    problem.setExternalUrl(rs.getString("external_url"));
                    problems.add(problem);
                }
                return problems;
            }
        }
    }

    // "정답 입력하기" 화면용 미션 1건 — user_id 조건으로 본인 미션만 찾는다. 없거나 남의 것이면 null.
    public DailyMissionViewDto findMissionById(Connection conn, Long userId, Long missionId) throws SQLException {
        String sql = "SELECT m.id AS mission_id, m.problem_id, m.is_completed, m.is_correct, " +
                "m.submitted_code, m.submitted_language, " +
                "p.title, p.difficulty_level, p.category, p.external_url " +
                "FROM USER_DAILY_MISSION m JOIN PROBLEM p ON p.id = m.problem_id " +
                "WHERE m.id = ? AND m.user_id = ? AND m.is_deleted = FALSE AND p.is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, missionId);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                DailyMissionViewDto view = new DailyMissionViewDto();
                view.setMissionId(rs.getLong("mission_id"));
                view.setProblemId(rs.getLong("problem_id"));
                view.setTitle(rs.getString("title"));
                view.setDifficultyLevel(rs.getObject("difficulty_level", Integer.class));
                view.setCategory(rs.getString("category"));
                view.setExternalUrl(rs.getString("external_url"));
                view.setCompleted(rs.getBoolean("is_completed"));
                view.setCorrect(rs.getObject("is_correct", Boolean.class));
                view.setSubmittedCode(rs.getString("submitted_code"));
                view.setSubmittedLanguage(rs.getString("submitted_language"));
                return view;
            }
        }
    }

    // FR-53 컴파일 확인을 통과한 풀이 코드 저장 + 완료 처리. 다시 제출하면 코드는 덮어쓰고 첫 완료 시각은 유지한다.
    // 정답 채점은 하지 않으므로 is_correct는 건드리지 않는다. 반영된 행 수(본인 미션이 아니면 0)를 돌려준다.
    public int saveSubmission(Connection conn, Long userId, Long missionId, String language, String code,
                              LocalDateTime now) throws SQLException {
        // 앞서 "실패"로 표시했더라도 코드를 제출했다면 실패 표시(is_correct = FALSE)는 거둔다
        String sql = "UPDATE USER_DAILY_MISSION SET submitted_code = ?, submitted_language = ?, submitted_at = ?, " +
                "is_completed = TRUE, completed_at = COALESCE(completed_at, ?), is_correct = NULL " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.setString(2, language);
            pstmt.setTimestamp(3, Timestamp.valueOf(now));
            pstmt.setTimestamp(4, Timestamp.valueOf(now));
            pstmt.setLong(5, missionId);
            pstmt.setLong(6, userId);
            return pstmt.executeUpdate();
        }
    }

    // FR-53 "실패" — 못 푼 문제로 끝낸다(완료 + 오답). 첫 완료 시각은 유지. 반영된 행 수(본인 미션이 아니면 0).
    public int markFailed(Connection conn, Long userId, Long missionId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE USER_DAILY_MISSION SET is_completed = TRUE, is_correct = FALSE, " +
                "completed_at = COALESCE(completed_at, ?) " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(now));
            pstmt.setLong(2, missionId);
            pstmt.setLong(3, userId);
            return pstmt.executeUpdate();
        }
    }

    // 본인의 오늘 배정분만 — 다른 사용자 id를 받지 않고 항상 세션의 userId로 부른다
    public List<DailyMissionViewDto> findMissionsByDate(Connection conn, Long userId, LocalDate date)
            throws SQLException {
        String sql = "SELECT m.id AS mission_id, m.problem_id, m.is_completed, m.is_correct, " +
                "p.title, p.difficulty_level, p.category, p.external_url " +
                "FROM USER_DAILY_MISSION m JOIN PROBLEM p ON p.id = m.problem_id " +
                "WHERE m.user_id = ? AND m.assigned_date = ? AND m.is_deleted = FALSE AND p.is_deleted = FALSE " +
                "ORDER BY m.id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = pstmt.executeQuery()) {
                List<DailyMissionViewDto> missions = new ArrayList<>();
                while (rs.next()) {
                    DailyMissionViewDto view = new DailyMissionViewDto();
                    view.setMissionId(rs.getLong("mission_id"));
                    view.setProblemId(rs.getLong("problem_id"));
                    view.setTitle(rs.getString("title"));
                    view.setDifficultyLevel(rs.getObject("difficulty_level", Integer.class));
                    view.setCategory(rs.getString("category"));
                    view.setExternalUrl(rs.getString("external_url"));
                    view.setCompleted(rs.getBoolean("is_completed"));
                    view.setCorrect(rs.getObject("is_correct", Boolean.class));
                    missions.add(view);
                }
                return missions;
            }
        }
    }
}
