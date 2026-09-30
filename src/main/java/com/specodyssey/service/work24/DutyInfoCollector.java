package com.specodyssey.service.work24;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 직무정보 · 표준직무기술서(215L01) — 직무(JOB)별 NCS 지식·기술에서 요구 기술을 뽑아 JOB_REQUIRED_SKILL에 넣는다.
 * 관련 요구사항: FR-113 (공고 데이터가 없는 직무 보완), TD-1 격차 분석 기준 데이터
 *
 * - 직무명(JOB.job_name)을 수행직무 내용으로 넣어 관련 NCS 능력단위를 받는다.
 * - 직무와 무관한 능력단위가 섞여 오므로 NCS 대분류 20(정보통신)만 남긴다.
 * - 지식·기술 항목만 쓰고 태도(협업 태도 등)는 버린다.
 * - JOB_REQUIRED_SKILL: source='NCS', importance='REQUIRED', is_estimated=FALSE.
 *   재수집 때 이 직무의 NCS 행만 새 결과로 교체하고, MANUAL·WORKNET 행은 건드리지 않는다.
 * - 원본 응답은 EXTERNAL_API_CACHE["215L01:{직무명}"]에 남긴다 (로드맵 설명용 능력단위 정의 ablt_def 포함).
 */
public class DutyInfoCollector implements Work24Collector {

    private static final Logger LOG = Logger.getLogger(DutyInfoCollector.class.getName());
    static final String API_CODE = "215L01";
    static final String SOURCE = "NCS";
    private static final String KEY_NAME = "WORK24_DUTY_INFO_API_KEY";
    private static final String UNIT_LIMIT = "5";
    private static final String IT_MAJOR_CLASS = "/_20"; // NCS 대분류 20 정보통신 (job_lrcl_cd가 URI로 온다)
    private static final long CALL_INTERVAL_MS = 300;

    private final Work24Client client = new Work24Client();
    private final Work24Cache cache = new Work24Cache();
    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();
    private final JobRequiredSkillDao requiredSkillDao = new JobRequiredSkillDao();

    /** 능력단위 하나 — 이름, 대분류 코드(URI), 지식·기술 문장들 */
    record Unit(String name, String majorClassUri, List<String> knowledgeAndSkills) {
        boolean isIt() {
            return majorClassUri != null && majorClassUri.endsWith(IT_MAJOR_CLASS);
        }
    }

    /** 직무 하나의 수집 결과 */
    record JobResult(JobDto job, String rawBody, List<Unit> itUnits, int allUnits,
                     Set<Long> skillIds, Set<Long> ownedByOtherSource) {
    }

    @Override
    public String name() {
        return "직무정보";
    }

    @Override
    public String keyPrefix() {
        return API_CODE + ":";
    }

    static String requestKey(JobDto job) {
        return API_CODE + ":" + job.getJobName();
    }

