package com.specodyssey.service.work24;

import com.google.gson.Gson;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 직업정보(212L01) — IT 계열 표준 직업명 목록을 캐시에 둔다.
 * 용도: JOB · JOB_ALIAS 시드를 만들 때 참고하는 표준 직업명 (자동으로 JOB에 넣지는 않는다).
 *
 * 직업정보는 전체 조회가 없고 키워드 검색만 있어, IT 관련 키워드 여러 개로 모은 뒤
 * 직업분류코드 13x(정보통신 계열)만 남긴다. "개발자"로 찾으면 "여행상품 개발자"도 나오기 때문이다.
 * 저장 형식: [{"jobCd":"K000001106","jobNm":"웹개발자(웹 프로그래머)","jobClcd":"133","jobClcdNm":"소프트웨어 개발자"}, …]
 */
public class JobInfoCollector implements Work24Collector {

    static final String API_CODE = "212L01";
    static final String REQUEST_KEY = API_CODE + ":IT";
    private static final String KEY_NAME = "WORK24_JOB_INFO_API_KEY";
    private static final List<String> KEYWORDS =
            List.of("개발자", "프로그래머", "엔지니어", "데이터", "보안", "네트워크", "시스템", "기획자");
    private static final String IT_CLASS_PREFIX = "13";
    private static final long CALL_INTERVAL_MS = 300;
    private static final Gson GSON = new Gson();

    private final Work24Client client = new Work24Client();
    private final Work24Cache cache = new Work24Cache();

    record Job(String jobCd, String jobNm, String jobClcd, String jobClcdNm) {
    }

    @Override
    public String name() {
        return "직업정보";
    }

    @Override
    public String keyPrefix() {
        return API_CODE + ":";
    }

    @Override
    public Prepared prepare() throws ExternalApiException {
        Map<String, Job> byCode = new LinkedHashMap<>();
        for (String keyword : KEYWORDS) {
            // 키워드 하나라도 실패하면 전체 실패로 본다 — 일부만 모은 목록으로 직전 캐시를 덮어쓰지 않기 위해
            String xml = client.call(API_CODE, KEY_NAME,
                    Map.of("returnType", "XML", "target", "JOBCD", "srchType", "K", "keyword", keyword));
            for (Job job : parseItJobs(xml)) {
                byCode.putIfAbsent(job.jobCd(), job);
            }
            pause();
        }
        if (byCode.isEmpty()) {
            throw new ExternalApiException("IT 계열 직업이 하나도 없습니다", null);
        }
        List<Job> jobs = new ArrayList<>(byCode.values());
        String body = GSON.toJson(jobs);

        return new Prepared() {
            @Override
            public String summary() {
                StringBuilder sb = new StringBuilder("IT 직업 " + jobs.size() + "개 → EXTERNAL_API_CACHE[" + REQUEST_KEY + "]");
                jobs.forEach(j -> sb.append("\n    ").append(j.jobClcd()).append(' ').append(j.jobNm()));
                return sb.toString();
            }

            @Override
            public String save() throws SQLException {
                TransactionUtil.runInTransaction(conn -> {
                    cache.saveSuccess(conn, REQUEST_KEY, body);
                    return null;
                });
                return "IT 직업 " + jobs.size() + "개 캐시 갱신";
            }
        };
    }

    static List<Job> parseItJobs(String xml) throws ExternalApiException {
        List<Job> jobs = new ArrayList<>();
        for (Map<String, String> row : Work24Xml.rows(xml, "jobList")) {
            String clcd = row.getOrDefault("jobClcd", "");
            String cd = row.getOrDefault("jobCd", "");
            if (clcd.startsWith(IT_CLASS_PREFIX) && !cd.isEmpty()) {
                jobs.add(new Job(cd, row.getOrDefault("jobNm", ""), clcd, row.getOrDefault("jobClcdNM", "")));
            }
        }
        return jobs;
    }

    private static void pause() {
        try {
            Thread.sleep(CALL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
