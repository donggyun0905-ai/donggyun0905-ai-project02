package com.specodyssey.service;

import com.specodyssey.dao.JobBenchmarkSpecDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobRequiredSkillDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.JobBenchmarkSpecDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobRequiredSkillDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.GroqLlmClient;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.LlmRetryPolicy;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 합격자 스펙 역산(JOB_BENCHMARK_SPEC) — LLM이 "이 직무는 보통 이 정도 단계를 밟는다"를
 * 티어별로 정리해준다. 관련 요구사항: FR-46 데이터 인사이트
 *
 * 3번 체크리스트 감사에서 "DAO만 있고 호출 0건"으로 나온 테이블 — 실제 합격자 데이터가 없어서
 * 명세서 TD-2 "대안 B"(LLM이 일반적 요구 역량을 생성하되 '예시적 추정'으로 명시)를 그대로
 * 적용한다.
 *
 * 한 번 생성하면 JOB_BENCHMARK_SPEC에 쌓아두고 재사용한다(On-demand + 캐싱, TD-2 설계 원칙과
 * 동일) — 매번 LLM을 부르면 직무 하나당 매 조회마다 비용이 나간다.
 *
 * 호출·재시도는 공용 LlmClient(GroqLlmClient, 화면용 짧은 재시도)에 맡긴다 (2026-10-06, FR-111).
 * 예전엔 이 클래스가 Groq를 직접 부르고 429에 20·40·60초씩 기다려 /insights 화면이 몇 분씩 멈출 수 있었다.
 * 실패하면 그 직무는 FAILURE_COOLDOWN 동안 다시 부르지 않는다 — 한도가 막힌 날 방문할 때마다 멈추지 않게.
 */
public class JobBenchmarkSpecService {

    private static final Logger LOG = Logger.getLogger(JobBenchmarkSpecService.class.getName());

    private static final double TEMPERATURE = 0.4;
    private static final int MAX_COMPLETION_TOKENS = 1200;
    private static final int MAX_CONTENT_LENGTH = 120;
    static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(10);

    private static final List<String> TIER_ORDER = List.of("ENTRY", "CORE", "ADVANCED", "EXPERT");
    private static final int MAX_REQUIRED_SKILLS_IN_PROMPT = 8;

    // 서블릿 인스턴스가 달라도 공유한다 (job_id → 마지막 실패 시각 ms)
    private static final Map<Long, Long> LAST_FAILURE = new ConcurrentHashMap<>();

    private final JobBenchmarkSpecDao jobBenchmarkSpecDao = new JobBenchmarkSpecDao();
    private final JobDao jobDao = new JobDao();
    private final JobRequiredSkillDao jobRequiredSkillDao = new JobRequiredSkillDao();
    private final SkillDao skillDao = new SkillDao();
    private final LlmClient llm;
    private final LongSupplier clock;

    public JobBenchmarkSpecService() {
        this(GroqLlmClient.fromConfig().withSettings(TEMPERATURE, MAX_COMPLETION_TOKENS), System::currentTimeMillis);
    }

    // 테스트에서 StubLlmClient와 가짜 시계를 넣는다
    JobBenchmarkSpecService(LlmClient llm, LongSupplier clock) {
        this.llm = llm;
        this.clock = clock;
    }

    // LLM 응답 형식 — {"items":[{"tier":"","specType":"","content":""}]}
    static class Response {
        List<Item> items;
    }

    static class Item {
        String tier;
        String specType;
        String content;
    }

    public record BenchmarkItem(String specType, String content) {
        // JSP의 EL이 읽을 수 있게 getter를 같이 둔다 — Tomcat 10.1(BeanELResolver)은 getX()만, Tomcat 11(RecordELResolver)은 x()만 찾는다.
        public String getSpecType() {
            return specType;
        }

        public String getContent() {
            return content;
        }
    }

    public record TierBenchmark(String tier, List<BenchmarkItem> items) {
        // JSP의 EL이 읽을 수 있게 getter를 같이 둔다 — Tomcat 10.1(BeanELResolver)은 getX()만, Tomcat 11(RecordELResolver)은 x()만 찾는다.
        public String getTier() {
            return tier;
        }

        public List<BenchmarkItem> getItems() {
            return items;
        }
    }


    /**
     * 저장된 게 있으면 그대로 돌려주고, 없으면 LLM으로 새로 생성해 저장한 뒤 돌려준다.
     * LLM 실패(API 키 없음·타임아웃·응답 형식 오류)는 예외를 삼키고 빈 리스트를 돌려준다 — 데이터
     * 인사이트 화면 하나가 깨진다고 전체 페이지가 에러나면 안 된다(FR-111 취지).
     * 실패한 직무는 FAILURE_COOLDOWN 동안 LLM을 부르지 않고 바로 빈 리스트를 돌려준다.
     */
    public List<TierBenchmark> getOrGenerate(Long jobId) throws SQLException {
        List<JobBenchmarkSpecDto> existing = jobBenchmarkSpecDao.findByJobId(jobId);
        if (existing.isEmpty()) {
            if (isTemporarilyUnavailable(jobId)) {
                return List.of();
            }
            try {
                generate(jobId);
                LAST_FAILURE.remove(jobId);
                existing = jobBenchmarkSpecDao.findByJobId(jobId);
            } catch (ExternalApiException | RuntimeException e) {
                LOG.log(Level.WARNING, "합격자 스펙 역산 생성 실패 (jobId=" + jobId + ")", e);
                LAST_FAILURE.put(jobId, clock.getAsLong());
                return List.of();
            }
        }
        return groupByTier(existing);
    }