    @Override
    public Prepared prepare() throws ExternalApiException, SQLException {
        List<JobDto> jobs = jobDao.findAll();
        NcsSkillExtractor extractor = new NcsSkillExtractor(skillDao.findAll());

        List<JobResult> results = new ArrayList<>();
        List<JobDto> failed = new ArrayList<>();
        for (JobDto job : jobs) {
            try {
                String body = client.call(API_CODE, KEY_NAME,
                        Map.of("jobCont", job.getJobName(), "limit", UNIT_LIMIT, "returnType", "JSON"));
                List<Unit> units = parse(body);
                List<Unit> itUnits = units.stream().filter(Unit::isIt).toList();
                Set<Long> skillIds = extractor.extract(
                        itUnits.stream().flatMap(u -> u.knowledgeAndSkills().stream()).toList());
                // 같은 (job_id, skill_id)를 다른 출처가 이미 가지고 있으면 NCS는 덮어쓰지 않는다 — 미리 세어 둔다
                Set<Long> owned = requiredSkillDao.findByJobId(job.getId()).stream()
                        .filter(r -> !SOURCE.equals(r.getSource()))
                        .map(JobRequiredSkillDto::getSkillId)
                        .filter(skillIds::contains)
                        .collect(Collectors.toSet());
                results.add(new JobResult(job, body, itUnits, units.size(), skillIds, owned));
            } catch (ExternalApiException e) {
                // 직무 하나가 실패해도 나머지 직무는 진행한다 — 실패한 직무는 직전 데이터 유지
                LOG.log(Level.WARNING, "직무정보 수집 실패 — " + job.getJobName() + " 은(는) 직전 데이터를 유지합니다", e);
                failed.add(job);
            }
            pause();
        }
        if (results.isEmpty()) {
            throw new ExternalApiException("모든 직무의 직무정보 수집에 실패했습니다", null);
        }

        return new Prepared() {
            @Override
            public String summary() {
                StringBuilder sb = new StringBuilder("직무 " + jobs.size() + "개 중 성공 " + results.size()
                        + " · 실패 " + failed.size() + " → JOB_REQUIRED_SKILL(source=NCS) + EXTERNAL_API_CACHE[215L01:직무명]");
                for (JobResult r : results) {
                    Set<Long> willInsert = new LinkedHashSet<>(r.skillIds());
                    willInsert.removeAll(r.ownedByOtherSource());
                    sb.append("\n    ").append(r.job().getJobName())
                            .append(" | 능력단위 ").append(r.itUnits().size()).append('/').append(r.allUnits()).append("(정보통신/전체)")
                            .append(" | 넣을 기술 ").append(names(willInsert, extractor));
                    if (!r.ownedByOtherSource().isEmpty()) {
                        sb.append(" | 다른 출처에 이미 있어 건너뜀 ").append(names(r.ownedByOtherSource(), extractor));
                    }
                }
                failed.forEach(j -> sb.append("\n    ").append(j.getJobName()).append(" | 실패 — 직전 데이터 유지"));
                return sb.toString();
            }

            @Override
            public String save() throws SQLException {
                int inserted = 0;
                LocalDateTime now = LocalDateTime.now(Work24Cache.ZONE);
                for (JobResult r : results) {
                    // 원본 캐시는 따로 먼저 저장한다 — 아래 트랜잭션이 롤백돼도 받은 응답은 남아야 한다 (FR-111)
                    TransactionUtil.runInTransaction(conn -> {
                        cache.saveSuccess(conn, requestKey(r.job()), r.rawBody());
                        return null;
                    });
                    inserted += TransactionUtil.runInTransaction(conn -> {
                        requiredSkillDao.softDeleteByJobAndSource(conn, r.job().getId(), SOURCE);
                        int count = 0;
                        for (Long skillId : r.skillIds()) {
                            if (r.ownedByOtherSource().contains(skillId)) {
                                continue;
                            }
                            JobRequiredSkillDto item = new JobRequiredSkillDto();
                            item.setJobId(r.job().getId());
                            item.setSkillId(skillId);
                            item.setImportance("REQUIRED");
                            item.setSource(SOURCE);
                            item.setEstimated(false); // NCS 공식 기준 — LLM 추정이 아니다
                            item.setCollectedAt(now);
                            requiredSkillDao.upsertForSource(conn, item);
                            count++;
                        }
                        return count;
                    });
                }
                for (JobDto job : failed) {
                    cache.saveFailure(requestKey(job));
                }
                return "직무 " + results.size() + "개 갱신 · NCS 요구 기술 " + inserted + "건 · 실패 " + failed.size() + "개";
            }
        };
    }

    /**
     * {"result": {"능력단위명": {"job_lrcl_cd": "…/_20", "knwg_tchn_attd": [{"knwg_tchn_attd": "…/기술/…", "knwg_tchn_attd_label": "…"}]}}}
     * 결과가 없으면 빈 목록.
     */
    static List<Unit> parse(String json) throws ExternalApiException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement result = root.get("result");
            List<Unit> units = new ArrayList<>();
            if (result == null || !result.isJsonObject()) {
                return units;
            }
            for (Map.Entry<String, JsonElement> entry : result.getAsJsonObject().entrySet()) {
                JsonObject unit = entry.getValue().getAsJsonObject();
                List<String> sentences = new ArrayList<>();
                JsonElement attds = unit.get("knwg_tchn_attd");
                if (attds != null && attds.isJsonArray()) {
                    for (JsonElement a : (JsonArray) attds) {
                        JsonObject item = a.getAsJsonObject();
                        String uri = text(item, "knwg_tchn_attd");
                        String label = text(item, "knwg_tchn_attd_label");
                        // 지식·기술만 쓴다 — 태도(예: "다양성과 개방성에 대한 열린 태도")는 기술이 아니다
                        if (label != null && uri != null && (uri.contains("/지식/") || uri.contains("/기술/"))) {
                            sentences.add(label);
                        }
                    }
                }
                units.add(new Unit(entry.getKey(), text(unit, "job_lrcl_cd"), sentences));
            }
            return units;
        } catch (JsonParseException | IllegalStateException e) {
            throw new ExternalApiException("직무정보 JSON 파싱 실패", e);
        }
    }

    private static String text(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String names(Set<Long> ids, NcsSkillExtractor extractor) {
        return ids.isEmpty() ? "없음" : ids.stream().map(extractor::nameOf).collect(Collectors.joining(", "));
    }

    private static void pause() {
        try {
            Thread.sleep(CALL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
