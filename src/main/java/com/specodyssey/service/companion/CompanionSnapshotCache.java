package com.specodyssey.service.companion;

import com.specodyssey.dao.CompanionFingerprintDao;
import com.specodyssey.util.AppClock;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 데스크톱 캐릭터의 "지금 할 말" 재사용 (2026-10-08) — 이미 DB에서 읽어 만든 결과를 바뀐 게 없으면 다시 쓴다.
 *
 * 캐릭터는 사이트에서 한 일이 10초 안에 보이도록 10초마다 묻는다. 매번 다시 만들면 한 명당 1분에 DB를 수십 번 읽는다.
 * 그래서 가벼운 질의 하나(CompanionFingerprintDao)로 바뀐 게 있는지만 보고, 같으면 직전 결과를 돌려준다.
 * 날짜·시각(저녁 경고, D-day 남은 날)이 바뀌어도 다시 만들고, 트렌드처럼 그 사용자 행에 안 걸리는 것도
 * 너무 오래되지 않게 {@link #MAX_AGE_MS}마다 한 번은 새로 만든다.
 */
public class CompanionSnapshotCache {

    /** 바뀐 게 없어도 이 시간이 지나면 새로 만든다 (트렌드 등 다른 사람이 바꾸는 데이터) */
    static final long MAX_AGE_MS = 60_000;
    /** 기억해 둘 사용자 수 — 넘으면 가장 오래 안 물어본 사람부터 잊는다 */
    static final int MAX_USERS = 1_000;

    /** 할 말 만들기 — 실제로는 CompanionMessageService.load */
    interface Loader {
        CompanionMessageService.Snapshot load(Long userId, int eveningHour) throws SQLException;
    }

    interface Fingerprint {
        String of(Long userId) throws SQLException;
    }

    private record Cached(String key, CompanionMessageService.Snapshot snapshot, long madeAt) {
    }

    private final Loader loader;
    private final Fingerprint fingerprint;
    private final LongSupplier clock;
    private final Map<Long, Cached> entries = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cached> eldest) {
            return size() > MAX_USERS;
        }
    });

    public CompanionSnapshotCache() {
        this(new CompanionMessageService()::load, new CompanionFingerprintDao()::fingerprint, System::currentTimeMillis);
    }

    CompanionSnapshotCache(Loader loader, Fingerprint fingerprint, LongSupplier clock) {
        this.loader = loader;
        this.fingerprint = fingerprint;
        this.clock = clock;
    }

    public CompanionMessageService.Snapshot load(Long userId, int eveningHour) throws SQLException {
        LocalDateTime now = AppClock.now();
        String key = fingerprint.of(userId) + "|" + now.toLocalDate() + "|" + now.getHour() + "|" + eveningHour;
        long at = clock.getAsLong();
        Cached cached = entries.get(userId);
        if (cached != null && cached.key().equals(key) && at - cached.madeAt() < MAX_AGE_MS) {
            return cached.snapshot();
        }
        CompanionMessageService.Snapshot fresh = loader.load(userId, eveningHour);
        entries.put(userId, new Cached(key, fresh, at));
        return fresh;
    }

    /** 이 사용자 것은 다음에 꼭 새로 만든다 — 캐릭터가 다른 계정으로 옮겨 갔을 때 등 */
    public void forget(Long userId) {
        entries.remove(userId);
    }
}
