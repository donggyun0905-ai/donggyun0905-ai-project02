package com.specodyssey.service;

import com.specodyssey.util.AiNotices;
import com.specodyssey.util.DBUtil;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // FR-111 — 직전 아이디어로 대체하면 다음 화면에 안내가 뜨도록 남긴다 (캐시 테이블을 쓰므로 DB 필요)
    @Test
    void 실패해서_직전_아이디어로_대체하면_안내를_남긴다() throws Exception {
        String job = "안내테스트 직무 " + System.nanoTime();
        List<String> skills = List.of("Spring Boot");
        ProjectIdeaService ok = new ProjectIdeaService(new CachingLlmClient(new StubLlmClient().register(
                ProjectIdeaService.ProjectIdea.class, "{\"title\":\"직전 아이디어\",\"description\":\"설명\"}")));
        String key = CachingLlmClient.requestKey(ok.prompt(job, skills), ProjectIdeaService.ProjectIdea.class);
        try {
            ok.suggest(job, skills);
            AiNotices.clear();

            ProjectIdeaService down = new ProjectIdeaService(new CachingLlmClient(StubLlmClient.failing(503), Duration.ZERO));
            ProjectIdeaService.ProjectIdea idea = down.suggest(job, skills);

            assertEquals("직전 아이디어", idea.title());
            List<AiNotices.Notice> notices = AiNotices.drain();
            assertEquals(1, notices.size());
            assertTrue(notices.get(0).getMessage().contains("직전 프로젝트 추천"), notices::toString);
        } finally {
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement p = conn.prepareStatement(
                         "DELETE FROM EXTERNAL_API_CACHE WHERE api_type = ? AND request_key = ?")) {
                p.setString(1, CachingLlmClient.API_TYPE);
                p.setString(2, key);
                p.executeUpdate();
            }
        }
    }

    @Test
    void 프로젝트_추천이_실패해_고정_문구로_대체하면_로드맵_생성기가_안내를_남긴다() throws Exception {
        RoadmapGenerator generator = new RoadmapGenerator(
                new ProjectIdeaService(StubLlmClient.failing(503)), new SkillDeepenService(null));
        AiNotices.clear();

        String reason = generator.buildProjectReason(null, List.of());

        assertTrue(reason.startsWith(RoadmapGenerator.PROJECT_FALLBACK_PREFIX), reason);
        List<AiNotices.Notice> notices = AiNotices.drain();
        assertEquals(1, notices.size());
        assertEquals(RoadmapGenerator.PROJECT_FALLBACK_NOTICE, notices.get(0).getMessage());
        assertEquals("ROADMAP_PROJECT", notices.get(0).getRetryTarget(), "503은 일시적 실패라 다시 시도 버튼을 띄운다");
    }

    @Test
    void 키_오류처럼_다시_해도_안_되는_실패는_로드맵_다시_시도_버튼을_띄우지_않는다() throws Exception {
        RoadmapGenerator generator = new RoadmapGenerator(
                new ProjectIdeaService(StubLlmClient.failing(401)), new SkillDeepenService(null));
        AiNotices.clear();

        generator.buildProjectReason(null, List.of());

        assertTrue(AiNotices.drain().stream().noneMatch(AiNotices.Notice::isRetryable));
    }
}
