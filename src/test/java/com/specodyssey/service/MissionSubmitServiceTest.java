package com.specodyssey.service;

import com.specodyssey.dto.DailyMissionViewDto;
import com.specodyssey.service.CodeCompileService.Language;
import com.specodyssey.service.MissionSubmitService.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MissionSubmitService 입력 검증·기본 언어 규칙 단위테스트.
 * 검증에서 걸리는 경우는 DB·외부 API까지 가지 않는다.
 */
class MissionSubmitServiceTest {

    private final MissionSubmitService service = new MissionSubmitService();

    @Test
    void 모르는_언어는_거절() throws Exception {
        assertEquals(Status.INVALID, service.submit(1L, 1L, "RUBY", "puts 1").status());
    }

    @Test
    void 빈_코드는_거절() throws Exception {
        assertEquals(Status.INVALID, service.submit(1L, 1L, "JAVA", "   ").status());
        assertEquals(Status.INVALID, service.submit(1L, 1L, "JAVA", null).status());
    }

    @Test
    void 너무_긴_코드는_거절() throws Exception {
        String longCode = "a".repeat(MissionSubmitService.MAX_CODE_LENGTH + 1);
        assertEquals(Status.INVALID, service.submit(1L, 1L, "JAVA", longCode).status());
    }

    @Test
    void 등급별_문제풀이_점수() {
        assertEquals(6, MissionSubmitService.pointsForTierOrder(0)); // 비기너
        assertEquals(6, MissionSubmitService.pointsForTierOrder(1)); // 취준생
        assertEquals(8, MissionSubmitService.pointsForTierOrder(2)); // 실전러
        assertEquals(10, MissionSubmitService.pointsForTierOrder(3)); // 취뽀 임박
        assertEquals(12, MissionSubmitService.pointsForTierOrder(4)); // 취뽀
        // 등급이 늘어나거나 순서 계산이 어긋나도 범위를 벗어나지 않는다
        assertEquals(12, MissionSubmitService.pointsForTierOrder(9));
        assertEquals(6, MissionSubmitService.pointsForTierOrder(-1));
    }

    @Test
    void 기본_언어는_이전_제출_언어_없으면_유형별() {
        DailyMissionViewDto sql = new DailyMissionViewDto();
        sql.setCategory("SQL");
        assertEquals(Language.SQL, MissionSubmitService.defaultLanguage(sql));

        DailyMissionViewDto algo = new DailyMissionViewDto();
        algo.setCategory("ALGORITHM");
        assertEquals(Language.JAVA, MissionSubmitService.defaultLanguage(algo));

        algo.setSubmittedLanguage("PYTHON");
        assertEquals(Language.PYTHON, MissionSubmitService.defaultLanguage(algo));
    }
}
