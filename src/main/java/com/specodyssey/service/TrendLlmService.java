package com.specodyssey.service;

import com.google.gson.annotations.SerializedName;
import com.specodyssey.dto.JobDto;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.GroqLlmClient;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.LlmRetryPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groq LLM으로 트렌드 후보 글에서 기술명을 뽑고, 한 줄 설명과 관련 직무를 정리한다.
 * 관련 요구사항: FR-54 트렌드 기술 노출, FR-55 직무 연관 필터링
 * 관련 규칙: claude.md "LLM 응답은 JSON으로 받고 파싱 실패를 예외 처리한다" — 형식이 어긋나면 예외를 던져
 * 호출부(TrendCollectService)가 저장 없이 직전 데이터를 유지하게 한다. 서버 코드에서만 호출한다.
 *
 * 호출·재시도는 공용 LlmClient(GroqLlmClient)에 맡긴다 (2026-10-06 — 예전엔 이 클래스가 Groq를 직접 부르고
 * 400을 4번 연달아 재시도했다). 스케줄러 배치라 재시도는 LlmRetryPolicy.BATCH(429에 20·40·60초 대기)를 쓴다.
 * 후보 글이 매일 달라 같은 프롬프트가 거의 없으므로 캐시는 씌우지 않는다 — 실패하면 직전 데이터 유지로 충분하다.
 */
public class TrendLlmService {

    private static final double TEMPERATURE = 0.2;
    // gpt-oss는 추론에 토큰을 쓴다 — 후보가 많으면 출력이 잘려 형식 오류가 나므로 한도를 넉넉히 둔다
    private static final int MAX_COMPLETION_TOKENS = 8000;
    private static final int MAX_TECHS = 20;
    private static final int CHUNK_SIZE = 80;
    private static final int MAX_SUMMARY_LENGTH = 200;
    private static final int MAX_TECH_NAME_LENGTH = 100;
    private static final BigDecimal MIN_RELEVANCE = new BigDecimal("0.5");

    /** 정리 결과 1건. candidateIndex는 입력 후보 목록의 위치, jobRelevance는 job_id → 연관도(0~1), confidence는 "취업 준비생이 배울 만한 확실한 기술"이라는 LLM의 확신도(0~1). */
    public record TrendItem(int candidateIndex, String techName, String summary, BigDecimal confidence,
                           Map<Long, BigDecimal> jobRelevance) {
    }

    // LLM 응답 형식 — {"items":[{"index":0,"tech_name":"","summary":"","confidence":0.9,"jobs":[{"job_id":1,"relevance":0.9}]}]}
    static class Response {
        List<Item> items;
    }

    static class Item {
        Integer index;
        @SerializedName("tech_name")
        String techName;
        String summary;
        BigDecimal confidence;
        List<JobRelevance> jobs;
    }

    static class JobRelevance {
        @SerializedName("job_id")
        Long jobId;
        BigDecimal relevance;
    }

    private final LlmClient llm;

    public TrendLlmService() {
        this(GroqLlmClient.fromConfig().withSettings(TEMPERATURE, MAX_COMPLETION_TOKENS).withRetryPolicy(LlmRetryPolicy.BATCH));
    }

    // 테스트에서 StubLlmClient를 넣는다
    public TrendLlmService(LlmClient llm) {
        this.llm = llm;
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
        Response response = llm.completeJson(systemPrompt() + "\n\n" + userPrompt(candidateTexts, jobs), Response.class);
        return toItems(response, candidateTexts.size(), jobs.stream().map(JobDto::getId).collect(Collectors.toSet()));
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


    // LLM 출력은 신뢰하지 않는다 — 범위 밖 번호·모르는 직무·비정상 값은 버리고, 목록 자체가 없으면 예외 (NFR-6)
    static List<TrendItem> toItems(Response response, int candidateCount, Set<Long> validJobIds)
            throws ExternalApiException {
        if (response == null || response.items == null) {
            throw new ExternalApiException("LLM 응답에 items가 없습니다", null, LlmRetryPolicy.FORMAT_ERROR);
        }
        List<TrendItem> result = new ArrayList<>();
        Set<String> seenTech = new HashSet<>();
        for (Item item : response.items) {
            if (item == null || item.index == null || item.techName == null || item.summary == null) {
                continue;
            }
            int index = item.index;
            String techName = item.techName.trim();
            String summary = item.summary.trim();
            if (index < 0 || index >= candidateCount || techName.isEmpty() || summary.isEmpty()
                    || techName.length() > MAX_TECH_NAME_LENGTH || !seenTech.add(techName.toLowerCase())) {
                continue;
            }
            if (summary.length() > MAX_SUMMARY_LENGTH) {
                summary = summary.substring(0, MAX_SUMMARY_LENGTH);
            }
            BigDecimal confidence = item.confidence == null
                    ? BigDecimal.ZERO // 확신도가 없으면 통과시키지 않도록 0으로 둔다
                    : item.confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE);
            Map<Long, BigDecimal> relevance = new LinkedHashMap<>();
            if (item.jobs != null) {
                for (JobRelevance j : item.jobs) {
                    if (j == null || j.jobId == null || j.relevance == null) {
                        continue;
                    }
                    BigDecimal score = j.relevance.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
                    if (validJobIds.contains(j.jobId) && score.compareTo(MIN_RELEVANCE) >= 0) {
                        relevance.put(j.jobId, score);
                    }
                }
            }
            result.add(new TrendItem(index, techName, summary, confidence, relevance));
        }
        return result;
    }
}
