package com.specodyssey.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.specodyssey.dto.JobDto;
import com.specodyssey.util.AppConfig;
import com.specodyssey.util.GroqRetryAfterClient;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Groq LLM으로 트렌드 후보 글에서 기술명을 뽑고, 한 줄 설명과 관련 직무를 정리한다.
 * 관련 요구사항: FR-54 트렌드 기술 노출, FR-55 직무 연관 필터링
 * 관련 규칙: claude.md "LLM 응답은 JSON으로 받고 파싱 실패를 예외 처리한다" — 형식이 어긋나면 예외를 던져
 * 호출부(TrendCollectService)가 저장 없이 직전 데이터를 유지하게 한다. 서버 코드에서만 호출한다.
 */
public class TrendLlmService {

    private static final Logger LOG = Logger.getLogger(TrendLlmService.class.getName());
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_TECHS = 20;
    private static final int CHUNK_SIZE = 80;
    private static final int MAX_RETRIES = 4;
    private static final long RETRY_WAIT_MS = 20_000;
    // retry-after는 한도가 풀리는 시각이라 딱 맞춰 보내면 다시 걸릴 수 있어 조금 더 기다린다. 비정상적으로 긴 값은 상한으로 자른다.
    private static final Duration RETRY_AFTER_MARGIN = Duration.ofMillis(500);
    private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(60);
    private static final int MAX_SUMMARY_LENGTH = 200;
    private static final int MAX_TECH_NAME_LENGTH = 100;
    private static final BigDecimal MIN_RELEVANCE = new BigDecimal("0.5");
    private static final Pattern TRAILING_QUALIFIER = Pattern.compile("\\s*[(（][^()（）]*[)）]$");

    // FR-55 직군별 보충 수집 때 "이 직군에서 기술로 인정할 것" — 개발 직군이 아니면 도구·방법론이 곧 이력서에 쓰는 기술이다
    private static final Map<String, String> CATEGORY_FOCUS = Map.of(
            "BACKEND", "백엔드 개발: 서버 언어·프레임워크, API 설계, 데이터베이스, 메시징, 캐시, 테스트 기법",
            "FRONTEND", "프론트엔드 개발: 웹 언어·프레임워크, 상태 관리, 빌드 도구, CSS 기법, 웹 접근성·성능 기법",
            "DATA", "데이터: 분석·시각화 도구, 데이터 파이프라인·웨어하우스, 머신러닝 라이브러리, SQL, 통계·실험 기법",
            "DEVOPS", "DevOps·클라우드: 클라우드 서비스, 컨테이너·오케스트레이션, CI/CD, IaC, 모니터링·관측 도구",
            "SECURITY", "보안: 보안 도구, 취약점 분석·모의해킹 기법, 인증·암호 기술, 보안 표준·프레임워크",
            "PM", "IT 기획·PM: 기획·협업 도구(예: Figma, Jira, Confluence, Notion), 프로덕트 분석 도구(예: GA4, Amplitude, Mixpanel), "
                    + "SQL, 방법론(예: 애자일, 스크럼, OKR, A/B 테스트, 사용자 리서치, 와이어프레임, PRD 작성)");

    /** 정리 결과 1건. candidateIndex는 입력 후보 목록의 위치, jobRelevance는 job_id → 연관도(0~1), confidence는 "취업 준비생이 배울 만한 확실한 기술"이라는 LLM의 확신도(0~1). */
    public record TrendItem(int candidateIndex, String techName, String summary, BigDecimal confidence,
                           Map<Long, BigDecimal> jobRelevance) {
    }

    /** 후보를 나눠 여러 번 호출하고 결과를 합친다. 같은 기술은 먼저 나온 것 하나만 남긴다. */
    public List<TrendItem> organize(List<String> candidateTexts, List<JobDto> jobs) throws ExternalApiException {
        return organize(candidateTexts, jobs, null);
    }

