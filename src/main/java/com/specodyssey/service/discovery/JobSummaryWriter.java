package com.specodyssey.service.discovery;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.specodyssey.service.CachingLlmClient;
import com.specodyssey.service.LlmResult;
import com.specodyssey.service.discovery.JobDiscoveryScorer.Recommendation;
import com.specodyssey.util.GroqLlmClient;
import com.specodyssey.util.LlmClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 추천 직무별 요약 — 하는 일 / 필요 역량 / 전망. 관련 요구사항: FR-35, FR-111
 *
 * ── 어디서 가져오나 ─────────────────────────────────────────
 *   하는 일  LLM 1~2문장
 *   필요 역량 DB(JOB_REQUIRED_SKILL) 그대로 — 지어낼 위험이 없고 격차 분석 화면과 같은 데이터다
 *   전망    LLM 1~2문장. 연봉·채용 건수·성장률 같은 숫자는 그럴듯한 가짜 통계가 되기 쉬워 금지하고,
 *           응답에 수치가 섞이면 그 항목만 기본 문구로 바꾼다. 화면에는 "AI가 정리한 일반적인 설명"으로 표시.
 *
 * ── 직무별로 만든다 ─────────────────────────────────────────
 *   "백엔드 개발자가 하는 일"은 누가 봐도 같아서 프롬프트에 사용자 정보를 넣지 않는다.
 *   그래서 CachingLlmClient(DB 캐시)가 다른 사용자 요청에도 같은 결과를 재사용한다 — 직무 18개면 18번이면 끝.
 *   직무 설명은 자주 바뀌지 않아 캐시 유효기간을 30일로 길게 둔다.
 *   나중에 근거를 늘리려면(예: 트렌드 데이터로 전망 보강) prompt()에 줄을 더하면 된다.
 *
 * ── 실패하면 (FR-111) ────────────────────────────────────────
 *   LLM이 실패하면 계열별 기본 문구로 채운다 — 요약이 비는 일은 없다. 안내 배너는 추천 이유(RecommendationDescriber)가
 *   이미 띄우므로 여기서는 따로 띄우지 않는다. 첫 호출이 실패하면 나머지 직무는 호출하지 않는다(타임아웃이 쌓이지 않게).
 */
public class JobSummaryWriter {

    static final int MAX_TEXT_LENGTH = 200;
    static final int MAX_SKILLS = 6;
    static final String SOURCE_AI = "AI";
    static final String SOURCE_DEFAULT = "DEFAULT";
    private static final Duration CACHE_TTL = Duration.ofDays(30);
    private static final Logger LOG = Logger.getLogger(JobSummaryWriter.class.getName());
    private static final Gson GSON = new Gson();

    // "연봉 4천만 원", "30% 성장", "채용 1만 건"처럼 숫자로 된 통계·금액
    private static final Pattern NUMERIC_CLAIM =
            Pattern.compile("\\d[\\d,.]*\\s*(%|퍼센트|만|억|천|원|명|건|배|위)");

    /** 계열별 기본 문구 [하는 일, 전망] — LLM이 실패하거나 응답이 이상할 때 쓴다 */
    static final Map<String, String[]> DEFAULTS = Map.of(
            "BACKEND", new String[]{
                    "서비스 뒤에서 돌아가는 서버와 API를 만들고, 데이터베이스에 데이터를 저장·조회하는 로직을 설계합니다.",
                    "대부분의 서비스에 서버가 필요해 꾸준히 찾는 직무이며, 클라우드·대용량 처리 경험이 있으면 선택지가 넓어집니다."},
            "FRONTEND", new String[]{
                    "사용자가 직접 보고 누르는 웹 화면을 만들고, 디자인과 서버 데이터를 화면에 자연스럽게 연결합니다.",
                    "사용자 경험이 서비스 경쟁력이 되면서 수요가 이어지고 있으며, 프레임워크 변화가 빨라 꾸준한 학습이 중요합니다."},
            "DATA", new String[]{
                    "데이터를 모으고 정리해 분석하거나, 분석에 필요한 데이터 흐름(파이프라인)을 만들어 의사결정을 돕습니다.",
                    "데이터 기반 의사결정과 AI 활용이 넓어지면서 관심이 커지는 분야이며, 통계와 SQL 기본기가 특히 중요합니다."},
            "DEVOPS", new String[]{
                    "서버·클라우드 인프라를 운영하고, 코드가 자동으로 테스트·배포되도록 개발과 운영 과정을 자동화합니다.",
                    "클라우드 전환이 계속되면서 인프라 자동화 역량을 찾는 곳이 늘고 있으며, 리눅스와 네트워크 기초가 바탕이 됩니다."},
            "SECURITY", new String[]{
                    "시스템과 데이터를 공격으로부터 지키기 위해 취약점을 찾고, 보안 정책과 대응 체계를 만들고 운영합니다.",
                    "보안 사고의 영향이 커지면서 중요성이 높아지는 분야이며, 네트워크·운영체제 이해와 관련 자격증이 도움이 됩니다."},
            "PM", new String[]{
                    "서비스가 해결할 문제를 정의하고 기능을 기획하며, 개발·디자인 등 여러 사람의 일정과 의견을 조율합니다.",
                    "기술을 이해하는 기획자를 찾는 곳이 많아지고 있으며, 데이터를 읽고 근거로 설득하는 능력이 강점이 됩니다."});
    private static final String[] GENERIC_DEFAULT = {
            "IT 서비스를 만들고 운영하는 과정에서 이 직무만의 역할을 맡습니다.",
            "IT 전반의 수요와 함께 움직이는 직무이며, 요구 기술을 하나씩 갖추는 것이 가장 확실한 준비입니다."};

