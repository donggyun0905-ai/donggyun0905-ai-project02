package com.specodyssey.service;

import com.google.gson.Gson;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.LocalEmbedder;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SKILL.embedding_vector를 채우는 배치 (TD-1). SKILL 마스터가 163개뿐이고 자주 안 바뀌므로
 * 상시 서비스가 아니라 필요할 때(신규 스킬 추가 등) 한 번 돌리는 유틸이다 — 관리 화면은 없고
 * 지금은 직접 이 클래스를 호출해서 돌린다(2026-09-30, 임베딩 마무리 담당자 작업).
 * 이미 embedding_vector가 있는 행은 다시 계산하지 않는다(모델이 바뀌면 강제로 다시 돌릴 방법이
 * 필요하지만, 그건 아직 모델 교체 자체가 일어난 적이 없어서 지금은 만들지 않는다).
 */
public class EmbeddingBackfillService {

    private static final Gson GSON = new Gson();

    private final SkillDao skillDao = new SkillDao();

    /** 커맨드라인에서 직접 돌리는 진입점 — 관리 화면이 없어서 이 클래스를 그대로 실행한다. */
    public static void main(String[] args) throws Exception {
        int updated = new EmbeddingBackfillService().backfillMissingEmbeddings();
        System.out.println("임베딩을 새로 채운 SKILL 행 수: " + updated);
    }

    /** embedding_vector가 비어 있는 SKILL만 골라 계산해서 저장한다. 몇 건 채웠는지 반환한다. */
    public int backfillMissingEmbeddings() throws Exception {
        List<SkillDto> skills = skillDao.findAll();
        int updated = 0;
        try (LocalEmbedder embedder = LocalEmbedder.fromConfig();
             Connection conn = DBUtil.getConnection()) {
            for (SkillDto skill : skills) {
                if (skill.getEmbeddingVector() != null) {
                    continue;
                }
                float[] vector = embedder.embed(skill.getSkillName());
                String json = GSON.toJson(vector);
                skillDao.updateEmbedding(conn, skill.getId(), json, LocalEmbedder.MODEL_NAME, LocalDateTime.now());
                updated++;
            }
        }
        return updated;
    }
}
