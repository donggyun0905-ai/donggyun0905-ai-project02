package com.specodyssey.service;

import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.LlmClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 로드맵 한 라운드(기술 5개)를 채울 만큼 부족한 기술이 없을 때, 이미 어느 정도 갖춘 직무 요구 기술 중
 * "더 깊게 파면 좋을 기술"을 LLM(Groq)에게 고르게 한다(2026-10-01, 사용자 요청).
 * 후보는 호출부가 직무 요구 기술(DB)에서 미리 추려 넘기고, LLM 응답은 그 후보 이름과 정확히 일치하는
 * 것만 인정한다 — 없는 기술을 지어내도 로드맵에 들어가지 않는다. LLM이 없거나(null) 실패하면 예외를
 * 던져 호출부가 중요도 순으로 대체하게 한다(FR-111, 로드맵 생성은 절대 막지 않는다).
 */
public class SkillDeepenService {

    public record Picks(List<String> skills) {
    }

    private final LlmClient llm;

    /** llm이 null이면 항상 실패(=호출부가 중요도 순 대체)한다 — 테스트·LLM 미설정 환경용. */
    public SkillDeepenService(LlmClient llm) {
        this.llm = llm;
    }

    /** candidateNames 중에서 count개 이하를 골라 이름(후보와 동일한 표기)으로 돌려준다. */
    public List<String> pick(String jobName, List<String> alreadyPlannedNames, List<String> candidateNames,
            int count) throws ExternalApiException {
        if (llm == null) {
            throw new ExternalApiException("LLM이 설정되지 않았습니다", null, -1);
        }
        String prompt = """
                너는 취업 준비생의 학습 계획을 돕는 멘토다.
                목표 직무와 이미 계획에 넣은 기술, 후보 기술 목록을 보고, 후보 중에서 이 직무에 가장 도움이 되도록
                더 깊게 공부할 기술을 정확히 %d개 이하로 골라라. 후보에 없는 기술은 절대 만들지 마라.
                반드시 다음 JSON 객체 하나만 출력한다: {"skills":["후보 기술명", ...]}

                목표 직무: %s
                이미 계획에 넣은 기술: %s
                후보 기술: %s
                """.formatted(count, jobName, String.join(", ", alreadyPlannedNames), String.join(", ", candidateNames));
        Picks picks = llm.completeJson(prompt, Picks.class);
        if (picks == null || picks.skills() == null) {
            throw new ExternalApiException("LLM이 기술 목록을 반환하지 않았습니다", null, -1);
        }
        // 후보 표기로 되돌려서(대소문자·공백 차이 흡수) 후보에 없는 이름은 버린다.
        List<String> result = new ArrayList<>();
        for (String picked : picks.skills()) {
            if (picked == null) {
                continue;
            }
            for (String candidate : candidateNames) {
                if (candidate.trim().toLowerCase(Locale.ROOT).equals(picked.trim().toLowerCase(Locale.ROOT))
                        && !result.contains(candidate)) {
                    result.add(candidate);
                    break;
                }
            }
            if (result.size() >= count) {
                break;
            }
        }
        return result;
    }
}
