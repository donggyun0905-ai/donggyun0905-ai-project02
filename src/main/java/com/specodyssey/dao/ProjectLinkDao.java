package com.specodyssey.dao;

import com.specodyssey.dto.ProjectLinkDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PROJECT_LINK 테이블 DAO — 프로젝트의 기타 링크(블로그 글, 발표 영상 등).
 * 수정은 "프로젝트의 링크를 통째로 바꾸는" 한 동작(replaceForProject)뿐이다: 기존 줄을 is_deleted로 지우고 새로 넣는다.
 * 호출하는 쪽이 프로젝트가 본인 것인지 먼저 확인해야 한다(이 DAO는 user_id를 모른다).
 */
public class ProjectLinkDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS = "id, project_id, label, url, sort_order, created_at, updated_at, is_deleted";

    public List<ProjectLinkDto> findByProjectId(Long projectId) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            return findByProjectId(conn, projectId);
        }
    }

    public List<ProjectLinkDto> findByProjectId(Connection conn, Long projectId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM PROJECT_LINK WHERE project_id = ? AND is_deleted = FALSE " +
                "ORDER BY sort_order, id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, projectId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<ProjectLinkDto> links = new ArrayList<>();
                while (rs.next()) {
                    links.add(mapRow(rs));
                }
                return links;
            }
        }
    }

    /** 프로젝트 여러 개의 링크를 한 번에(프로필·공유 화면에서 프로젝트마다 쿼리하지 않으려고). 링크가 없는 프로젝트는 키가 없다. */
    public Map<Long, List<ProjectLinkDto>> findByProjectIds(Collection<Long> projectIds) throws SQLException {
        Map<Long, List<ProjectLinkDto>> byProject = new HashMap<>();
        if (projectIds.isEmpty()) {
            return byProject;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(projectIds.size(), "?"));
        String sql = "SELECT " + COLUMNS + " FROM PROJECT_LINK WHERE project_id IN (" + placeholders + ") " +
                "AND is_deleted = FALSE ORDER BY project_id, sort_order, id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int i = 1;
            for (Long id : projectIds) {
                pstmt.setLong(i++, id);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ProjectLinkDto link = mapRow(rs);
                    byProject.computeIfAbsent(link.getProjectId(), k -> new ArrayList<>()).add(link);
                }
            }
        }
        return byProject;
    }

    /** 프로젝트의 링크를 주어진 목록으로 통째로 바꾼다(빈 목록이면 전부 지운다). 입력 순서가 sort_order가 된다. */
    public void replaceForProject(Connection conn, Long projectId, List<ProjectLinkDto> links) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement(
                "UPDATE PROJECT_LINK SET is_deleted = TRUE WHERE project_id = ? AND is_deleted = FALSE")) {
            del.setLong(1, projectId);
            del.executeUpdate();
        }
        String sql = "INSERT INTO PROJECT_LINK (project_id, label, url, sort_order) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ins = conn.prepareStatement(sql)) {
            int order = 0;
            for (ProjectLinkDto link : links) {
                ins.setLong(1, projectId);
                ins.setString(2, link.getLabel());
                ins.setString(3, link.getUrl());
                ins.setInt(4, order++);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private ProjectLinkDto mapRow(ResultSet rs) throws SQLException {
        ProjectLinkDto link = new ProjectLinkDto();
        link.setId(rs.getLong("id"));
        link.setProjectId(rs.getLong("project_id"));
        link.setLabel(rs.getString("label"));
        link.setUrl(rs.getString("url"));
        link.setSortOrder(rs.getInt("sort_order"));
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        link.setCreatedAt(created == null ? null : created.toLocalDateTime());
        link.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        link.setDeleted(rs.getBoolean("is_deleted"));
        return link;
    }
}
