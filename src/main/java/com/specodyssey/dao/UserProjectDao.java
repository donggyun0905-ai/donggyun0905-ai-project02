package com.specodyssey.dao;

import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * USER_PROJECTS 테이블 DAO.
 * 관련 요구사항: FR-24
 */
public class UserProjectDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, user_id, title, description, tech_stack, start_date, end_date, " +
            "upgraded_from_project_id, repo_url, deploy_url, retrospective, team_size, my_role, created_at, " +
            "updated_at, is_deleted";

    public Long insert(UserProjectDto project) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return insert(conn, project);
        }
    }

    public Long insert(Connection conn, UserProjectDto project) throws SQLException {
        String sql = "INSERT INTO USER_PROJECTS " +
                "(user_id, title, description, tech_stack, start_date, end_date, upgraded_from_project_id, " +
                " repo_url, deploy_url, retrospective, team_size, my_role) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, project.getUserId());
            pstmt.setString(2, project.getTitle());
            pstmt.setString(3, project.getDescription());
            pstmt.setString(4, project.getTechStack());
            setNullableDate(pstmt, 5, project.getStartDate());
            setNullableDate(pstmt, 6, project.getEndDate());
            setNullableLong(pstmt, 7, project.getUpgradedFromProjectId());
            pstmt.setString(8, project.getRepoUrl());
            pstmt.setString(9, project.getDeployUrl());
            pstmt.setString(10, project.getRetrospective());
            if (project.getTeamSize() == null) {
                pstmt.setNull(11, Types.INTEGER);
            } else {
                pstmt.setInt(11, project.getTeamSize());
            }
            pstmt.setString(12, project.getMyRole());
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : null;
            }
        }
    }

    public List<UserProjectDto> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USER_PROJECTS WHERE user_id = ? AND is_deleted = FALSE ORDER BY start_date DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<UserProjectDto> projects = new ArrayList<>();
                while (rs.next()) {
                    projects.add(mapRow(rs));
                }
                return projects;
            }
        }
    }

    // CORE/ADVANCED SKILL 단계에서 "기존 프로젝트 업그레이드"를 선택했을 때, 그 프로젝트가 실제로
    // 본인 소유인지 확인하는 용도(RoadmapService.submitSkillProjectStep) — user_id를 조건에 넣어
    // 다른 사용자의 프로젝트를 업그레이드 대상으로 지정할 수 없게 막는다.
    public UserProjectDto findById(Connection conn, Long id, Long userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM USER_PROJECTS WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.setLong(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // 로드맵 화면이 "이전에 제출한 프로젝트"를 폼에 미리 채울 때 — 본인 것만 읽는다.
    public UserProjectDto findById(Long id, Long userId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return findById(conn, id, userId);
        }
    }

    // 팀 규모·본인 역할은 프로필 화면에서만 고친다 — 로드맵 제출(ProjectSubmissionService)도 update()를 쓰는데
    // 그 폼에는 이 칸이 없어서, update()에 넣으면 제출할 때마다 지워진다.
    public void updateTeamInfo(Connection conn, Long projectId, Long userId, Integer teamSize, String myRole)
            throws SQLException {
        String sql = "UPDATE USER_PROJECTS SET team_size = ?, my_role = ? WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (teamSize == null) {
                pstmt.setNull(1, Types.INTEGER);
            } else {
                pstmt.setInt(1, teamSize);
            }
            pstmt.setString(2, myRole);
            pstmt.setLong(3, projectId);
            pstmt.setLong(4, userId);
            pstmt.executeUpdate();
        }
    }

    public void update(Connection conn, UserProjectDto project, Long userId) throws SQLException {
        String sql = "UPDATE USER_PROJECTS SET title = ?, description = ?, tech_stack = ?, " +
                "start_date = ?, end_date = ?, repo_url = ?, deploy_url = ?, retrospective = ? " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, project.getTitle());
            pstmt.setString(2, project.getDescription());
            pstmt.setString(3, project.getTechStack());
            setNullableDate(pstmt, 4, project.getStartDate());
            setNullableDate(pstmt, 5, project.getEndDate());
            pstmt.setString(6, project.getRepoUrl());
            pstmt.setString(7, project.getDeployUrl());
            pstmt.setString(8, project.getRetrospective());
            pstmt.setLong(9, project.getId());
            pstmt.setLong(10, userId);
            pstmt.executeUpdate();
        }
    }

    // 프로젝트 "업데이트" 단계를 끝내면 마지막으로 손본 시각(updated_at)을 지금으로 — 다음 업데이트 주기가 여기서부터 다시 센다.
    public int touch(Connection conn, Long projectId, Long userId) throws SQLException {
        String sql = "UPDATE USER_PROJECTS SET updated_at = CURRENT_TIMESTAMP " +
                "WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, projectId);
            pstmt.setLong(2, userId);
            return pstmt.executeUpdate();
        }
    }

    public void delete(Connection conn, Long projectId, Long userId) throws SQLException {
        String sql = "UPDATE USER_PROJECTS SET is_deleted = TRUE WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, projectId);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    private UserProjectDto mapRow(ResultSet rs) throws SQLException {
        UserProjectDto project = new UserProjectDto();
        project.setId(rs.getLong("id"));
        project.setUserId(rs.getLong("user_id"));
        project.setTitle(rs.getString("title"));
        project.setDescription(rs.getString("description"));
        project.setTechStack(rs.getString("tech_stack"));
        java.sql.Date startDate = rs.getDate("start_date");
        project.setStartDate(startDate == null ? null : startDate.toLocalDate());
        java.sql.Date endDate = rs.getDate("end_date");
        project.setEndDate(endDate == null ? null : endDate.toLocalDate());
        project.setUpgradedFromProjectId(rs.getObject("upgraded_from_project_id", Long.class));
        project.setRepoUrl(rs.getString("repo_url"));
        project.setDeployUrl(rs.getString("deploy_url"));
        project.setRetrospective(rs.getString("retrospective"));
        project.setTeamSize(rs.getObject("team_size", Integer.class));
        project.setMyRole(rs.getString("my_role"));
        project.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        project.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        project.setDeleted(rs.getBoolean("is_deleted"));
        return project;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.BIGINT);
        } else {
            pstmt.setLong(index, value);
        }
    }

    private void setNullableDate(PreparedStatement pstmt, int index, LocalDate date) throws SQLException {
        if (date == null) {
            pstmt.setNull(index, Types.DATE);
        } else {
            pstmt.setDate(index, java.sql.Date.valueOf(date));
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
