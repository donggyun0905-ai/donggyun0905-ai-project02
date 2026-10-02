package com.specodyssey.service;

import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.GroqLlmClient;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.LlmRetryPolicy;

import java.util.List;

/**
 * 목표 직무 + 부족 기술 목록을 주면, 신입 개발자가 2~4주 안에 혼자 완성할 수 있는 구체적인
 * 프로젝트 아이디어 1개를 Groq LLM에게 받는다. 관련 요구사항: FR-32(로드맵 PROJECT 단계),
 * FR-111(LLM 실패 시 대체 문구)
 * 관련 규칙: claude.md "LLM 응답은 JSON으로 받고 파싱 실패를 예외 처리한다" — 실패하면 예외를 던져
 * 호출부(RoadmapService)가 로드맵 생성 자체는 막지 않고 기존 고정 문구로 조용히 대체하게 한다.
 *
 * 호출·재시도는 공용 LlmClient(GroqLlmClient + LlmRetryPolicy)에 맡긴다(2026-10-01, youngjun 제안
 * 반영) — 예전엔 이 클래스가 직접 HTTP 재시도를 돌렸는데, RoadmapServiceTest처럼 매번 실제 Groq를
 * 부르는 테스트가 429를 만나면 20·40·60초씩 기다려 전체 테스트가 1시간 넘게 걸리고 Groq 무료
 * 한도도 같이 소모됐다. 테스트에서는 StubLlmClient로 바꿔 끼울 수 있다.
 */
public class ProjectIdeaService {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    public record ProjectIdea(String title, String description) {
    }

    private final LlmClient llm;

    public ProjectIdeaService() {
        this(GroqLlmClient.fromConfig());
    }

    public ProjectIdeaService(LlmClient llm) {
        this.llm = llm;
    }

    public ProjectIdea suggest(String jobName, List<String> missingSkillNames) throws ExternalApiException {
        String prompt = systemPrompt() + "\n\n" + userPrompt(jobName, missingSkillNames);
        ProjectIdea idea = llm.completeJson(prompt, ProjectIdea.class);
        return validate(idea);
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

    // LLM 출력은 신뢰하지 않는다 — 형식 자체가 틀리거나 비어있으면 예외 (NFR-6)
    private ProjectIdea validate(ProjectIdea idea) throws ExternalApiException {
        if (idea == null || idea.title() == null || idea.description() == null
                || idea.title().isBlank() || idea.description().isBlank()) {
            throw new ExternalApiException("LLM이 빈 제목/설명을 반환했습니다", null, LlmRetryPolicy.FORMAT_ERROR);
        }
        String title = idea.title().trim();
        String description = idea.description().trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return new ProjectIdea(title, description);
    }
}
