package com.specodyssey.service;

import com.specodyssey.service.TrendCollectService.Pick;
import com.specodyssey.service.TrendLlmService.TrendItem;
import com.specodyssey.service.TrendSourceClient.Candidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TrendCollectService 직군별 보충·몫 보장 로직 단위테스트 (FR-55). DB·외부 API 없이 고르는 규칙만 확인한다.
 */
class TrendCollectServiceTest {

    // job_id → job_category (1·2: 백엔드, 3: 프론트, 4: 기획)
    private static final Map<Long, String> CATEGORY_BY_JOB = Map.of(1L, "BACKEND", 2L, "BACKEND", 3L, "FRONTEND", 4L, "PM");

    @Test
    void shortCategories_기술이_모자란_직군만_돌려준다() {
        List<Pick> picks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            picks.add(pick("Back" + i, 1L));
        }
        picks.add(pick("Front", 3L));

        List<String> result = TrendCollectService.shortCategories(picks, CATEGORY_BY_JOB,
                List.of("BACKEND", "FRONTEND", "PM"));

        assertEquals(List.of("FRONTEND", "PM"), result);
    }

    @Test
    void shortCategories_여러_직군에_걸친_기술은_각_직군에_센다() {
        List<Pick> picks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            picks.add(pick("Full" + i, 1L, 3L));
        }

        List<String> result = TrendCollectService.shortCategories(picks, CATEGORY_BY_JOB,
                List.of("BACKEND", "FRONTEND", "PM"));

        assertEquals(List.of("PM"), result);
    }

    @Test
    void selectWithQuota_개발_기술이_상한을_채워도_기획_몫은_남긴다() {
        List<Pick> picks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            picks.add(pick("Back" + i, 1L));
        }
        picks.add(pick("Figma", 4L));
        picks.add(pick("Jira", 4L));

        List<Pick> selected = TrendCollectService.selectWithQuota(picks, CATEGORY_BY_JOB, 2, 30, 5);

        List<String> names = selected.stream().map(p -> p.item().techName()).toList();
        assertEquals(5, selected.size());
        assertTrue(names.containsAll(List.of("Figma", "Jira")), names.toString());
        // 원래 순서 유지: 백엔드 앞쪽 3개 + 기획 2개
        assertEquals(List.of("Back0", "Back1", "Back2", "Figma", "Jira"), names);
    }

    @Test
    void selectWithQuota_상한보다_적으면_전부_고른다() {
        List<Pick> picks = List.of(pick("A", 1L), pick("B", 1L), pick("C", 4L));

        List<Pick> selected = TrendCollectService.selectWithQuota(picks, CATEGORY_BY_JOB, 1, 30, 30);

        assertEquals(picks, selected);
    }

    @Test
    void selectWithQuota_몫만으로도_상한을_넘지_않는다() {
        List<Pick> picks = List.of(pick("A", 1L), pick("B", 3L), pick("C", 4L));

        List<Pick> selected = TrendCollectService.selectWithQuota(picks, CATEGORY_BY_JOB, 3, 30, 2);

        assertEquals(List.of(picks.get(0), picks.get(1)), selected);
    }

    @Test
    void selectWithQuota_남은_자리는_직군을_돌아가며_채운다() {
        List<Pick> picks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            picks.add(pick("Back" + i, 1L));
        }
        for (int i = 0; i < 6; i++) {
            picks.add(pick("Pm" + i, 4L));
        }

        // 몫 1개씩 채운 뒤 남은 4자리를 백엔드·기획이 번갈아 가져간다
        List<Pick> selected = TrendCollectService.selectWithQuota(picks, CATEGORY_BY_JOB, 1, 30, 6);

        List<String> names = selected.stream().map(p -> p.item().techName()).toList();
        assertEquals(List.of("Back0", "Back1", "Back2", "Pm0", "Pm1", "Pm2"), names);
    }

    @Test
    void selectWithQuota_직군당_상한을_넘기지_않는다() {
        List<Pick> picks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            picks.add(pick("Pm" + i, 4L));
        }
        picks.add(pick("Back", 1L));

        List<Pick> selected = TrendCollectService.selectWithQuota(picks, CATEGORY_BY_JOB, 1, 3, 30);

        List<String> names = selected.stream().map(p -> p.item().techName()).toList();
        assertEquals(List.of("Pm0", "Pm1", "Pm2", "Back"), names);
    }

    @Test
    void baseTechName_끝의_괄호_설명을_뗀다() {
        assertEquals("Jira", TrendLlmService.baseTechName("Jira (Clear Done Column)"));
        assertEquals("Jira", TrendLlmService.baseTechName("  Jira（Sub-task under Epic） "));
        assertEquals("Amazon Web Services", TrendLlmService.baseTechName("Amazon Web Services (AWS)"));
        assertEquals("Spring Boot", TrendLlmService.baseTechName("Spring Boot"));
        assertEquals("(PM)", TrendLlmService.baseTechName("(PM)"));
    }

    private static Pick pick(String techName, Long... jobIds) {
        Map<Long, BigDecimal> relevance = new LinkedHashMap<>();
        for (Long jobId : jobIds) {
            relevance.put(jobId, new BigDecimal("0.9000"));
        }
        TrendItem item = new TrendItem(0, techName, techName + " 설명", BigDecimal.ONE, relevance);
        return new Pick(new Candidate(techName, "https://example.com/" + techName, LocalDateTime.now()), item);
    }
}
