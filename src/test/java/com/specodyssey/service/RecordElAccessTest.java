package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSP의 EL에서 읽는 record는 구성요소마다 getter(getX / isX)도 있어야 한다.
 *
 * Tomcat 10.1의 BeanELResolver는 record의 접근자 x()를 모르고 getX()만 찾는다
 * (예: ${peerComparison.peerAverage} → PropertyNotFoundException, 인사이트 화면이 500).
 * 반대로 Tomcat 11의 RecordELResolver는 getX()가 아니라 x()만 찾는다(구성요소가 아닌 파생 값은
 * 별도의 x() 메서드가 있어야 한다). record 접근자 x()는 자동으로 생기므로, 여기서는 getter 쪽만 확인한다.
 *
 * JSP에서 쓰는 service 계층 record를 새로 만들면 아래 목록에 넣는다.
 */
class RecordElAccessTest {

    private static final List<Class<?>> VIEW_RECORDS = List.of(
            SpecScoreService.PeerComparison.class,
            SpecScoreService.GrowthSummary.class,
            SpecScoreService.MonthlyScorePoint.class,
            JobBenchmarkSpecService.TierBenchmark.class,
            JobBenchmarkSpecService.BenchmarkItem.class,
            AiUsageLogService.UsageEntry.class,
            ResumeFeedbackService.Feedback.class,
            ResumeFeedbackService.Suggestion.class,
            ResumeFeedbackService.Keyword.class,
            MissionStreakService.StreakView.class,
            MissionStreakService.DayView.class,
            MissionSubmitService.CurrentTier.class,
            MissionSubmitService.SubmitResult.class,
            CodeCompileService.CompileCheck.class);

    @Test
    void 화면에_쓰는_record는_구성요소마다_getter가_있다() {
        List<String> missing = new ArrayList<>();
        for (Class<?> type : VIEW_RECORDS) {
            for (var component : type.getRecordComponents()) {
                String name = component.getName();
                String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                if (!hasPublicGetter(type, "get" + cap) && !hasPublicGetter(type, "is" + cap)) {
                    missing.add(type.getSimpleName() + "." + name);
                }
            }
        }
        assertTrue(missing.isEmpty(), "getter가 없는 record 구성요소: " + missing);
    }

    @Test
    void 구성요소가_아닌_파생_값은_getter와_record식_접근자가_둘_다_있다() throws Exception {
        // JSP가 ${missionStreak.nextStreak}, ${feedback.anyEstimated}처럼 읽는 값
        assertTrue(hasPublicGetter(MissionStreakService.StreakView.class, "getNextStreak"));
        assertTrue(hasPublicGetter(MissionStreakService.StreakView.class, "nextStreak"));
        assertTrue(hasPublicGetter(ResumeFeedbackService.Feedback.class, "isAnyEstimated"));
        assertTrue(hasPublicGetter(ResumeFeedbackService.Feedback.class, "anyEstimated"));
    }

    private static boolean hasPublicGetter(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 0
                    && Modifier.isPublic(method.getModifiers())) {
                return true;
            }
        }
        return false;
    }
}
