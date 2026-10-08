package com.specodyssey.util;

import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * 외부 API가 계속 실패할 때 호출을 끊는다 — 서킷 브레이커 (2026-10-08).
 * 관련 요구사항: FR-111 AI API 실패 대응 · FR-112 외부 데이터 조회 실패 대응
 *
 * 지금까지는 실패하면 재시도 정책에 따라 몇 번 더 보내고, 다음 요청에서 또 처음부터 재시도했다.
 * 상대가 완전히 죽었거나 권한이 막힌 상황(깃허브 403, 고용24 장애)에서는 <b>사용자마다·요청마다</b>
 * 같은 실패를 반복하며 타임아웃만큼 기다린다. 서킷 브레이커는 연속 실패가 쌓이면 일정 시간 아예
 * 보내지 않고 바로 대체 동작으로 넘긴다 — FR-111·112가 말하는 "캐시·안내 문구로 대체"가 즉시 일어난다.
 *
 * 상태 세 가지:
 *   CLOSED    평소. 실패가 FAILURE_THRESHOLD번 연속 쌓이면 OPEN으로 간다.
 *   OPEN      끊긴 상태. openDuration 동안 전부 거절한다.
 *   HALF_OPEN 그 시간이 지난 뒤 <b>한 번만</b> 보내 본다. 성공하면 CLOSED, 실패하면 다시 OPEN.
 *
 * 반만 열어 한 번 떠보는 단계가 필요한 이유: 바로 CLOSED로 돌리면 아직 죽어 있는 상대에게 모든 요청이
 * 동시에 몰려 또 한꺼번에 실패한다.
 *
 * 시간은 주입받는다 — 테스트에서 sleep 없이 시간을 앞으로 돌리기 위해서다.
 */
public final class CircuitBreaker {

    private static final Logger LOG = Logger.getLogger(CircuitBreaker.class.getName());

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final long openMillis;
    private final LongSupplier clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAt;
    private boolean trialInFlight;

    /**
     * @param name             로그에 남길 이름 (예: "Groq", "GitHub")
     * @param failureThreshold 연속 실패가 이만큼 쌓이면 끊는다
     * @param openMillis       끊고 나서 다시 떠보기까지의 시간
     */
    public CircuitBreaker(String name, int failureThreshold, long openMillis) {
        this(name, failureThreshold, openMillis, System::currentTimeMillis);
    }

    CircuitBreaker(String name, int failureThreshold, long openMillis, LongSupplier clock) {
        if (failureThreshold <= 0 || openMillis <= 0) {
            throw new IllegalArgumentException("실패 임계값과 차단 시간은 0보다 커야 합니다");
        }
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
        this.clock = clock;
    }

    /** 지금 보내도 되는지. OPEN 시간이 지났으면 HALF_OPEN으로 바꾸고 한 번만 통과시킨다. */
    public synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            if (clock.getAsLong() - openedAt < openMillis) {
                return false;
            }
            state = State.HALF_OPEN;
            trialInFlight = false;
        }
        if (state == State.HALF_OPEN) {
            if (trialInFlight) {
                return false; // 떠보는 호출 하나가 아직 안 끝났다 — 나머지는 기다리지 말고 대체 동작으로
            }
            trialInFlight = true;
            return true;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        if (state != State.CLOSED) {
            LOG.info(() -> name + " 호출이 다시 성공해 차단을 풉니다");
        }
        state = State.CLOSED;
        consecutiveFailures = 0;
        trialInFlight = false;
    }

    /** 일시적 실패(5xx·타임아웃)든 권한 실패(403)든 "상대에게 보내도 소용없다"는 신호로 같이 센다. */
    public synchronized void recordFailure() {
        trialInFlight = false;
        if (state == State.HALF_OPEN) {
            open(); // 떠봤는데 또 실패 — 다시 끊는다
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized State state() {
        // OPEN 시간이 지났으면 조회만으로도 HALF_OPEN으로 보이게 — 관리자 화면이 "다시 떠볼 때"를 알 수 있다
        if (state == State.OPEN && clock.getAsLong() - openedAt >= openMillis) {
            return State.HALF_OPEN;
        }
        return state;
    }

    /** 끊긴 상태가 풀릴 때까지 남은 밀리초. 끊기지 않았으면 0 */
    public synchronized long millisUntilRetry() {
        if (state != State.OPEN) {
            return 0L;
        }
        return Math.max(0L, openMillis - (clock.getAsLong() - openedAt));
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.getAsLong();
        consecutiveFailures = 0;
        trialInFlight = false;
        LOG.warning(() -> name + " 호출이 연속 실패해 " + (openMillis / 1000) + "초 동안 끊습니다 — "
                + "그동안은 캐시·안내 문구로 대체합니다");
    }
}
