package com.specodyssey.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.specodyssey.dao.AiUsageLogDao;
import com.specodyssey.dto.AiUsageLogDto;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 활용 기록 자기 제출. 관련 요구사항: FR-101(선택) · 102(선택), NFR-4
 *
 * 명세서에서 "[선택] — 여유 있을 때" 최하위 우선순위로 분류된 기능이다. FR-101이 말하는 "활용
 * 스타일 프로파일링"(자동 분류·분석)까지는 이번에 만들지 않는다 — 그건 별도의 분석 로직이 필요한
 * 완전히 다른 작업이라 범위를 벗어난다. 여기서 하는 건 "사용자가 직접 제출"하는 부분, 즉
 * AI_USAGE_LOG가 DAO만 있고 아무도 호출하지 않던 걸 저장·조회·공유여부 전환까지 연결하는
 * 것까지다(2026-10-01, 3번 체크리스트 감사에서 발견).
 *
 * usage_record_json에는 {"title":"...","description":"..."} 형태로 저장한다 — 제목·설명
 * 두 필드만 받는 가장 단순한 자기 제출 폼이라 별도 컬럼을 늘리지 않고 JSON TEXT 컬럼 하나에
 * 담는 원래 스키마 설계를 그대로 따른다.
 */
public class AiUsageLogService {

    private static final Gson GSON = new Gson();

    private final AiUsageLogDao aiUsageLogDao = new AiUsageLogDao();

    public record UsageEntry(Long id, String title, String description, boolean shared) {
    }

    public void submit(Long userId, String title, String description, boolean shared) throws SQLException {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        JsonObject json = new JsonObject();
        json.addProperty("title", title.trim());
        json.addProperty("description", description == null ? "" : description.trim());

        AiUsageLogDto log = new AiUsageLogDto();
        log.setUserId(userId);
        log.setUsageRecordJson(GSON.toJson(json));
        log.setShared(shared);
        aiUsageLogDao.insert(log);
    }

    public List<UsageEntry> listMine(Long userId) throws SQLException {
        List<UsageEntry> entries = new ArrayList<>();
        for (AiUsageLogDto log : aiUsageLogDao.findByUserId(userId)) {
            JsonObject json = JsonParser.parseString(log.getUsageRecordJson()).getAsJsonObject();
            String title = json.has("title") ? json.get("title").getAsString() : "";
            String description = json.has("description") ? json.get("description").getAsString() : "";
            entries.add(new UsageEntry(log.getId(), title, description, log.isShared()));
        }
        return entries;
    }

    public void setShared(Long userId, Long logId, boolean shared) throws SQLException {
        AiUsageLogDto existing = aiUsageLogDao.findByUserId(userId).stream()
                .filter(l -> l.getId().equals(logId))
                .findFirst().orElse(null);
        if (existing == null) {
            return;
        }
        existing.setShared(shared);
        TransactionUtil.runInTransaction(conn -> {
            aiUsageLogDao.update(conn, existing, userId);
            return null;
        });
    }

    public void delete(Long userId, Long logId) throws SQLException {
        TransactionUtil.runInTransaction(conn -> {
            aiUsageLogDao.delete(conn, logId, userId);
            return null;
        });
    }
}
