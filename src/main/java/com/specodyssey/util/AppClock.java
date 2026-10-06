package com.specodyssey.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 서비스가 "오늘·지금"을 읽는 곳. 평소에는 실제 한국 시간을 돌려준다.
 *
 * 테스트 계정 시뮬레이션(service.simulation)이 지난 70일을 하루씩 돌릴 때만, 그 작업 스레드에 한해 날짜를 바꿔 끼운다
 * (runOn). 그래서 미션 배정·제출·연속 기록·점수 적립·로드맵 완료가 실제 사용과 같은 코드로, 그날 날짜로 기록된다.
 * 다른 요청 스레드에는 영향이 없다 (ThreadLocal).
 */
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final ThreadLocal<LocalDate> SIMULATED_DATE = new ThreadLocal<>();

    private AppClock() {
    }

    public interface Action<T> {
        T run() throws Exception;
    }

    /** 오늘 날짜 (한국 시간). 시뮬레이션 중인 스레드면 시뮬레이션 날짜. */
    public static LocalDate today() {
        LocalDate simulated = SIMULATED_DATE.get();
        return simulated != null ? simulated : LocalDate.now(ZONE);
    }

    /** 지금 시각 (한국 시간). 시뮬레이션 중이면 그날 날짜 + 지금 시각. */
    public static LocalDateTime now() {
        LocalDate simulated = SIMULATED_DATE.get();
        return simulated != null ? simulated.atTime(LocalTime.now(ZONE)) : LocalDateTime.now(ZONE);
    }

    public static boolean isSimulating() {
        return SIMULATED_DATE.get() != null;
    }

    /** 이 스레드에서만 날짜를 date로 바꿔 action을 실행하고, 끝나면 원래대로 되돌린다. */
    public static <T> T runOn(LocalDate date, Action<T> action) throws Exception {
        LocalDate previous = SIMULATED_DATE.get();
        SIMULATED_DATE.set(date);
        try {
            return action.run();
        } finally {
            if (previous == null) {
                SIMULATED_DATE.remove();
            } else {
                SIMULATED_DATE.set(previous);
            }
        }
    }
}
