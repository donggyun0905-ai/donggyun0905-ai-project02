package com.specodyssey.dao;

import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 같은 직무를 목표로 한 사람들의 보유 기술 — 함께 익힌 기술 추천의 입력 (2026-10-08).
 * 관련 요구사항: FR-114 다음에 할 일
 *
 * 개인을 특정할 수 있는 값(로그인 아이디·이름)은 돌려주지 않는다. user_id는 "같은 사람인지"만
 * 구분하면 되므로 서비스 안에서만 쓰고 화면에는 사람 수(집계)만 나간다.
 *
 * 제외 대상: 본인, 탈퇴·논리 삭제된 계정, 테스트 계정(USERS.is_test), 기술과 연결되지 않은 입력(skill_id IS NULL).
 */
public class SkillCooccurrenceDao {

    /**
     * 같은 목표 직무를 가진 다른 사람들의 보유 기술. key = user_id(익명), value = skill_id 집합.
     * 사람 수가 많아도 기술 몇 개씩이라 전부 읽어도 가볍다 — 자카드 계산은 집합 전체가 필요하다.
     */
    public Map<Long, Set<Long>> peerSkillsByJob(Long jobId, Long excludeUserId) throws SQLException {
        String sql = "SELECT us.user_id, us.skill_id "
                + "FROM USER_SKILLS us "
                + "JOIN USERS u ON u.id = us.user_id "
                + "WHERE u.desired_job_id = ? AND u.id <> ? "
                + "  AND u.is_deleted = FALSE AND u.withdraw_requested_at IS NULL AND u.is_test = FALSE "
                + "  AND us.is_deleted = FALSE AND us.skill_id IS NOT NULL "
                + "ORDER BY us.user_id, us.skill_id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, jobId);
            pstmt.setLong(2, excludeUserId);
            try (ResultSet rs = pstmt.executeQuery()) {
                Map<Long, Set<Long>> byUser = new LinkedHashMap<>();
                while (rs.next()) {
                    byUser.computeIfAbsent(rs.getLong("user_id"), key -> new LinkedHashSet<>())
                            .add(rs.getLong("skill_id"));
                }
                return byUser;
            }
        }
    }

    /** 내가 가진 기술 — 자카드의 한쪽 집합. SKILL과 연결된 것만(raw 입력만 있는 건 비교할 수 없다). */
    public Set<Long> mySkillIds(Long userId) throws SQLException {
        String sql = "SELECT DISTINCT skill_id FROM USER_SKILLS "
                + "WHERE user_id = ? AND is_deleted = FALSE AND skill_id IS NOT NULL";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                Set<Long> ids = new LinkedHashSet<>();
                while (rs.next()) {
                    ids.add(rs.getLong("skill_id"));
                }
                return ids;
            }
        }
    }
}
