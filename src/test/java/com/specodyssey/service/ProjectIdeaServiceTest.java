package com.specodyssey.service;

import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ProjectIdeaService 테스트. 관련 요구사항: FR-32, FR-111
 * 실제 Groq 호출은 GroqLlmClient(+LlmRetryPolicy)가 맡으므로, 여기서는 StubLlmClient로
 * "LLM이 이렇게 응답했을 때 이 서비스가 제대로 검증·가공하는지"만 본다.
 */
class ProjectIdeaServiceTest {

    @Test
    void 정상_응답은_제목과_설명을_그대로_돌려준다() throws Exception {
        ProjectIdeaService service = new ProjectIdeaService(new StubLlmClient().register(
                ProjectIdeaService.ProjectIdea.class, "{\"title\":\"가계부 API\",\"description\":\"Spring Boot로 만든 가계부\"}"));

        ProjectIdeaService.ProjectIdea idea = service.suggest("백엔드 개발자", List.of("Spring Boot", "MySQL"));

        assertEquals("가계부 API", idea.title());
        assertEquals("Spring Boot로 만든 가계부", idea.description());
    }

    @Test
    void 제목이나_설명이_비어있으면_예외를_던진다() {
        ProjectIdeaService service = new ProjectIdeaService(new StubLlmClient().register(
                ProjectIdeaService.ProjectIdea.class, "{\"title\":\"\",\"description\":\"설명\"}"));

        assertThrows(ExternalApiException.class, () -> service.suggest("백엔드 개발자", List.of("Spring Boot")));
    }

    @Test
    void 제목과_설명이_길면_길이에_맞춰_잘린다() throws Exception {
        String longTitle = "가".repeat(120);
        String longDescription = "나".repeat(600);
        ProjectIdeaService service = new ProjectIdeaService(new StubLlmClient().register(
                ProjectIdeaService.ProjectIdea.class,
                "{\"title\":\"" + longTitle + "\",\"description\":\"" + longDescription + "\"}"));

        ProjectIdeaService.ProjectIdea idea = service.suggest("백엔드 개발자", List.of("Spring Boot"));

        assertEquals(100, idea.title().length());
        assertEquals(500, idea.description().length());
    }

    @Test
    void LLM_호출이_실패하면_예외를_그대로_올린다() {
        ProjectIdeaService service = new ProjectIdeaService(StubLlmClient.failing(429));

        assertThrows(ExternalApiException.class, () -> service.suggest("백엔드 개발자", List.of("Spring Boot")));
    }
}
