package com.specodyssey.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.specodyssey.dto.JobDto;
import com.specodyssey.util.AppConfig;
import com.specodyssey.util.ExternalApiClient;
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
import java.util.stream.Collectors;

/**
 * Groq LLM으로 트렌드 후보 글에서 기술명을 뽑고, 한 줄 설명과 관련 직무를 정리한다.
 * 관련 요구사항: FR-54 트렌드 기술 노출, FR-55 직무 연관 필터링
 * 관련 규칙: claude.md "LLM 응답은 JSON으로 받고 파싱 실패를 예외 처리한다" — 형식이 어긋나면 예외를 던져
 * 호출부(TrendCollectService)가 저장 없이 직전 데이터를 유지하게 한다. 서버 코드에서만 호출한다.
 */
public class TrendLlmService {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_TECHS = 20;
    private static final int CHUNK_SIZE = 80;
    private static final int MAX_RETRIES = 4;
    private static final long RETRY_WAIT_MS = 20_000;
    private static final int MAX_SUMMARY_LENGTH = 200;
    private static final int MAX_TECH_NAME_LENGTH = 100;
    private static final BigDecimal MIN_RELEVANCE = new BigDecimal("0.5");

    /** 정리 결과 1건. candidateIndex는 입력 후보 목록의 위치, jobRelevance는 job_id → 연관도(0~1), confidence는 "취업 준비생이 배울 만한 확실한 기술"이라는 LLM의 확신도(0~1). */
    public record TrendItem(int candidateIndex, String techName, String summary, BigDecimal confidence,
                           Map<Long, BigDecimal> jobRelevance) {
    }

    /** 후보를 나눠 여러 번 호출하고 결과를 합친다. 같은 기술은 먼저 나온 것 하나만 남긴다. */
    public List<TrendItem> organize(List<String> candidateTexts, List<JobDto> jobs) throws ExternalApiException {
        List<TrendItem> all = new ArrayList<>();
        Set<String> seenTech = new HashSet<>();
        for (int offset = 0; offset < candidateTexts.size(); offset += CHUNK_SIZE) {
            List<String> chunk = candidateTexts.subList(offset, Math.min(offset + CHUNK_SIZE, candidateTexts.size()));
            for (TrendItem item : organizeChunk(chunk, jobs)) {
                if (seenTech.add(item.techName().toLowerCase())) {
                    all.add(new TrendItem(offset + item.candidateIndex(), item.techName(), item.summary(), item.confidence(),
                            item.jobRelevance()));
                }
            }
        }
        return all;
    }

    private List<TrendItem> organizeChunk(List<String> candidateTexts, List<JobDto> jobs) throws ExternalApiException {
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
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(candidateTexts, jobs))));

        String response = post(body, apiKey);
        return parse(response, candidateTexts.size(), jobs.stream().map(JobDto::getId).collect(Collectors.toSet()));
    }

    // 일시적 실패는 다시 시도한다.
    // - 429: Groq 무료 한도(분당 토큰) 초과 — 잠시 기다렸다가 재시도
    // - 400: 모델이 JSON 형식을 어긴 출력을 내면 Groq가 검증 실패로 400을 돌려준다(샘플링 탓의 간헐적 실패) — 바로 재시도
    private String post(Map<String, Object> body, String apiKey) throws ExternalApiException {
        for (int attempt = 1; ; attempt++) {
            try {
                return ExternalApiClient.postJson(ENDPOINT, body, Map.of("Authorization", "Bearer " + apiKey), TIMEOUT);
            } catch (ExternalApiException e) {
                int status = e.getStatusCode();
                if ((status != 429 && status != 400) || attempt >= MAX_RETRIES) {
                    throw e;
                }
                if (status == 429) {
                    try {
                        Thread.sleep(RETRY_WAIT_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
    }

    private String systemPrompt() {
        return """
                너는 IT 취업 준비생에게 오늘의 기술 트렌드를 알려주는 편집자다.
                번호가 붙은 글 목록에서, 취업 준비생이 학습해서 이력서에 쓸 수 있는 구체적인 IT 기술을 다루는 글만 골라라.
                포함: 프로그래밍 언어, 프레임워크, 라이브러리, 개발 도구, 데이터베이스, 클라우드·인프라 기술, 프로토콜, 개발 기법.
                제외: 기업·인물·제품 브랜드(예: NVIDIA, Meta), 하드웨어·기기·운영체제 제품, 인사·투자·인수·정치·사건 뉴스, 기술 자체가 아니라 그 기술을 사용한 개인 프로젝트나 잡담, 기술이 특정되지 않는 글.
                글이 그 기술 자체(새 버전, 사용법, 동작 원리, 사례)를 실제로 다룰 때만 고른다. 기술명이 일반 단어와 겹쳐 글이 그 기술을 다루는지 확실하지 않으면 제외한다.
                같은 기술은 한 번만, 가장 대표적인 글 하나만 고른다. 애매하면 고르지 말고, 적게 골라도 좋다. 최대 %d개.
                각 기술마다 다음을 정한다.
                - index: 그 글의 번호
                - tech_name: 널리 쓰이는 정식 영문 기술명 (예: "Kubernetes", "Spring Boot"). 글 제목을 그대로 쓰지 않는다.
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
                String techName = item.get("tech_name").getAsString().trim();
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
