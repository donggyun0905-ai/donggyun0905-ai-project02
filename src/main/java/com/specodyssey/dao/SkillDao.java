package com.specodyssey.dao;

import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SKILL 테이블 DAO.
 * 1주차에는 마스터 데이터만 시드로 채워두고 조회 전용으로만 쓴다.
 * 임베딩 매칭에 필요한 저장·갱신 메서드는 2주차에 그 기능을 만드는 담당자가 추가한다.
 */
public class SkillDao {

    // 매퍼(mapRow)가 읽는 컬럼만 가져온다 — SELECT *는 컬럼이 늘 때(특히 큰 TEXT) 안 쓰는 값까지 실어 나른다.
    private static final String COLUMNS =
            "id, skill_name, category, embedding_vector, embedding_model, embedded_at, " +
            "created_at, updated_at, is_deleted";

    public List<SkillDto> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SKILL WHERE is_deleted = FALSE ORDER BY skill_name";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<SkillDto> skills = new ArrayList<>();
            while (rs.next()) {
                skills.add(mapRow(rs));
            }
            return skills;
        }
    }

    public SkillDto findByName(String skillName) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SKILL WHERE skill_name = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, skillName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // FR-32 로드맵 단계 표시용 — related_skill_id로 기술명을 조회한다.
    public SkillDto findById(Long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM SKILL WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // TD-1 임베딩 배치(EmbeddingBackfillService)가 SKILL.skill_name을 벡터로 바꿔 저장할 때 쓴다.
    // embedding_vector는 float[] 768개를 JSON 배열 문자열로 직렬화한 것 — 컬럼 자체는 그냥 TEXT라
    // DAO는 포맷을 모른다(직렬화/역직렬화는 호출부 책임).
    public void updateEmbedding(Connection conn, Long id, String embeddingVectorJson, String embeddingModel,
            LocalDateTime embeddedAt) throws SQLException {
        String sql = "UPDATE SKILL SET embedding_vector = ?, embedding_model = ?, embedded_at = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, embeddingVectorJson);
            pstmt.setString(2, embeddingModel);
            pstmt.setTimestamp(3, embeddedAt == null ? null : Timestamp.valueOf(embeddedAt));
            pstmt.setLong(4, id);
            pstmt.executeUpdate();
        }
    }

    // 매칭용 메모리 캐시(SkillCatalog)가 다시 읽을지 판단하는 값 — SKILL·SKILL_ALIAS의 행 수·최대 id·최근 수정 시각.
    // 추가·삭제·임베딩 갱신(updated_at 자동 갱신) 중 하나라도 일어나면 값이 달라진다. 전체를 읽는 것보다 훨씬 가볍다.
    public String findMatchingDataStamp() throws SQLException {
        String sql = "SELECT (SELECT COUNT(*) FROM SKILL), (SELECT MAX(id) FROM SKILL), (SELECT MAX(updated_at) FROM SKILL), "
                + "(SELECT COUNT(*) FROM SKILL_ALIAS), (SELECT MAX(id) FROM SKILL_ALIAS), (SELECT MAX(updated_at) FROM SKILL_ALIAS)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            rs.next();
            StringBuilder stamp = new StringBuilder();
            for (int i = 1; i <= 6; i++) {
                stamp.append(rs.getString(i)).append('|');
            }
            return stamp.toString();
        }
    }

    private SkillDto mapRow(ResultSet rs) throws SQLException {
        SkillDto skill = new SkillDto();
        skill.setId(rs.getLong("id"));
        skill.setSkillName(rs.getString("skill_name"));
        skill.setCategory(rs.getString("category"));
        skill.setEmbeddingVector(rs.getString("embedding_vector"));
        skill.setEmbeddingModel(rs.getString("embedding_model"));
        skill.setEmbeddedAt(toLocalDateTime(rs.getTimestamp("embedded_at")));
        skill.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        skill.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        skill.setDeleted(rs.getBoolean("is_deleted"));
        return skill;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