    /**
     * 직군 하나에 집중해 정리한다 (FR-55 직군별 보충 수집). jobs에는 그 직군의 직무만 넘긴다.
     * focusCategory가 null이면 일반 정리와 같다.
     */
    public List<TrendItem> organize(List<String> candidateTexts, List<JobDto> jobs, String focusCategory)
            throws ExternalApiException {
        List<TrendItem> all = new ArrayList<>();
        Set<String> seenTech = new HashSet<>();
        for (int offset = 0; offset < candidateTexts.size(); offset += CHUNK_SIZE) {
            List<String> chunk = candidateTexts.subList(offset, Math.min(offset + CHUNK_SIZE, candidateTexts.size()));
            for (TrendItem item : organizeChunk(chunk, jobs, focusCategory)) {
                if (seenTech.add(item.techName().toLowerCase())) {
                    all.add(new TrendItem(offset + item.candidateIndex(), item.techName(), item.summary(), item.confidence(),
                            item.jobRelevance()));
                }
            }
        }
        return all;
    }

    private List<TrendItem> organizeChunk(List<String> candidateTexts, List<JobDto> jobs, String focusCategory)
            throws ExternalApiException {
        String apiKey = AppConfig.get("GROQ_API_KEY");
        if (apiKey == null) {
            throw new ExternalApiException("GROQ_API_KEY가 설정되지 않았습니다 (config.properties 또는 환경변수)", null);
        }
        String model = AppConfig.get("GROQ_MODEL");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model == null ? DEFAULT_MODEL : model);
        body.put("temperature", 0.2);
        // gpt-oss는 추론에 토큰을 쓴다 — 후보가 많으면 출력이 잘려 JSON 검증 오류(400)가 나므로 추론을 줄이고 한도를 넉넉히 둔다
        body.put("reasoning_effort", "low");
        body.put("max_completion_tokens", 8000);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt(focusCategory)),
                Map.of("role", "user", "content", userPrompt(candidateTexts, jobs))));

        String response = post(body, apiKey);
        return parse(response, candidateTexts.size(), jobs.stream().map(JobDto::getId).collect(Collectors.toSet()));
    }

    // 일시적 실패는 다시 시도한다.
    // - 429: Groq 무료 한도(분당 토큰) 초과 — Groq가 retry-after 헤더로 알려주는 시간만큼만 기다렸다가 재시도
    //        (예전엔 20·40·60초 고정이라 몇 초면 풀릴 한도에도 수 분씩 기다렸다). 헤더가 없으면 예전 방식으로 기다린다.
    // - 400: 모델이 JSON 형식을 어긴 출력을 내면 Groq가 검증 실패로 400을 돌려준다(샘플링 탓의 간헐적 실패) — 바로 재시도
    private String post(Map<String, Object> body, String apiKey) throws ExternalApiException {
        for (int attempt = 1; ; attempt++) {
            GroqRetryAfterClient.Response response = GroqRetryAfterClient.postJson(
                    ENDPOINT, body, Map.of("Authorization", "Bearer " + apiKey), TIMEOUT);
            if (response.isSuccess()) {
                return response.body();
            }
            int status = response.statusCode();
            ExternalApiException failure = new ExternalApiException(
                    "외부 API 응답 실패: HTTP " + status + " (" + ENDPOINT + ")", null, status);
            if ((status != 429 && status != 400) || attempt >= MAX_RETRIES) {
                throw failure;
            }
            if (status == 429) {
                Duration wait = retryWait(response.retryAfter(), attempt);
                LOG.info("Groq 요청 한도 초과(429) — " + wait.toMillis() + "ms 후 재시도 (" + attempt + "/" + (MAX_RETRIES - 1)
                        + ", retry-after " + (response.retryAfter() == null ? "없음" : response.retryAfter().toMillis() + "ms") + ")");
                try {
                    Thread.sleep(wait.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            } else {
                LOG.info("Groq JSON 검증 실패(400) — 바로 재시도 (" + attempt + "/" + (MAX_RETRIES - 1) + ")");
            }
        }
    }

    /** 429 대기 시간 — retry-after에 여유분을 더하되 상한을 둔다. 헤더가 없으면 예전처럼 RETRY_WAIT_MS × 시도 횟수. */
    static Duration retryWait(Duration retryAfter, int attempt) {
        if (retryAfter == null) {
            return Duration.ofMillis(RETRY_WAIT_MS * attempt);
        }
        Duration wait = retryAfter.plus(RETRY_AFTER_MARGIN);
        return wait.compareTo(MAX_RETRY_WAIT) > 0 ? MAX_RETRY_WAIT : wait;
    }

    private String systemPrompt(String focusCategory) {
        String focus = focusCategory == null ? null : CATEGORY_FOCUS.get(focusCategory);
        String focusRule = focus == null ? "" : """
                이번에는 다음 직군에 필요한 기술만 고른다. 이 직군이 실무에서 쓰는 도구·방법론도 기술로 인정한다.
                직군: %s
                """.formatted(focus);
        return focusRule + """
                너는 IT 취업 준비생에게 오늘의 기술 트렌드를 알려주는 편집자다.
                번호가 붙은 글 목록에서, 취업 준비생이 학습해서 이력서에 쓸 수 있는 구체적인 IT 기술을 다루는 글만 골라라.
                포함: 프로그래밍 언어, 프레임워크, 라이브러리, 개발 도구, 데이터베이스, 클라우드·인프라 기술, 프로토콜, 개발 기법,
                그리고 개발 외 IT 직무(기획·PM, 데이터 분석, 보안)가 실무에서 쓰는 소프트웨어 도구와 업무 방법론(예: Figma, Jira, GA4, 애자일, A/B 테스트).
                제외: 기업·인물 소식(예: NVIDIA, Meta의 실적·발표), 하드웨어·기기·운영체제 제품, 인사·투자·인수·정치·사건 뉴스, 기술 자체가 아니라 그 기술을 사용한 개인 프로젝트나 잡담, 기술이 특정되지 않는 글.
                업무용 소프트웨어 도구는 브랜드 이름이어도 직무에서 쓰는 기술로 본다.
                글이 그 기술 자체(새 버전, 사용법, 동작 원리, 사례)를 실제로 다룰 때만 고른다. 기술명이 일반 단어와 겹쳐 글이 그 기술을 다루는지 확실하지 않으면 제외한다.
                같은 기술은 한 번만, 가장 대표적인 글 하나만 고른다. 애매하면 고르지 말고, 적게 골라도 좋다. 최대 %d개.
                각 기술마다 다음을 정한다.
                - index: 그 글의 번호
                - tech_name: 널리 쓰이는 정식 영문 기술명 (예: "Kubernetes", "Spring Boot", "Jira", "Scrum"). 글 제목을 그대로 쓰지 않는다.
                  도구·방법론 자체의 이름만 쓰고, 괄호나 부제로 기능·세부 사용법을 붙이지 않는다 (예: "Jira (Sub-task under Epic)"가 아니라 "Jira").
                  글의 주제·팁·습관·문제 상황(예: "Scrum Habits", "Scope Creep Prevention")은 기술명이 아니므로 고르지 않는다.
                - summary: 이 기술이 무엇인지 취업 준비생이 이해할 수 있게 설명하는 한국어 한 문장 (%d자 이내). 글 제목이 아니라 기술 자체를 설명한다.
                - confidence: 0~1. 이 글이 취업 공고에 자주 등장하는 널리 알려진 기술을 직접 다루고 있어 취업 준비생이 학습할 가치가 확실하다고 얼마나 확신하는지. 0.9 이상은 누구나 아는 주류 기술일 때만 준다. 마이너·실험적·개인 프로젝트 이름, 오래되어 실무에서 거의 안 쓰는 기술, 특정 회사의 내부·부속 도구, 글이 그 기술을 직접 다루는지 애매한 경우는 0.7 이하로 낮게 준다.
                - jobs: 이 기술과 관련 깊은 직무 목록. 주어진 직무 목록의 id만 쓰고, relevance는 0~1 사이 숫자. 그 직무가 실무에서 실제로 쓰는 기술일 때만 넣고, 관련이 약하면 넣지 않는다.
                반드시 다음 JSON 객체 하나만 출력한다: {"items":[{"index":0,"tech_name":"","summary":"","confidence":0.9,"jobs":[{"job_id":1,"relevance":0.9}]}]}
                """.formatted(MAX_TECHS, MAX_SUMMARY_LENGTH / 2);
    }

    private String userPrompt(List<String> candidateTexts, List<JobDto> jobs) {
        StringBuilder sb = new StringBuilder("직무 목록:\n");
        for (JobDto job : jobs) {
            sb.append(job.getId()).append(": ").append(job.getJobName()).append('\n');
        }
        sb.append("\n글 목록:\n");
        for (int i = 0; i < candidateTexts.size(); i++) {
            sb.append(i).append(": ").append(candidateTexts.get(i)).append('\n');
        }
        return sb.toString();
    }

    /**
     * 기술명 끝의 괄호 설명을 뗀다 — LLM이 "Jira (Clear Done Column)"처럼 같은 기술을 기능별로 쪼개 내놓아도 한 기술로 보게 한다.
     * 예: "Amazon Web Services (AWS)" → "Amazon Web Services". 괄호만 있는 이름이면 원래 이름을 그대로 둔다.
     */
    static String baseTechName(String techName) {
        String trimmed = techName.trim();
        String base = TRAILING_QUALIFIER.matcher(trimmed).replaceFirst("").trim();
        return base.isEmpty() ? trimmed : base;
    }

    // LLM 출력은 신뢰하지 않는다 — 범위 밖 번호·모르는 직무·비정상 값은 버리고, 형식 자체가 틀리면 예외
    private List<TrendItem> parse(String response, int candidateCount, Set<Long> validJobIds)
            throws ExternalApiException {
        try {
            String content = JsonParser.parseString(response).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            JsonArray items = JsonParser.parseString(content).getAsJsonObject().getAsJsonArray("items");

            List<TrendItem> result = new ArrayList<>();
            Set<String> seenTech = new java.util.HashSet<>();
            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                int index = item.get("index").getAsInt();
                String techName = baseTechName(item.get("tech_name").getAsString());
                String summary = item.get("summary").getAsString().trim();
                if (index < 0 || index >= candidateCount || techName.isEmpty() || summary.isEmpty()
                        || techName.length() > MAX_TECH_NAME_LENGTH || !seenTech.add(techName.toLowerCase())) {
                    continue;
                }
                if (summary.length() > MAX_SUMMARY_LENGTH) {
                    summary = summary.substring(0, MAX_SUMMARY_LENGTH);
                }
                BigDecimal confidence = item.has("confidence") && !item.get("confidence").isJsonNull()
                        ? item.get("confidence").getAsBigDecimal().max(BigDecimal.ZERO).min(BigDecimal.ONE)
                        : BigDecimal.ZERO; // 확신도가 없으면 통과시키지 않도록 0으로 둔다
                Map<Long, BigDecimal> relevance = new LinkedHashMap<>();
                JsonArray jobs = item.getAsJsonArray("jobs");
                if (jobs != null) {
                    for (JsonElement j : jobs) {
                        long jobId = j.getAsJsonObject().get("job_id").getAsLong();
                        BigDecimal score = j.getAsJsonObject().get("relevance").getAsBigDecimal()
                                .max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
                        if (validJobIds.contains(jobId) && score.compareTo(MIN_RELEVANCE) >= 0) {
                            relevance.put(jobId, score);
                        }
                    }
                }
                result.add(new TrendItem(index, techName, summary, confidence, relevance));
            }
            return result;
        } catch (RuntimeException e) {
            // JsonSyntaxException, NPE, IllegalStateException, IndexOutOfBounds 등 응답 형식 오류 전부 (NFR-6)
            throw new ExternalApiException("LLM 응답 JSON 파싱 실패", e);
        }
    }
}
