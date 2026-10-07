package com.specodyssey.service.discovery;

import com.specodyssey.service.discovery.JobDiscoveryScorer.Recommendation;
import com.specodyssey.service.discovery.JobSummaryWriter.JobSummary;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.LlmClient;
import com.specodyssey.util.StubLlmClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** FR-35 직무 요약. LLM 없이 StubLlmClient로 검증한다 (DB 불필요). */
class JobSummaryWriterTest {

    private static Recommendation rec(long jobId, String name, String category) {
        Recommendation r = new Recommendation();
        r.jobId = jobId;
        r.jobName = name;
        r.category = category;
        return r;
    }

    private static final Map<Long, List<String>> SKILLS = Map.of(
            1L, List.of("Java", "Spring Boot", "MySQL"),
            2L, List.of("Python", "SQL"));

    @Test
    void LLM이_준_하는_일과_전망에_DB_요구_기술을_합친다() {
        var recs = List.of(rec(1, "백엔드 개발자", "BACKEND"));
        LlmClient llm = new StubLlmClient().register(JobSummaryWriter.Response.class,
                "{\"duties\":\"서버를 만듭니다.\",\"outlook\":\"꾸준히 찾는 직무입니다.\"}");

        new JobSummaryWriter(llm).summarize(recs, SKILLS);

        JobSummary s = JobSummaryWriter.parse(recs.get(0).summaryJson);
        assertEquals("서버를 만듭니다.", s.getDuties());
        assertEquals("꾸준히 찾는 직무입니다.", s.getOutlook());
        assertEquals(List.of("Java", "Spring Boot", "MySQL"), s.getSkills());
        assertTrue(s.isAi());
    }

    @Test
    void LLM이_실패하면_계열별_기본_문구로_채운다() {
        var recs = List.of(rec(2, "데이터 분석가", "DATA"));

        new JobSummaryWriter(StubLlmClient.failing(503)).summarize(recs, SKILLS);

        JobSummary s = JobSummaryWriter.parse(recs.get(0).summaryJson);
        assertEquals(JobSummaryWriter.DEFAULTS.get("DATA")[0], s.getDuties());
        assertEquals(JobSummaryWriter.DEFAULTS.get("DATA")[1], s.getOutlook());
        assertEquals(List.of("Python", "SQL"), s.getSkills(), "필요 역량은 LLM과 상관없이 DB에서 나온다");
        assertFalse(s.isAi());
    }

    @Test
    void 첫_호출이_실패하면_나머지_직무는_호출하지_않는다() {
        List<String> prompts = new ArrayList<>();
        LlmClient llm = new LlmClient() {
            @Override
            public <T> T completeJson(String prompt, Class<T> type) throws ExternalApiException {
                prompts.add(prompt);
                throw new ExternalApiException("타임아웃 흉내", null, -1);
            }
        };
        var recs = List.of(rec(1, "백엔드 개발자", "BACKEND"), rec(2, "데이터 분석가", "DATA"),
                rec(3, "보안 엔지니어", "SECURITY"));

        new JobSummaryWriter(llm).summarize(recs, SKILLS);

        assertEquals(1, prompts.size(), "타임아웃이 직무 수만큼 쌓이면 안 된다");
        assertTrue(recs.stream().allMatch(r -> JobSummaryWriter.parse(r.summaryJson) != null));
    }

    @Test
    void 전망에_수치_통계가_섞이면_전망만_기본_문구로_바꾼다() {
        JobSummaryWriter.Response ai = new JobSummaryWriter.Response();
        ai.duties = "서버를 만듭니다.";
        ai.outlook = "평균 연봉이 4,500만 원이고 매년 20% 성장합니다.";

        JobSummary s = JobSummaryWriter.build("BACKEND", List.of(), ai);

        assertEquals("서버를 만듭니다.", s.getDuties());
        assertEquals(JobSummaryWriter.DEFAULTS.get("BACKEND")[1], s.getOutlook());
        assertFalse(s.isAi(), "일부라도 기본 문구면 AI 설명으로 표시하지 않는다");
    }

    @Test
    void 숫자가_있어도_통계가_아니면_그대로_둔다() {
        JobSummaryWriter.Response ai = new JobSummaryWriter.Response();
        ai.duties = "웹 화면을 만듭니다.";
        ai.outlook = "HTML5와 ES6 이후 표준이 자리 잡아 기본기가 중요합니다.";

        assertTrue(JobSummaryWriter.build("FRONTEND", List.of(), ai).isAi());
    }

    @Test
    void 빈_응답이나_너무_긴_문장도_안전하게_처리한다() {
        JobSummaryWriter.Response ai = new JobSummaryWriter.Response();
        ai.duties = "가".repeat(500);
        ai.outlook = "  ";

        JobSummary s = JobSummaryWriter.build("PM", List.of(), ai);

        assertEquals(JobSummaryWriter.MAX_TEXT_LENGTH, s.getDuties().length());
        assertEquals(JobSummaryWriter.DEFAULTS.get("PM")[1], s.getOutlook());
    }

    @Test
    void 필요_역량은_최대_6개_중복_없이() {
        var recs = List.of(rec(9, "풀스택", "BACKEND"));
        Map<Long, List<String>> many = Map.of(9L, List.of("A", "B", "A", "C", "D", "E", "F", "G"));

        new JobSummaryWriter(StubLlmClient.failing(503)).summarize(recs, many);

        assertEquals(List.of("A", "B", "C", "D", "E", "F"), JobSummaryWriter.parse(recs.get(0).summaryJson).getSkills());
    }

