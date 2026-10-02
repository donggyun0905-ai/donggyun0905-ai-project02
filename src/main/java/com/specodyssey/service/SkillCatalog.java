package com.specodyssey.service;

import com.google.gson.Gson;
import com.specodyssey.dao.SkillAliasDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.SkillAliasDto;
import com.specodyssey.dto.SkillDto;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 스킬 매칭(FuzzyNameMatcher·EmbeddingMatcher)이 쓰는 SKILL·SKILL_ALIAS·임베딩 벡터를 메모리에 올려 두고 같이 쓴다.
 * 관련 요구사항: TD-1, NFR-1(응답 속도)
 *
 * 매칭할 때마다 전체를 다시 읽으면(벡터 JSON 약 1.5MB) 입력 하나에 수 초가 걸려서 만들었다.
 * 매번 가벼운 조회(SkillDao.findMatchingDataStamp)로 데이터가 바뀌었는지만 확인하고, 바뀌었을 때만 다시 읽는다 —
 * 그래서 시드 추가·백필·테스트 픽스처가 바로 반영된다. 벡터 JSON은 읽을 때 한 번만 float[]로 바꾼다.
 */
public final class SkillCatalog {

    /** 임베딩 벡터가 있는 스킬 하나 */
    public record SkillVector(Long skillId, float[] vector) {
    }

    /** 한 시점의 매칭 데이터. 읽기 전용으로 쓴다. */
    public record Snapshot(List<SkillDto> skills, List<SkillAliasDto> aliases, List<SkillVector> vectors, String stamp) {
    }

    private static final Gson GSON = new Gson();
    private static final SkillDao SKILL_DAO = new SkillDao();
    private static final SkillAliasDao SKILL_ALIAS_DAO = new SkillAliasDao();

    private static volatile Snapshot cached;

    private SkillCatalog() {
    }

    /** 최신 매칭 데이터. DB가 바뀌지 않았으면 메모리에 있는 것을 그대로 돌려준다. */
    public static Snapshot current() throws SQLException {
        String stamp = SKILL_DAO.findMatchingDataStamp();
        Snapshot snapshot = cached;
        if (snapshot != null && snapshot.stamp().equals(stamp)) {
            return snapshot;
        }
        synchronized (SkillCatalog.class) {
            snapshot = cached;
            if (snapshot == null || !snapshot.stamp().equals(stamp)) {
                snapshot = load(stamp);
                cached = snapshot;
            }
            return snapshot;
        }
    }

    /** 다음 current() 호출 때 무조건 다시 읽게 한다 (백필 직후 등) */
    public static void refresh() {
        cached = null;
    }

    private static Snapshot load(String stamp) throws SQLException {
        List<SkillDto> skills = SKILL_DAO.findAll();
        List<SkillAliasDto> aliases = SKILL_ALIAS_DAO.findAll();
        List<SkillVector> vectors = new ArrayList<>();
        for (SkillDto skill : skills) {
            if (skill.getEmbeddingVector() != null) {
                vectors.add(new SkillVector(skill.getId(), GSON.fromJson(skill.getEmbeddingVector(), float[].class)));
            }
        }
        return new Snapshot(List.copyOf(skills), List.copyOf(aliases), List.copyOf(vectors), stamp);
    }
}
