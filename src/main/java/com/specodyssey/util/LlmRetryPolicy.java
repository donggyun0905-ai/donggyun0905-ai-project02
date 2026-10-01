package com.specodyssey.util;

import java.time.Duration;

/**
 * LLM 호출 재시도 규칙 (FR-111 공통 처리). 어떤 실패를 몇 번, 얼마나 기다렸다가 다시 시도할지 한곳에서 정한다.
 *
 * - 429·498·5xx·-1(타임아웃·네트워크): 일시적 실패라 재시도한다 (claude.md 규칙)
 * - 400·FORMAT_ERROR: 한 번만 재시도한다 — claude.md "400은 재시도하지 않는다"의 예외.
 *   Groq는 모델이 JSON 형식을 어기면 400(JSON 검증 실패)을 돌려주는데, 샘플링 탓이라 다시 부르면 성공할 수 있다.
 *   ExternalApiClient가 응답 본문을 넘겨주지 않아 다른 400(잘못된 요청)과 구분할 수 없으므로 횟수를 1번으로 묶는다.
 * - 401·403·404 등 나머지: 다시 해도 같으므로 재시도하지 않는다.
 *
 * 화면 요청 안에서 호출되므로 오래 붙잡지 않는다 — 대기는 1초·2초로 짧게 두고, 누적 시간이 예산을 넘으면 그만둔다.
 */
public final class LlmRetryPolicy {

    /** 응답이 JSON이 아니거나 요청한 타입과 맞지 않을 때 쓰는 상태 코드 (Groq 문서의 422 "모델 환각"과 같은 의미) */
    public static final int FORMAT_ERROR = 422;

    public static final LlmRetryPolicy DEFAULT = new LlmRetryPolicy(3, 1_000, Duration.ofSeconds(60));

    private final int maxAttempts;
    private final long backoffMillis;
    private final Duration retryBudget;

    public LlmRetryPolicy(int maxAttempts, long backoffMillis, Duration retryBudget) {
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
        this.retryBudget = retryBudget;
    }

    /** attempt번째(1부터) 시도가 status로 실패했고 지금까지 elapsed가 걸렸을 때, 한 번 더 시도할지 */
    public boolean shouldRetry(int status, int attempt, Duration elapsed) {
        if (attempt >= maxAttempts || elapsed.compareTo(retryBudget) >= 0) {
            return false;
        }
        if (status == 400 || status == FORMAT_ERROR) {
            return attempt == 1;
        }
        return isTransient(status);
    }

    /** 시간이 지나면 풀릴 수 있는 실패인지 */
    public static boolean isTransient(int status) {
        return status == -1 || status == 429 || status == 498 || status >= 500;
    }

    /** attempt번째 실패 뒤 다음 시도까지 기다릴 시간 */
    public long backoffMillis(int attempt) {
        return backoffMillis * attempt;
    }
}
