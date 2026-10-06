package com.specodyssey.service.discovery;

import com.specodyssey.service.discovery.JobDiscoveryScorer.Recommendation;
import com.specodyssey.util.AiNotices;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LLM 없이 StubLlmClient로 검증한다 (DB 불필요). */
class RecommendationDescriberTest {

    private static Recommendation rec(int rank, String job, String reason, String... skills) {
        Recommendation r = new Recommendation();
        r.rankOrder = rank;
        r.jobName = job;
        r.category = "BACKEND";
        r.surveyScore = 0.8;
        r.specScore = 0.5;
        r.matchedSkills.addAll(List.of(skills));
        r.reason = reason;
        return r;
    }

    @Test
    void LLM이_준_문장으로_순위별_추천_이유를_바꾼다() {
        var recs = List.of(rec(1, "백엔드 개발자", "기본1", "Java"), rec(2, "API 개발자", "기본2"));
        LlmClient llm = new StubLlmClient().register(RecommendationDescriber.Response.class,
                "{\"items\":[{\"rank\":2,\"reason\":\"API 설계에 관심이 많아요.\"},{\"rank\":1,\"reason\":\"Java를 이미 다뤄 봤어요.\"}]}");

        new RecommendationDescriber(llm).describe(recs);

        assertEquals("Java를 이미 다뤄 봤어요.", recs.get(0).reason);
        assertEquals("API 설계에 관심이 많아요.", recs.get(1).reason);
    }

    @Test
    void LLM이_실패하면_기본_문장을_그대로_둔다() {
        var recs = List.of(rec(1, "백엔드 개발자", "기본1"));

        new RecommendationDescriber(StubLlmClient.failing(503)).describe(recs);

        assertEquals("기본1", recs.get(0).reason);
    }

    // FR-111 — 기본 문장으로 대체했으면 다음 화면에 안내가 뜨도록 남긴다
    @Test
    void LLM이_실패하면_기본_설명으로_대체했다는_안내를_남긴다() {
        AiNotices.clear();

        new RecommendationDescriber(StubLlmClient.failing(503)).describe(List.of(rec(1, "백엔드 개발자", "기본1")));

        List<AiNotices.Notice> notices = AiNotices.drain();
        assertEquals(1, notices.size());
        assertEquals(RecommendationDescriber.UNAVAILABLE_NOTICE + " 잠시 후 다시 시도해 주세요.", notices.get(0).getMessage());
        assertEquals("DISCOVERY", notices.get(0).getRetryTarget(), "일시적 실패면 다시 시도 버튼을 띄운다");
    }

    @Test
    void 다시_시도해도_안_되는_실패는_재시도_문구를_붙이지_않는다() {
        AiNotices.clear();

        new RecommendationDescriber(StubLlmClient.failing(401)).describe(List.of(rec(1, "백엔드 개발자", "기본1")));

        List<AiNotices.Notice> notices = AiNotices.drain();
        assertEquals(1, notices.size());
        assertEquals(RecommendationDescriber.UNAVAILABLE_NOTICE, notices.get(0).getMessage());
        assertFalse(notices.get(0).isRetryable(), "키 오류처럼 다시 해도 안 되는 실패는 버튼을 띄우지 않는다");
    }

    @Test
    void 성공하면_안내를_남기지_않는다() {
        AiNotices.clear();
        LlmClient llm = new StubLlmClient().register(RecommendationDescriber.Response.class,
                "{\"items\":[{\"rank\":1,\"reason\":\"Java를 이미 다뤄 봤어요.\"}]}");

        new RecommendationDescriber(llm).describe(List.of(rec(1, "백엔드 개발자", "기본1")));

        assertTrue(AiNotices.drain().isEmpty());
    }

    @Test
    void 없는_순위나_빈_문장은_무시하고_너무_길면_자른다() {
        var recs = List.of(rec(1, "백엔드 개발자", "기본1"), rec(2, "API 개발자", "기본2"));
        String longReason = "가".repeat(RecommendationDescriber.MAX_REASON_LENGTH + 50);
        LlmClient llm = new StubLlmClient().register(RecommendationDescriber.Response.class,
                "{\"items\":[{\"rank\":9,\"reason\":\"없는 순위\"},{\"rank\":2,\"reason\":\"   \"},{\"rank\":1,\"reason\":\"" + longReason + "\"}]}");

        new RecommendationDescriber(llm).describe(recs);

        assertEquals(RecommendationDescriber.MAX_REASON_LENGTH, recs.get(0).reason.length());
        assertEquals("기본2", recs.get(1).reason);
    }

    @Test
    void 추천이_없으면_LLM을_부르지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        LlmClient counting = new LlmClient() {
            @Override
            public <T> T completeJson(String prompt, Class<T> type) throws ExternalApiException {
                calls.incrementAndGet();
                throw new ExternalApiException("호출되면 안 됨", null, 500);
            }
        };

        new RecommendationDescriber(counting).describe(List.of());

        assertEquals(0, calls.get());
    }

    @Test
    void 프롬프트에는_계산기_근거만_들어간다() {
        String prompt = RecommendationDescriber.prompt(List.of(rec(1, "백엔드 개발자", "기본1", "Java", "Spring")));

        assertTrue(prompt.contains("백엔드 개발자"));
        assertTrue(prompt.contains("Java, Spring"));
        assertTrue(prompt.contains("설문 적합도 80점"));
        assertFalse(prompt.contains("null"));
    }
}