    @Test
    void 모르는_계열이나_요구_기술_없음도_요약이_나온다() {
        var recs = List.of(rec(7, "새 직무", "UNKNOWN"));

        new JobSummaryWriter(StubLlmClient.failing(503)).summarize(recs, Map.of());

        JobSummary s = JobSummaryWriter.parse(recs.get(0).summaryJson);
        assertNotNull(s.getDuties());
        assertTrue(s.getSkills().isEmpty());
    }

    @Test
    void 프롬프트에_사용자_정보가_없어_직무가_같으면_같은_프롬프트다() {
        // 캐시가 사용자 사이에서 재사용되려면 직무 정보만으로 프롬프트가 결정돼야 한다
        String a = JobSummaryWriter.prompt("백엔드 개발자", "BACKEND", List.of("Java"));
        String b = JobSummaryWriter.prompt("백엔드 개발자", "BACKEND", List.of("Java"));

        assertEquals(a, b);
        assertTrue(a.contains("숫자와 통계"));
    }

    @Test
    void 예전_추천처럼_요약이_비었거나_깨졌으면_null() {
        assertNull(JobSummaryWriter.parse(null));
        assertNull(JobSummaryWriter.parse(""));
        assertNull(JobSummaryWriter.parse("{깨진"));
    }

    // ---- 최근 공고 근거 · 전공 배지 (2026-10-07) ----

    private static JobTrendDigest backendTrend() {
        List<com.specodyssey.dao.InsightDao.TrendRow> rows = new ArrayList<>();
        String[] names = {"Java", "Spring Boot", "MySQL", "Docker", "AWS"};
        double[] aug = {60, 50, 40, 20, 15};
        double[] sep = {55, 50, 41, 32, 21};
        for (int i = 0; i < names.length; i++) {
            rows.add(new com.specodyssey.dao.InsightDao.TrendRow(i, names[i], "202608", java.math.BigDecimal.valueOf(aug[i])));
            rows.add(new com.specodyssey.dao.InsightDao.TrendRow(i, names[i], "202609", java.math.BigDecimal.valueOf(sep[i])));
        }
        return JobTrendDigest.from(rows);
    }

    @Test
    void 트렌드가_있으면_프롬프트에_근거로_넣고_요약에도_저장한다() {
        List<String> prompts = new ArrayList<>();
        LlmClient llm = new LlmClient() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T completeJson(String prompt, Class<T> type) {
                prompts.add(prompt);
                JobSummaryWriter.Response r = new JobSummaryWriter.Response();
                r.duties = "서버를 만듭니다.";
                r.outlook = "Docker 같은 배포 기술을 찾는 곳이 늘고 있습니다.";
                return (T) r;
            }
        };
        var recs = List.of(rec(1, "백엔드 개발자", "BACKEND"));

        new JobSummaryWriter(llm).summarize(recs, SKILLS, Map.of(1L, backendTrend()));

        assertTrue(prompts.get(0).contains("지난달보다 언급이 늘어난 기술: Docker"), prompts.get(0));
        JobSummary s = JobSummaryWriter.parse(recs.get(0).summaryJson);
        assertEquals("9월", s.getTrend().getMonth());
        assertEquals("Docker", s.getTrend().getRising().get(0).getName());
        assertEquals(12, s.getTrend().getRising().get(0).getChange());
    }

    @Test
    void 트렌드가_없는_직무는_근거_없음으로_묻는다() {
        String prompt = JobSummaryWriter.prompt("UI 개발자", "FRONTEND", List.of(), null);

        assertTrue(prompt.contains("최근 공고 근거: 없음"));
    }

    @Test
    void LLM이_실패해도_트렌드와_전공_배지는_남는다() {
        Recommendation r = rec(1, "데이터 분석가", "DATA");
        r.closeMajor = "통계학과";

        new JobSummaryWriter(StubLlmClient.failing(503)).summarize(List.of(r), SKILLS, Map.of(1L, backendTrend()));

        JobSummary s = JobSummaryWriter.parse(r.summaryJson);
        assertEquals("통계학과", s.getMajor());
        assertNotNull(s.getTrend(), "트렌드는 DB 데이터라 LLM과 상관없이 보여 준다");
        assertFalse(s.isAi());
    }

    @Test
    void 전공_배지는_프롬프트에_들어가지_않아_캐시를_나눠_쓴다() {
        List<String> prompts = new ArrayList<>();
        LlmClient llm = new LlmClient() {
            @Override
            public <T> T completeJson(String prompt, Class<T> type) throws ExternalApiException {
                prompts.add(prompt);
                throw new ExternalApiException("실패", null, 400);
            }
        };
        Recommendation withMajor = rec(1, "백엔드 개발자", "BACKEND");
        withMajor.closeMajor = "컴퓨터공학과";

        new JobSummaryWriter(llm).summarize(List.of(withMajor), SKILLS);
        new JobSummaryWriter(llm).summarize(List.of(rec(1, "백엔드 개발자", "BACKEND")), SKILLS);

        assertEquals(prompts.get(0), prompts.get(1));
    }
}
