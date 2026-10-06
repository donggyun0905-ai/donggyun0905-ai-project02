package com.specodyssey.service;

import com.specodyssey.dao.LevelTierDao;
import com.specodyssey.dto.LevelTierDto;

import java.sql.SQLException;
import java.util.List;

/**
 * 등급 구간(LEVEL_TIER)을 읽어 CACHE_MILLIS 동안 메모리에 둔다 — SCORING_RULE을 다루는
 * {@link ScoringRules}와 같은 방식이다.
 *
 * 등급 구간은 시드로 관리하는 마스터성 데이터인데(sql/02_seed.sql · 04_seed_extended.sql) 화면 한 번에
 * 여러 곳에서 전체를 다시 읽고 있었다 — 헤더 배지(SessionFilter)에서 2번, "한눈에 보기" 위젯에서 2번,
 * 대시보드 등급 카드에서 2~3번(2026-10-06 성능 점검). 같은 테이블을 한 요청에 6번까지 읽던 것을 없앤다.
 *
 * DAO는 그대로 매번 DB를 읽는다 — 캐시는 이 서비스 계층에만 둔다. 등급 구간을 직접 바꿨으면(시드 재적용·
 * DB 직접 수정) 최대 CACHE_MILLIS 뒤에 반영된다. 앱에서 LEVEL_TIER에 쓰는 화면은 아직 없다.
 */
final class LevelTiers {

    private static final long CACHE_MILLIS = 60_000L;

    private static final LevelTierDao DAO = new LevelTierDao();

    private static volatile List<LevelTierDto> cached = List.of();
    private static volatile long loadedAt;

    private LevelTiers() {
    }

    /** min_score 오름차순. 돌려주는 목록은 수정하지 말 것 — 호출하는 모든 곳이 같은 목록을 본다. */
    static List<LevelTierDto> all() throws SQLException {
        long now = System.currentTimeMillis();
        if (loadedAt != 0L && now - loadedAt < CACHE_MILLIS) {
            return cached;
        }
        synchronized (LevelTiers.class) {
            if (loadedAt != 0L && System.currentTimeMillis() - loadedAt < CACHE_MILLIS) {
                return cached;
            }
            cached = List.copyOf(DAO.findAll());
            loadedAt = System.currentTimeMillis();
            return cached;
        }
    }
}
