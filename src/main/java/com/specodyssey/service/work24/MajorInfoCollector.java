package com.specodyssey.service.work24;

import com.google.gson.Gson;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 학과정보(213L01) — 표준 학과명 전체 목록을 캐시에 둔다.
 * 관련 요구사항: FR-21 프로필 전공 입력(자동완성), FR-45 또래 비교 집계 키(USERS.major 표준화)
 *
 * 원본 XML은 약 180KB라 TEXT에 안 들어가므로 학과명별로 묶은 JSON(약 22KB)으로 줄여 저장한다.
 * 저장 형식: [{"name":"컴퓨터공학과","group":"5","unusual":false,"details":["소프트웨어공학과", …]}, …]
 */
public class MajorInfoCollector implements Work24Collector {

    static final String API_CODE = "213L01";
    static final String REQUEST_KEY = API_CODE + ":ALL";
    private static final String KEY_NAME = "WORK24_MAJOR_INFO_API_KEY";
    private static final Gson GSON = new Gson();

    private final Work24Client client = new Work24Client();
    private final Work24Cache cache = new Work24Cache();

    record Major(String name, String group, boolean unusual, List<String> details) {
    }

    @Override
    public String name() {
        return "학과정보";
    }

    @Override
    public String keyPrefix() {
        return API_CODE + ":";
    }

    @Override
    public Prepared prepare() throws ExternalApiException {
        String xml = client.call(API_CODE, KEY_NAME,
                Map.of("returnType", "XML", "target", "MAJORCD", "srchType", "A", "keyword", ""));
        List<Major> majors = parse(xml);
        if (majors.isEmpty()) {
            // 빈 목록으로 덮어쓰면 자동완성이 통째로 사라진다 — 실패로 보고 직전 캐시를 유지한다
            throw new ExternalApiException("학과 목록이 비어 있습니다", null);
        }
        String body = GSON.toJson(majors);
        int details = majors.stream().mapToInt(m -> m.details().size()).sum();

        return new Prepared() {
            @Override
            public String summary() {
                return "학과 " + majors.size() + "개 · 세부학과 " + details + "개 → EXTERNAL_API_CACHE[" + REQUEST_KEY + "] "
                        + body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + "바이트";
            }

            @Override
            public String save() throws SQLException {
                TransactionUtil.runInTransaction(conn -> {
                    cache.saveSuccess(conn, REQUEST_KEY, body);
                    return null;
                });
                return "학과 " + majors.size() + "개 캐시 갱신";
            }
        };
    }

    /** 학과명 기준으로 묶는다. 같은 학과명 아래 세부학과명은 중복 없이 가나다순. */
    static List<Major> parse(String xml) throws ExternalApiException {
        Map<String, String> groupOf = new LinkedHashMap<>();
        Map<String, Boolean> unusualOf = new LinkedHashMap<>();
        Map<String, TreeSet<String>> detailsOf = new LinkedHashMap<>();
        for (Map<String, String> row : Work24Xml.rows(xml, "majorList")) {
            String name = row.getOrDefault("knowSchDptNm", "");
            if (name.isEmpty()) {
                continue;
            }
            groupOf.putIfAbsent(name, row.getOrDefault("empCurtState1Id", ""));
            unusualOf.putIfAbsent(name, "2".equals(row.get("majorGb"))); // 1 일반학과 · 2 이색학과
            TreeSet<String> details = detailsOf.computeIfAbsent(name, k -> new TreeSet<>());
            String detail = row.getOrDefault("knowDtlSchDptNm", "");
            if (!detail.isEmpty() && !detail.equals(name)) {
                details.add(detail);
            }
        }
        List<Major> majors = new ArrayList<>();
        detailsOf.forEach((name, details) ->
                majors.add(new Major(name, groupOf.get(name), unusualOf.get(name), new ArrayList<>(details))));
        return majors;
    }
}
