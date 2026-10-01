package com.specodyssey.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.specodyssey.util.AppConfig;
import com.specodyssey.util.ExternalApiClient;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 목표 직무 + 부족 기술 목록을 주면, 신입 개발자가 2~4주 안에 혼자 완성할 수 있는 구체적인
 * 프로젝트 아이디어 1개를 Groq LLM에게 받는다. 관련 요구사항: FR-32(로드맵 PROJECT 단계),
 * FR-111(LLM 실패 시 대체 문구)
 * TrendLlmService와 같은 Groq 호출 패턴(재시도, JSON 강제 응답, 파싱 실패 시 예외)을 재사용한다.
 * 관련 규칙: claude.md "LLM 응답은 JSON으로 받고 파싱 실패를 예외 처리한다" — 실패하면 예외를 던져
 * 호출부(RoadmapService)가 로드맵 생성 자체는 막지 않고 기존 고정 문구로 조용히 대체하게 한다.
 */
public class ProjectIdeaService {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RETRIES = 4;
    private static final long RETRY_WAIT_MS = 20_000;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    public record ProjectIdea(String title, String description) {
    }

    public ProjectIdea suggest(String jobName, List<String> missingSkillNames) throws ExternalApiException {
        String apiKey = AppConfig.get("GROQ_API_KEY");
        if (apiKey == null) {
            throw new ExternalApiException("GROQ_API_KEY가 설정되지 않았습니다 (config.properties 또는 환경변수)", null);
        }
        String model = AppConfig.get("GROQ_MODEL");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model == null ? DEFAULT_MODEL : model);
        body.put("temperature", 0.4);
        body.put("reasoning_effort", "low");
        body.put("max_completion_tokens", 1000);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(jobName, missingSkillNames))));

        String response = post(body, apiKey);
        return parse(response);
    }

    // 일시적 실패는 다시 시도한다 — TrendLlmService.post()와 동일한 판단.
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
                너는 취업 준비생에게 포트폴리오 프로젝트를 제안하는 멘토다.
                주어진 목표 직무와 부족한 기술 목록을 보고, 신입 개발자가 2~4주 안에 혼자 완성할 수 있는
                구체적인 프로젝트 아이디어를 딱 1개만 제안해라.
                - title: 프로젝트 제목 (한국어, 간결하게, %d자 이내)
                - description: 무엇을 만드는지, 어떤 기능을 포함하는지, 주어진 부족 기술을 실제로 어떻게
                  써보게 되는지 구체적으로 설명하는 한국어 문단 (%d자 이내)
                반드시 다음 JSON 객체 하나만 출력한다: {"title":"","description":""}
                """.formatted(MAX_TITLE_LENGTH, MAX_DESCRIPTION_LENGTH);
    }

    private String userPrompt(String jobName, List<String> missingSkillNames) {
        return "목표 직무: " + jobName + "\n부족한 기술: " + String.join(", ", missingSkillNames);
    }

    // LLM 출력은 신뢰하지 않는다 — 형식 자체가 틀리거나 비어있으면 예외.
    private ProjectIdea parse(String response) throws ExternalApiException {
        try {
            String content = JsonParser.parseString(response).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            String title = obj.get("title").getAsString().trim();
            String description = obj.get("description").getAsString().trim();
            if (title.isEmpty() || description.isEmpty()) {
                throw new IllegalStateException("LLM이 빈 제목/설명을 반환했습니다");
            }
            if (title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH);
            }
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, MAX_DESCRIPTION_LENGTH);
            }
            return new ProjectIdea(title, description);
        } catch (RuntimeException e) {
            // JsonSyntaxException, NPE, IllegalStateException 등 응답 형식 오류 전부 (NFR-6)
            throw new ExternalApiException("LLM 응답 JSON 파싱 실패", e);
        }
    }
}
