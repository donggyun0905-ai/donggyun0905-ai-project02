package com.specodyssey.service.discovery;

import com.specodyssey.service.CachingLlmClient;
import com.specodyssey.service.LlmResult;
import com.specodyssey.service.discovery.JobDiscoveryScorer.Recommendation;
import com.specodyssey.util.AiNotices;
import com.specodyssey.util.GroqLlmClient;
import com.specodyssey.util.LlmClient;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 직무 추천 이유를 LLM 문장으로 다듬는다. 관련 요구사항: FR-35·38("AI가 종합"), FR-111
 *
 * 계산기(JobDiscoveryScorer)가 만든 점수·겹치는 기술·기본 문장을 근거로 주고, 후보 전체를 한 번의 호출로 받는다.
 * 실패(키 없음·타임아웃·형식 오류)하거나 항목이 이상하면 그 항목은 계산기 기본 문장을 그대로 둔다 — 설문 제출은 막지 않는다.
 * 같은 근거면 같은 프롬프트라 CachingLlmClient가 재사용하고, 호출이 실패해도 직전 결과로 대체한다.
 */
public class RecommendationDescriber {

    static final int MAX_REASON_LENGTH = 300;
    static final String UNAVAILABLE_NOTICE = "AI 응답을 받지 못해 추천 이유를 기본 설명으로 보여드립니다.";
    private static final Logger LOG = Logger.getLogger(RecommendationDescriber.class.getName());

    private final LlmClient llm;

    public RecommendationDescriber() {
        this(new CachingLlmClient(GroqLlmClient.fromConfig()));
    }

    public RecommendationDescriber(LlmClient llm) {
        this.llm = llm;
    }

    /** recommendations의 reason을 LLM 문장으로 바꾼다. 바꾸지 못한 항목은 그대로 둔다. */
    public void describe(List<Recommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return;
        }
        LlmResult<Response> result;
        try {
            result = CachingLlmClient.completeWithStatus(llm, prompt(recommendations), Response.class);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "추천 이유 LLM 생성 실패 — 기본 문장을 유지합니다", e);
            AiNotices.add(UNAVAILABLE_NOTICE);
            return;
        }
        // FR-111 — 대체했으면 다음 화면에 안내한다. 시간이 지나면 풀리는 실패면 "다시 시도" 버튼도 단다
        AiNotices.RetryTarget retry = result.isRetryable() ? AiNotices.RetryTarget.DISCOVERY : null;
        if (result.isFallback()) {
            AiNotices.add("AI 응답을 받지 못해 " + result.getCachedAtText() + "에 만든 직전 추천 이유를 보여드립니다.", retry);
        } else if (result.isUnavailable()) {
            LOG.log(Level.WARNING, "추천 이유 LLM 생성 실패 — 기본 문장을 유지합니다");
            AiNotices.add(retry != null ? UNAVAILABLE_NOTICE + " 잠시 후 다시 시도해 주세요." : UNAVAILABLE_NOTICE, retry);
            return;
        }
        Response response = result.getValue();
        if (response == null || response.items == null) {
            return;
        }
        Map<Integer, Recommendation> byRank = new HashMap<>();
        for (Recommendation r : recommendations) {
            byRank.put(r.rankOrder, r);
        }
        for (Item item : response.items) {
            if (item == null || item.reason == null) {
                continue;
            }
            Recommendation target = byRank.get(item.rank);
            String reason = item.reason.strip();
            if (target == null || reason.isEmpty()) {
                continue;
            }
            target.reason = reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
        }
    }

    static String prompt(List<Recommendation> recommendations) {
        StringBuilder candidates = new StringBuilder();
        for (Recommendation r : recommendations) {
            candidates.append(String.format(Locale.ROOT, "- rank %d: %s (%s)%n", r.rankOrder, r.jobName, r.category))
                    .append(String.format(Locale.ROOT, "  설문 적합도 %.0f점/100%n", r.surveyScore * 100))
                    .append(r.specScore == null
                            ? "  보유 기술 적합도: 이 직무의 요구 기술 데이터 없음\n"
                            : String.format(Locale.ROOT, "  보유 기술 적합도 %.0f점/100%n", r.specScore * 100))
                    .append("  이미 가진 관련 기술: ")
                    .append(r.matchedSkills.isEmpty() ? "없음" : String.join(", ", r.matchedSkills)).append('\n');
            if (r.major != null && r.majorScore != null) {
                // FR-38 ② 전공도 근거로 준다 (2026-10-06) — 0~100은 6개 계열 중 상대적인 가까움
                candidates.append(String.format(Locale.ROOT, "  전공: %s (이 분야와 가까운 정도 %.0f점/100)%n",
                        r.major, r.majorScore * 100));
            }
            candidates.append("  기본 설명: ").append(r.reason == null ? "" : r.reason).append('\n');
        }
        return """
                너는 IT 취업 준비생의 진로 상담가다. 흥미·성향 설문과 보유 기술로 계산한 후보 직무 목록을 보고,
                각 직무를 왜 추천하는지 취업 준비생에게 말하듯 한국어로 2문장 이내(%d자 이내)로 설명해라.
                - 아래에 주어진 점수와 "이미 가진 관련 기술", 전공(주어졌을 때만)만 근거로 쓴다. 목록에 없는 기술을 이미 가졌다고 쓰지 않는다.
                - 전공이 그 분야와 가까우면(70점 이상) 전공을 살릴 수 있다는 점을 짧게 언급해도 좋다.
                - 이미 가진 기술이 없으면 설문 성향을 근거로 설명하고, 첫걸음으로 무엇을 배우면 좋은지 한 가지 덧붙인다.
                - 점수 숫자를 그대로 나열하지 말고 자연스러운 문장으로 쓴다.
                반드시 다음 JSON 객체 하나만 출력한다: {"items":[{"rank":1,"reason":""}]}

                후보 직무:
                %s""".formatted(MAX_REASON_LENGTH, candidates);
    }

    static class Response {
        List<Item> items;
    }

    static class Item {
        int rank;
        String reason;
    }
}