    /** 최근 AI 생성에 실패해 아직 다시 부르지 않는 중인지 — 화면이 "희망 직무 확인" 대신 실패 안내를 띄울 때 쓴다 */
    public boolean isTemporarilyUnavailable(Long jobId) {
        Long failedAt = jobId == null ? null : LAST_FAILURE.get(jobId);
        return failedAt != null && clock.getAsLong() - failedAt < FAILURE_COOLDOWN.toMillis();
    }

    private List<TierBenchmark> groupByTier(List<JobBenchmarkSpecDto> specs) {
        Map<String, List<BenchmarkItem>> byTier = new LinkedHashMap<>();
        for (String tier : TIER_ORDER) {
            byTier.put(tier, new ArrayList<>());
        }
        for (JobBenchmarkSpecDto spec : specs) {
            byTier.computeIfAbsent(spec.getTier(), t -> new ArrayList<>())
                    .add(new BenchmarkItem(spec.getSpecType(), spec.getContent()));
        }
        List<TierBenchmark> result = new ArrayList<>();
        for (String tier : TIER_ORDER) {
            if (!byTier.get(tier).isEmpty()) {
                result.add(new TierBenchmark(tier, byTier.get(tier)));
            }
        }
        return result;
    }

    private void generate(Long jobId) throws SQLException, ExternalApiException {
        JobDto job = jobDao.findById(jobId);
        if (job == null) {
            return;
        }
        List<String> topSkillNames = topRequiredSkillNames(jobId);
        Response response = llm.completeJson(systemPrompt() + "\n\n" + userPrompt(job.getJobName(), topSkillNames),
                Response.class);
        List<JobBenchmarkSpecDto> parsed = toSpecs(response, jobId);

        LocalDateTime now = LocalDateTime.now();
        TransactionUtil.runInTransaction(conn -> {
            for (JobBenchmarkSpecDto spec : parsed) {
                spec.setGeneratedAt(now);
                spec.setEstimated(true);
                jobBenchmarkSpecDao.insert(conn, spec);
            }
            return null;
        });
    }

    private List<String> topRequiredSkillNames(Long jobId) throws SQLException {
        List<JobRequiredSkillDto> required = jobRequiredSkillDao.findByJobId(jobId);
        required.sort(Comparator.comparing((JobRequiredSkillDto r) -> "REQUIRED".equals(r.getImportance()) ? 0 : 1));
        List<String> names = new ArrayList<>();
        for (JobRequiredSkillDto req : required) {
            if (names.size() >= MAX_REQUIRED_SKILLS_IN_PROMPT) {
                break;
            }
            SkillDto skill = skillDao.findById(req.getSkillId());
            if (skill != null) {
                names.add(skill.getSkillName());
            }
        }
        return names;
    }

    private String systemPrompt() {
        return """
                너는 채용 시장 분석가다. 주어진 목표 직무와 핵심 요구 기술을 보고, 그 직무에 합격하는
                사람들이 보통 밟는 성장 단계를 "입문(ENTRY) → 핵심(CORE) → 심화(ADVANCED) →
                전문가(EXPERT)" 4단계로 정리해라.
                각 단계마다 1~3개 항목을 만들어라. 각 항목은:
                - tier: ENTRY/CORE/ADVANCED/EXPERT 중 하나
                - specType: CERT(자격증)/PROJECT(프로젝트 경험)/SKILL(기술)/LANGUAGE(사용 언어) 중 하나
                - content: 한국어로 간결하게 (%d자 이내, 예: "Java와 Spring으로 만든 프로젝트 1개")
                실제 합격자 데이터가 아니라 일반적인 추정이라는 걸 알고, 과장하지 말고 현실적으로 작성해라.
                반드시 다음 JSON 객체 하나만 출력한다: {"items":[{"tier":"","specType":"","content":""}]}
                4단계가 전부 최소 1개씩은 있어야 한다.
                """.formatted(MAX_CONTENT_LENGTH);
    }

    private String userPrompt(String jobName, List<String> topSkillNames) {
        String skillsText = topSkillNames.isEmpty() ? "(정보 없음)" : String.join(", ", topSkillNames);
        return "목표 직무: " + jobName + "\n핵심 요구 기술: " + skillsText;
    }

    // LLM 출력은 신뢰하지 않는다 — 형식을 못 지킨 항목은 건너뛰고, 쓸 항목이 하나도 없으면 예외 (NFR-6)
    static List<JobBenchmarkSpecDto> toSpecs(Response response, Long jobId) throws ExternalApiException {
        if (response == null || response.items == null || response.items.isEmpty()) {
            throw new ExternalApiException("LLM이 빈 목록을 반환했습니다", null, LlmRetryPolicy.FORMAT_ERROR);
        }
        List<JobBenchmarkSpecDto> specs = new ArrayList<>();
        for (Item item : response.items) {
            if (item == null || item.tier == null || item.specType == null || item.content == null) {
                continue;
            }
            String tier = item.tier.trim().toUpperCase();
            String specType = item.specType.trim().toUpperCase();
            String itemContent = item.content.trim();
            if (!TIER_ORDER.contains(tier) || itemContent.isEmpty()) {
                continue; // 형식을 못 지킨 항목 하나 때문에 전체를 실패시키지 않는다 — 그냥 건너뜀
            }
            if (itemContent.length() > MAX_CONTENT_LENGTH) {
                itemContent = itemContent.substring(0, MAX_CONTENT_LENGTH);
            }
            JobBenchmarkSpecDto spec = new JobBenchmarkSpecDto();
            spec.setJobId(jobId);
            spec.setTier(tier);
            spec.setSpecType(specType);
            spec.setContent(itemContent);
            specs.add(spec);
        }
        if (specs.isEmpty()) {
            throw new ExternalApiException("유효한 항목이 하나도 없습니다", null, LlmRetryPolicy.FORMAT_ERROR);
        }
        return specs;
    }
}