    private final LlmClient llm;

    public JobSummaryWriter() {
        this(new CachingLlmClient(GroqLlmClient.fromConfig(), CACHE_TTL));
    }

    public JobSummaryWriter(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * 각 추천의 summaryJson을 채운다. 어떤 경우에도 예외를 던지지 않는다.
     * @param skillsByJob job_id → 요구 기술 이름 (필수 먼저). 없으면 필요 역량이 빈 목록이 된다.
     */
    public void summarize(List<Recommendation> recommendations, Map<Long, List<String>> skillsByJob) {
        if (recommendations == null) {
            return;
        }
        boolean llmDown = false;
        for (Recommendation r : recommendations) {
            List<String> skills = topSkills(skillsByJob == null ? null : skillsByJob.get(r.jobId));
            Response ai = null;
            if (!llmDown) {
                LlmResult<Response> result = call(r, skills);
                if (result == null || result.isUnavailable()) {
                    llmDown = true;
                } else {
                    ai = result.getValue();
                }
            }
            r.summaryJson = GSON.toJson(build(r.category, skills, ai));
        }
    }

    private LlmResult<Response> call(Recommendation r, List<String> skills) {
        try {
            return CachingLlmClient.completeWithStatus(llm, prompt(r.jobName, r.category, skills), Response.class);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "직무 요약 LLM 생성 실패 — 기본 문구를 씁니다: " + r.jobName, e);
            return null;
        }
    }

    /** LLM 응답(없으면 null)과 기본 문구로 요약을 만든다. 항목마다 따로 검사해 이상한 것만 기본 문구로 바꾼다. */
    static JobSummary build(String category, List<String> skills, Response ai) {
        String[] fallback = DEFAULTS.getOrDefault(category, GENERIC_DEFAULT);
        String duties = ai == null ? null : clean(ai.duties);
        String outlook = ai == null ? null : clean(ai.outlook);
        if (outlook != null && NUMERIC_CLAIM.matcher(outlook).find()) {
            outlook = null;
        }
        boolean fromAi = duties != null && outlook != null;
        return new JobSummary(duties == null ? fallback[0] : duties, skills,
                outlook == null ? fallback[1] : outlook, fromAi ? SOURCE_AI : SOURCE_DEFAULT);
    }

    /** 저장된 summary_json → 화면용. 비었거나 깨졌으면 null (예전에 받은 추천). */
    public static JobSummary parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JobSummary s = GSON.fromJson(json, JobSummary.class);
            return s == null || s.duties == null ? null : s;
        } catch (JsonParseException e) {
            return null;
        }
    }

    static String prompt(String jobName, String category, List<String> skills) {
        return """
                너는 IT 진로 안내서를 쓰는 사람이다. 아래 직무를 처음 알아보는 취업 준비생에게 한국어로 설명해라.
                - duties: 이 직무가 실제로 하는 일. 1~2문장, %d자 이내.
                - outlook: 이 직무의 일반적인 전망과 준비할 때 도움이 되는 점. 1~2문장, %d자 이내.
                - 연봉·채용 건수·성장률 같은 숫자와 통계, 특정 회사 이름은 쓰지 않는다. 확실하지 않은 내용은 쓰지 않는다.
                - 모든 문장은 "~합니다"로 끝나는 존댓말로 쓰고, 문장을 직무 이름으로 시작하지 않는다.
                - 참고 요구 기술은 하는 일을 설명할 때만 참고하고, 기술 이름을 길게 나열하지 않는다.
                반드시 다음 JSON 객체 하나만 출력한다: {"duties":"","outlook":""}

                직무: %s (%s)
                참고 요구 기술: %s""".formatted(MAX_TEXT_LENGTH, MAX_TEXT_LENGTH, jobName, category,
                skills.isEmpty() ? "없음" : String.join(", ", skills));
    }

    private static List<String> topSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : skills) {
            if (s != null && !s.isBlank() && !out.contains(s) && out.size() < MAX_SKILLS) {
                out.add(s);
            }
        }
        return out;
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.strip();
        return t.length() > MAX_TEXT_LENGTH ? t.substring(0, MAX_TEXT_LENGTH) : t;
    }

    static class Response {
        String duties;
        String outlook;
    }

    /**
     * summary_json 한 건 = 화면 표시용. EL이 getter로 읽으므로 record 대신 클래스로 둔다
     * (Tomcat 10.1의 EL 5.0은 record 접근자를 못 읽음).
     */
    public static class JobSummary {
        private String duties;
        private List<String> skills;
        private String outlook;
        private String source;

        JobSummary() {
        }

        JobSummary(String duties, List<String> skills, String outlook, String source) {
            this.duties = duties;
            this.skills = skills;
            this.outlook = outlook;
            this.source = source;
        }

        public String getDuties() { return duties; }
        public List<String> getSkills() { return skills == null ? List.of() : skills; }
        public String getOutlook() { return outlook; }
        public boolean isAi() { return SOURCE_AI.equals(source); }
    }
}
